package com.clipboard.manager.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_entries ORDER BY timestamp DESC")
    fun getAllEntries(): LiveData<List<ClipboardEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipboardEntry)

    @Query("UPDATE clipboard_entries SET timestamp = :timestamp WHERE content = :content")
    suspend fun updateTimestamp(content: String, timestamp: Long)

    @Delete
    suspend fun delete(entry: ClipboardEntry)

    @Query("DELETE FROM clipboard_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM clipboard_entries WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): ClipboardEntry?
}
