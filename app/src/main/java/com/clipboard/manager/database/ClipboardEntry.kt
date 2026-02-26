package com.clipboard.manager.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_entries")
data class ClipboardEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val note: String = ""
)
