package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.CommandLog
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandLogDao {
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CommandLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CommandLog): Long

    @Query("UPDATE command_logs SET status = :status, errorMessage = :errorMessage WHERE commandId = :commandId")
    suspend fun updateLogStatus(commandId: String, status: String, errorMessage: String? = null)

    @Query("DELETE FROM command_logs")
    suspend fun clearAllLogs()
}
