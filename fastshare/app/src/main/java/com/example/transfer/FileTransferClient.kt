package com.example.transfer

import android.content.Context
import android.util.Log
import com.example.data.TransferRepository
import com.example.model.NearbyDevice
import com.example.model.SharedFile
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
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class FileTransferClient(
    private val context: Context,
    private val repository: TransferRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "FileTransferClient"
        private const val CONNECT_TIMEOUT_MS = 6000
    }

    private val _sessionState = MutableStateFlow(TransferSessionState())
    val sessionState: StateFlow<TransferSessionState> = _sessionState.asStateFlow()

    private var transferJob: Job? = null
    private var activeSocket: Socket? = null

    fun sendFiles(
        targetDevice: NearbyDevice,
        files: List<SharedFile>,
        senderDeviceName: String
    ) {
        if (files.isEmpty()) return

        transferJob?.cancel()
        transferJob = scope.launch(Dispatchers.IO) {
            val totalBytes = files.sumOf { it.size }
            val transferItems = files.map { file ->
                TransferItem(
                    id = UUID.randomUUID().toString(),
                    fileName = file.name,
                    fileSize = file.size,
                    bytesTransferred = 0L,
                    mimeType = file.mimeType,
                    direction = TransferDirection.SENT,
                    status = TransferStatus.PENDING,
                    localFilePath = file.uri.toString(),
                    peerDeviceName = targetDevice.name
                )
            }.toMutableList()

            _sessionState.value = TransferSessionState(
                isTransferring = true,
                peerDevice = targetDevice,
                direction = TransferDirection.SENT,
                totalFiles = files.size,
                totalBytes = totalBytes,
                bytesTransferred = 0L,
                items = transferItems.toList()
            )

            var socket: Socket? = null
            try {
                socket = Socket()
                activeSocket = socket
                socket.connect(InetSocketAddress(targetDevice.ipAddress, targetDevice.port), CONNECT_TIMEOUT_MS)

                val outputStream = socket.getOutputStream()
                val inputStream = socket.getInputStream()
                val writer = PrintWriter(outputStream, true)
                val reader = BufferedReader(InputStreamReader(inputStream))

                // 1. Handshake
                val handshakeJson = JSONObject().apply {
                    put("senderName", senderDeviceName)
                    put("count", files.size)
                    put("totalBytes", totalBytes)
                }
                writer.println(handshakeJson.toString())

                // Read handshake ACK
                val ackResponse = reader.readLine()
                if (ackResponse == null || !ackResponse.contains("ACCEPTED")) {
                    throw IllegalStateException("Connection rejected by receiver: $ackResponse")
                }

                var overallBytesTransferred = 0L
                var lastSpeedCheckTime = System.currentTimeMillis()
                var bytesSinceLastCheck = 0L
                var currentSpeed = 0L

                for (i in files.indices) {
                    if (!isActive) break
                    val file = files[i]
                    val currentItem = transferItems[i]

                    // Mark as transferring
                    val transferringItem = currentItem.copy(status = TransferStatus.TRANSFERRING)
                    transferItems[i] = transferringItem
                    _sessionState.value = _sessionState.value.copy(
                        currentFileIndex = i,
                        currentFileName = file.name,
                        items = transferItems.toList()
                    )

                    // Send File Header
                    val fileHeader = JSONObject().apply {
                        put("name", file.name)
                        put("size", file.size)
                        put("mime", file.mimeType)
                    }
                    writer.println(fileHeader.toString())

                    // Send File Bytes
                    val fileIn = context.contentResolver.openInputStream(file.uri)
                        ?: throw IllegalStateException("Cannot open input stream for: ${file.name}")

                    val buffer = ByteArray(64 * 1024)
                    var fileBytesSent = 0L

                    fileIn.use { stream ->
                        while (isActive) {
                            val read = stream.read(buffer)
                            if (read == -1) break

                            outputStream.write(buffer, 0, read)
                            fileBytesSent += read
                            overallBytesTransferred += read
                            bytesSinceLastCheck += read

                            val now = System.currentTimeMillis()
                            if (now - lastSpeedCheckTime >= 200) {
                                val durationSec = (now - lastSpeedCheckTime) / 1000.0
                                if (durationSec > 0) {
                                    currentSpeed = (bytesSinceLastCheck / durationSec).toLong()
                                }
                                bytesSinceLastCheck = 0L
                                lastSpeedCheckTime = now
                            }

                            val inProgressItem = transferringItem.copy(
                                bytesTransferred = fileBytesSent,
                                speedBytesPerSec = currentSpeed
                            )
                            transferItems[i] = inProgressItem

                            _sessionState.value = _sessionState.value.copy(
                                bytesTransferred = overallBytesTransferred,
                                speedBytesPerSec = currentSpeed,
                                items = transferItems.toList()
                            )
                        }
                        outputStream.flush()
                    }

                    // Read File ACK
                    val fileAck = reader.readLine()
                    if (fileAck != null && fileAck.contains("FILE_OK")) {
                        val completedItem = transferringItem.copy(
                            bytesTransferred = file.size,
                            status = TransferStatus.COMPLETED
                        )
                        transferItems[i] = completedItem
                        // Save in Room
                        repository.saveTransfer(completedItem)
                    } else {
                        val failedItem = transferringItem.copy(status = TransferStatus.FAILED)
                        transferItems[i] = failedItem
                        repository.saveTransfer(failedItem)
                    }

                    _sessionState.value = _sessionState.value.copy(
                        items = transferItems.toList()
                    )
                }

                _sessionState.value = _sessionState.value.copy(
                    isTransferring = false,
                    isCompleted = true,
                    bytesTransferred = totalBytes,
                    items = transferItems.toList()
                )
            } catch (e: Exception) {
                Log.e(TAG, "File transfer error", e)
                _sessionState.value = _sessionState.value.copy(
                    isTransferring = false,
                    errorMessage = e.message ?: "Transfer failed"
                )
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // ignore
                }
                activeSocket = null
            }
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        _sessionState.value = _sessionState.value.copy(
            isTransferring = false,
            errorMessage = "Transfer cancelled"
        )
    }

    fun resetSession() {
        _sessionState.value = TransferSessionState()
    }
}
