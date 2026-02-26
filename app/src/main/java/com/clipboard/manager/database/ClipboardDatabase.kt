package com.clipboard.manager.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(entities = [ClipboardEntry::class], version = 2, exportSchema = false)
abstract class ClipboardDatabase : RoomDatabase() {

    abstract fun clipboardDao(): ClipboardDao

    companion object {
        @Volatile
        private var INSTANCE: ClipboardDatabase? = null

        fun getDatabase(context: Context): ClipboardDatabase {
            return INSTANCE ?: synchronized(this) {
                // 使用外部文件目录存储数据库，卸载后数据保留
                val dbDir = context.getExternalFilesDir(null)

                // 确保目录存在
                dbDir?.mkdirs()

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClipboardDatabase::class.java,
                    "clipboard_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getDatabasePath(context: Context): String {
            val dbDir = context.getExternalFilesDir(null)
            return "${dbDir?.absolutePath}/clipboard_database"
        }
    }
}
