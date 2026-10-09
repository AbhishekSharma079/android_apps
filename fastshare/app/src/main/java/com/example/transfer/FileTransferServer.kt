package com.example.transfer

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.TransferRepository
import com.example.model.TransferDirection
import com.example.model.TransferItem
import com.example.model.TransferSessionState
import com.example.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class FileTransferServer(
    private val context: Context,
    private val repository: TransferRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "FileTransferServer"
        const val DEFAULT_PORT = 8888
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val _sessionState = MutableStateFlow(TransferSessionState())
    val sessionState: StateFlow<TransferSessionState> = _sessionState.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    fun startServer(port: Int = DEFAULT_PORT) {
        if (_isServerRunning.value) return

        serverJob?.cancel()
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(port).apply {
                    reuseAddress = true
                }
                serverSocket = server
                _isServerRunning.value = true
                Log.d(TAG, "Server started on port $port")

                while (isActive && !server.isClosed) {
                    try {
                        val clientSocket = server.accept()
                        handleClientConnection(clientSocket)
                    } catch (e: Exception) {
                        if (!isActive) break
                        Log.d(TAG, "Server accept interrupted: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                _isServerRunning.value = false
            }
        }
    }

    fun stopServer() {
        _isServerRunning.value = false
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
    }

    private suspend fun handleClientConnection(socket: Socket) = withContext(Dispatchers.IO) {
        val inputStream = socket.getInputStream()
        val outputStream = socket.getOutputStream()
        val reader = BufferedReader(InputStreamReader(inputStream))
        val writer = PrintWriter(outputStream, true)

        try {
            // 1. Read handshake
            val handshakeStr = reader.readLine() ?: return@withContext
            val handshakeJson = JSONObject(handshakeStr)
            val senderName = handshakeJson.optString("senderName", "Sender Device")
            val fileCount = handshakeJson.optInt("count", 0)
            val totalBytes = handshakeJson.optLong("totalBytes", 0L)

            // Accept transfer
            val ackJson = JSONObject().apply { put("status", "ACCEPTED") }
            writer.println(ackJson.toString())

            val receivedItems = mutableListOf<TransferItem>()
            var overallBytesTransferred = 0L

            _sessionState.value = TransferSessionState(
                isTransferring = true,
                direction = TransferDirection.RECEIVED,
                totalFiles = fileCount,
                totalBytes = totalBytes,
                bytesTransferred = 0L,
                items = emptyList()
            )

            val destDir = repository.getReceivedFilesDir()

            var lastSpeedCheckTime = System.currentTimeMillis()
            var bytesSinceLastCheck = 0L
            var currentSpeed = 0L

            for (i in 0 until fileCount) {
                val fileHeaderStr = reader.readLine() ?: break
                val fileHeader = JSONObject(fileHeaderStr)
                val fileName = fileHeader.optString("name", "received_file_$i")
                val fileSize = fileHeader.optLong("size", 0L)
                val mimeType = fileHeader.optString("mime", "application/octet-stream")

                val targetFile = getUniqueFile(destDir, fileName)
                val fileOut = FileOutputStream(targetFile)

                val item = TransferItem(
                    id = UUID.randomUUID().toString(),
                    fileName = fileName,
                    fileSize = fileSize,
                    bytesTransferred = 0L,
                    mimeType = mimeType,
                    direction = TransferDirection.RECEIVED,
                    status = TransferStatus.TRANSFERRING,
                    localFilePath = targetFile.absolutePath,
                    peerDeviceName = senderName
                )

                val currentList = receivedItems.toMutableList().apply { add(item) }
                _sessionState.value = _sessionState.value.copy(
                    currentFileIndex = i,
                    currentFileName = fileName,
                    items = currentList
                )

                var fileBytesReceived = 0L
                val buffer = ByteArray(64 * 1024)

                while (fileBytesReceived < fileSize) {
                    val bytesToRead = Math.min(buffer.size.toLong(), fileSize - fileBytesReceived).toInt()
                    val read = inputStream.read(buffer, 0, bytesToRead)
                    if (read == -1) break

                    fileOut.write(buffer, 0, read)
                    fileBytesReceived += read
                    overallBytesTransferred += read
                    bytesSinceLastCheck += read

                    val now = System.currentTimeMillis()
                    if (now - lastSpeedCheckTime >= 250) {
                        val durationSec = (now - lastSpeedCheckTime) / 1000.0
                        if (durationSec > 0) {
                            currentSpeed = (bytesSinceLastCheck / durationSec).toLong()
                        }
                        bytesSinceLastCheck = 0L
                        lastSpeedCheckTime = now
                    }

                    _sessionState.value = _sessionState.value.copy(
                        bytesTransferred = overallBytesTransferred,
                        speedBytesPerSec = currentSpeed
                    )
                }

                fileOut.flush()
                fileOut.close()

                val completedItem = item.copy(
                    bytesTransferred = fileSize,
                    status = TransferStatus.COMPLETED,
                    localFilePath = targetFile.absolutePath
                )
                receivedItems.remove(item)
                receivedItems.add(completedItem)

                // Save to Room DB
                repository.saveTransfer(completedItem)

                // Send ACK for file
                val fileAck = JSONObject().apply { put("status", "FILE_OK") }
                writer.println(fileAck.toString())
            }

            _sessionState.value = _sessionState.value.copy(
                isTransferring = false,
                isCompleted = true,
                bytesTransferred = totalBytes,
                items = receivedItems
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
            _sessionState.value = _sessionState.value.copy(
                isTransferring = false,
                errorMessage = e.message
            )
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun getUniqueFile(dir: File, fileName: String): File {
        var file = File(dir, fileName)
        if (!file.exists()) return file

        val nameWithoutExt = fileName.substringBeforeLast('.', fileName)
        val ext = if (fileName.contains('.')) "." + fileName.substringAfterLast('.') else ""
        var counter = 1

        while (file.exists()) {
            file = File(dir, "${nameWithoutExt}_$counter$ext")
            counter++
        }
        return file
    }

    fun resetSession() {
        _sessionState.value = TransferSessionState()
    }
}
