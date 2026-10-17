package com.unigrab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unigrab.app.data.local.entity.LogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM download_logs WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun getLogsForTaskFlow(taskId: String): Flow<List<LogEntry>>

    @Query("SELECT * FROM download_logs WHERE taskId = :taskId ORDER BY timestamp ASC")
    suspend fun getLogsForTask(taskId: String): List<LogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntry)

    @Query("DELETE FROM download_logs WHERE taskId = :taskId")
    suspend fun deleteLogsForTask(taskId: String)
}
