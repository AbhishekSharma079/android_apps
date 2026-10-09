package com.example.model

import android.net.Uri

enum class FileCategory(val displayName: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    MUSIC("Music"),
    DOCUMENTS("Documents"),
    APPS("Apps"),
    OTHERS("Others")
}

enum class TransferDirection {
    SENT,
    RECEIVED
}

enum class TransferStatus {
    PENDING,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class SharedFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val uri: Uri,
    val category: FileCategory,
    val dateModified: Long = System.currentTimeMillis(),
    val thumbnailUri: Uri? = null,
    val isSelected: Boolean = false
)

data class TransferItem(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val bytesTransferred: Long = 0L,
    val mimeType: String,
    val direction: TransferDirection,
    val status: TransferStatus = TransferStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val localFilePath: String? = null,
    val peerDeviceName: String = "Nearby Device",
    val speedBytesPerSec: Long = 0L
) {
    val progress: Float
        get() = if (fileSize > 0) (bytesTransferred.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f) else 0f
}

data class NearbyDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 8888,
    val isReceiver: Boolean = true,
    val discoveredAt: Long = System.currentTimeMillis()
)

data class TransferSessionState(
    val isTransferring: Boolean = false,
    val peerDevice: NearbyDevice? = null,
    val direction: TransferDirection = TransferDirection.SENT,
    val totalFiles: Int = 0,
    val totalBytes: Long = 0L,
    val bytesTransferred: Long = 0L,
    val currentFileIndex: Int = 0,
    val currentFileName: String = "",
    val speedBytesPerSec: Long = 0L,
    val items: List<TransferItem> = emptyList(),
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
) {
    val overallProgress: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
