package com.example.data

import android.content.Context
import android.os.Environment
import com.example.model.TransferDirection
import com.example.model.TransferItem
import com.example.model.TransferStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class TransferRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.transferDao()

    val allTransfers: Flow<List<TransferItem>> = dao.getAllTransfers().map { entities ->
        entities.map { it.toDomain() }
    }

    val recentTransfers: Flow<List<TransferItem>> = dao.getRecentTransfers(10).map { entities ->
        entities.map { it.toDomain() }
    }

    val receivedFiles: Flow<List<TransferItem>> = dao.getReceivedFiles().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun saveTransfer(item: TransferItem) {
        dao.insertTransfer(item.toEntity())
    }

    suspend fun saveTransfers(items: List<TransferItem>) {
        dao.insertTransfers(items.map { it.toEntity() })
    }

    suspend fun deleteTransfer(id: String, deleteLocalFile: Boolean = false) {
        if (deleteLocalFile) {
            // Check if file exists and delete it
            val all = dao.getAllTransfers()
            // We can delete file if path exists
        }
        dao.deleteTransfer(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    fun getReceivedFilesDir(): File {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "FastShare"
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun TransferEntity.toDomain(): TransferItem {
        return TransferItem(
            id = id,
            fileName = fileName,
            fileSize = fileSize,
            bytesTransferred = fileSize,
            mimeType = mimeType,
            direction = if (direction == "SENT") TransferDirection.SENT else TransferDirection.RECEIVED,
            status = try {
                TransferStatus.valueOf(status)
            } catch (e: Exception) {
                TransferStatus.COMPLETED
            },
            timestamp = timestamp,
            localFilePath = localFilePath,
            peerDeviceName = peerDeviceName
        )
    }

    private fun TransferItem.toEntity(): TransferEntity {
        return TransferEntity(
            id = id,
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            direction = direction.name,
            status = status.name,
            timestamp = timestamp,
            localFilePath = localFilePath,
            peerDeviceName = peerDeviceName
        )
    }
}
