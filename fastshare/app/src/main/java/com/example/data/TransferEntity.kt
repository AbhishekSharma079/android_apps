package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val direction: String, // "SENT" or "RECEIVED"
    val status: String,    // "COMPLETED", "FAILED", "CANCELLED"
    val timestamp: Long,
    val localFilePath: String?,
    val peerDeviceName: String
)
