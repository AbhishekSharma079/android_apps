package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TransferRepository
import com.example.model.FileCategory
import com.example.model.NearbyDevice
import com.example.model.SharedFile
import com.example.model.TransferDirection
import com.example.model.TransferItem
import com.example.model.TransferSessionState
import com.example.transfer.DeviceDiscoveryManager
import com.example.transfer.DeviceMediaScanner
import com.example.transfer.FileTransferClient
import com.example.transfer.FileTransferServer
import com.example.transfer.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val repository = TransferRepository(context)
    val mediaScanner = DeviceMediaScanner(context)

    val discoveryManager = DeviceDiscoveryManager(context, viewModelScope)
    val server = FileTransferServer(context, repository, viewModelScope)
    val client = FileTransferClient(context, repository, viewModelScope)

    // Transfers from Database
    val recentTransfers: StateFlow<List<TransferItem>> = repository.recentTransfers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val allTransfers: StateFlow<List<TransferItem>> = repository.allTransfers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val receivedFiles: StateFlow<List<TransferItem>> = repository.receivedFiles.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Current category & files
    private val _currentCategory = MutableStateFlow(FileCategory.PHOTOS)
    val currentCategory: StateFlow<FileCategory> = _currentCategory.asStateFlow()

    private val _categoryFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val categoryFiles: StateFlow<List<SharedFile>> = _categoryFiles.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SharedFile>> = _selectedFiles.asStateFlow()

    // Device Settings & Identity
    private val prefs = context.getSharedPreferences("fastshare_prefs", Context.MODE_PRIVATE)
    private val _deviceName = MutableStateFlow(prefs.getString("device_name", NetworkUtils.getDefaultDeviceName()) ?: NetworkUtils.getDefaultDeviceName())
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("notifications_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _autoReceiveEnabled = MutableStateFlow(prefs.getBoolean("auto_receive", true))
    val autoReceiveEnabled: StateFlow<Boolean> = _autoReceiveEnabled.asStateFlow()

    // Active session combined state
    val serverSessionState = server.sessionState
    val clientSessionState = client.sessionState

    val discoveredDevices = discoveryManager.discoveredDevices
    val isSearching = discoveryManager.isSearching

    init {
        loadCategoryFiles(FileCategory.PHOTOS)
    }

    fun setDeviceName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            _deviceName.value = trimmed
            prefs.edit().putString("device_name", trimmed).apply()
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun setAutoReceiveEnabled(enabled: Boolean) {
        _autoReceiveEnabled.value = enabled
        prefs.edit().putBoolean("auto_receive", enabled).apply()
    }

    fun setCategory(category: FileCategory) {
        _currentCategory.value = category
        loadCategoryFiles(category)
    }

    fun loadCategoryFiles(category: FileCategory) {
        viewModelScope.launch {
            _isLoadingFiles.value = true
            val files = mediaScanner.queryDeviceFiles(category)
            val selectedUris = _selectedFiles.value.map { it.uri }.toSet()
            _categoryFiles.value = files.map { file ->
                file.copy(isSelected = selectedUris.contains(file.uri))
            }
            _isLoadingFiles.value = false
        }
    }

    fun toggleFileSelection(file: SharedFile) {
        val currentSelected = _selectedFiles.value.toMutableList()
        val index = currentSelected.indexOfFirst { it.uri == file.uri }
        if (index >= 0) {
            currentSelected.removeAt(index)
        } else {
            currentSelected.add(file.copy(isSelected = true))
        }
        _selectedFiles.value = currentSelected

        // Update list checkmarks
        val currentFiles = _categoryFiles.value.map {
            if (it.uri == file.uri) it.copy(isSelected = index < 0) else it
        }
        _categoryFiles.value = currentFiles
    }

    fun addFilesFromUris(uris: List<Uri>) {
        viewModelScope.launch {
            val newlySelected = mutableListOf<SharedFile>()
            for (uri in uris) {
                mediaScanner.resolveFromUri(uri)?.let { newlySelected.add(it.copy(isSelected = true)) }
            }
            if (newlySelected.isNotEmpty()) {
                val current = _selectedFiles.value.toMutableList()
                newlySelected.forEach { newItem ->
                    if (current.none { it.uri == newItem.uri }) {
                        current.add(newItem)
                    }
                }
                _selectedFiles.value = current
                loadCategoryFiles(_currentCategory.value)
            }
        }
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
        _categoryFiles.value = _categoryFiles.value.map { it.copy(isSelected = false) }
    }

    fun startSenderDiscovery() {
        discoveryManager.startDiscovery(
            isReceiver = false,
            deviceName = _deviceName.value,
            serverPort = FileTransferServer.DEFAULT_PORT
        )
    }

    fun startReceiverMode() {
        server.startServer(FileTransferServer.DEFAULT_PORT)
        discoveryManager.startDiscovery(
            isReceiver = true,
            deviceName = _deviceName.value,
            serverPort = FileTransferServer.DEFAULT_PORT
        )
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }

    fun connectAndSend(targetDevice: NearbyDevice) {
        val files = _selectedFiles.value
        if (files.isEmpty()) return
        discoveryManager.stopDiscovery()
        client.sendFiles(
            targetDevice = targetDevice,
            files = files,
            senderDeviceName = _deviceName.value
        )
    }

    fun sendToSelfForTest() {
        // Starts receiver locally and sends to localhost so user can test transfers on 1 device
        val files = _selectedFiles.value
        if (files.isEmpty()) return
        server.startServer(FileTransferServer.DEFAULT_PORT)
        val loopbackDevice = NearbyDevice(
            id = "127.0.0.1:8888",
            name = "${_deviceName.value} (Local)",
            ipAddress = "127.0.0.1",
            port = FileTransferServer.DEFAULT_PORT,
            isReceiver = true
        )
        connectAndSend(loopbackDevice)
    }

    fun cancelActiveTransfer() {
        client.cancelTransfer()
    }

    fun resetTransferState() {
        client.resetSession()
        server.resetSession()
        clearSelectedFiles()
    }

    fun openFile(context: Context, item: TransferItem) {
        try {
            val path = item.localFilePath
            if (path == null) {
                Toast.makeText(context, "File path not found", Toast.LENGTH_SHORT).show()
                return
            }

            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(context, "File does not exist on device", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, item: TransferItem) {
        try {
            val path = item.localFilePath ?: return
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share file"))
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteTransfer(item: TransferItem, deletePhysicalFile: Boolean = false) {
        viewModelScope.launch {
            if (deletePhysicalFile && item.localFilePath != null) {
                try {
                    val f = File(item.localFilePath)
                    if (f.exists()) f.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            repository.deleteTransfer(item.id)
        }
    }

    fun clearAllTransfers() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
