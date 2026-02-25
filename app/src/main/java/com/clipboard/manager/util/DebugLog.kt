package com.clipboard.manager.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private const val TAG = "ClipboardManager"
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logFile = File(logDir, "clipboard_debug_${System.currentTimeMillis()}.log")
            log("=== Debug Log Started ===")
            log("App Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
            log("Android Version: ${android.os.Build.VERSION.RELEASE}")
            log("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init debug log", e)
        }
    }

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $message"

        // 输出到 LogCat
        Log.d(TAG, logMessage)

        // 保存到文件
        try {
            logFile?.appendText("$logMessage\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    fun logError(message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] ERROR: $message"

        Log.e(TAG, logMessage, throwable)

        try {
            logFile?.appendText("$logMessage\n")
            throwable?.let {
                logFile?.appendText("  Exception: ${it.message}\n")
                logFile?.appendText("  StackTrace: ${it.stackTraceToString()}\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write error to log file", e)
        }
    }

    fun getLogContent(): String {
        return try {
            logFile?.readText() ?: "No log file"
        } catch (e: Exception) {
            "Failed to read log: ${e.message}"
        }
    }

    fun clearLog() {
        try {
            logFile?.writeText("")
            log("=== Log Cleared ===")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log", e)
        }
    }
}
