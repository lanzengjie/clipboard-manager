package com.clipboard.manager.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_entries ORDER BY isFavorite DESC, timestamp DESC")
    fun getAllEntries(): LiveData<List<ClipboardEntry>>

    @Query("SELECT * FROM clipboard_entries WHERE content LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY isFavorite DESC, timestamp DESC")
    fun searchEntries(query: String): LiveData<List<ClipboardEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipboardEntry)

    @Query("UPDATE clipboard_entries SET timestamp = :timestamp WHERE content = :content")
    suspend fun updateTimestamp(content: String, timestamp: Long)

    @Query("UPDATE clipboard_entries SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE clipboard_entries SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Delete
    suspend fun delete(entry: ClipboardEntry)

    @Query("DELETE FROM clipboard_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM clipboard_entries WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): ClipboardEntry?
}
