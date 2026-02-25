package com.clipboard.manager.database

import androidx.lifecycle.LiveData

class ClipboardRepository(private val clipboardDao: ClipboardDao) {

    val allEntries: LiveData<List<ClipboardEntry>> = clipboardDao.getAllEntries()

    suspend fun insert(entry: ClipboardEntry) {
        // Check if content already exists
        val existing = clipboardDao.findByContent(entry.content)
        if (existing == null) {
            // 不存在则插入新记录
            clipboardDao.insert(entry)
        } else {
            // 已存在则更新时间戳，将最新内容移到顶部
            clipboardDao.updateTimestamp(entry.content, System.currentTimeMillis())
        }
    }

    suspend fun delete(entry: ClipboardEntry) {
        clipboardDao.delete(entry)
    }

    suspend fun deleteAll() {
        clipboardDao.deleteAll()
    }
}
