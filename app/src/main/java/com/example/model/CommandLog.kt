package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_logs")
data class CommandLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commandId: String,
    val type: String = "SMS",
    val recipient: String,
    val message: String,
    val status: String, // "pending", "sent", "failed"
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
