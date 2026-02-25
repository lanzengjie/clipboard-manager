package com.clipboard.manager.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.clipboard.manager.database.ClipboardDatabase
import com.clipboard.manager.database.ClipboardEntry
import com.clipboard.manager.database.ClipboardRepository
import com.clipboard.manager.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipboardAccessibilityService : AccessibilityService() {

    companion object {
        var isEnabled = false
        var isSelfCopy = false
        private var instance: ClipboardAccessibilityService? = null

        fun getInstance(): ClipboardAccessibilityService? = instance
    }

    private lateinit var clipboardManager: ClipboardManager
    private lateinit var repository: ClipboardRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 用于检测是否是应用自身触发的复制
    private var lastClipText: String? = null

    // 剪贴板监听器
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        DebugLog.log("=== OnPrimaryClipChanged Listener Fired ===")
        checkClipboard()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        DebugLog.log("=== onServiceConnected Called ===")

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        DebugLog.log("ClipboardManager initialized: $clipboardManager")

        val database = ClipboardDatabase.getDatabase(applicationContext)
        repository = ClipboardRepository(database.clipboardDao())
        DebugLog.log("Repository initialized")

        isEnabled = true

        // 获取当前剪贴板内容
        try {
            val clip = clipboardManager.primaryClip
            DebugLog.log("Initial clipboard check - primaryClip: ${clip != null}")
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                lastClipText = text
                DebugLog.log("Initial clipboard text: '${text?.take(100)}' (length: ${text?.length})")
            }
        } catch (e: Exception) {
            DebugLog.logError("Error getting initial clipboard", e)
        }

        // 注册剪贴板监听
        try {
            clipboardManager.addPrimaryClipChangedListener(clipboardListener)
            DebugLog.log("Clipboard listener registered successfully")
        } catch (e: Exception) {
            DebugLog.logError("Failed to register clipboard listener", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        DebugLog.log("=== onAccessibilityEvent Called ===")
        DebugLog.log("Event type: ${event?.eventType}")
        DebugLog.log("Event package: ${event?.packageName}")
        DebugLog.log("Event class: ${event?.className}")

        // 当窗口内容变化时检查剪贴板
        if (event?.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            DebugLog.log("Window content changed, checking clipboard...")
            checkClipboard()
        }
    }

    private fun checkClipboard() {
        DebugLog.log("--- checkClipboard() called ---")

        // 检查是否是应用自身触发的复制操作
        if (isSelfCopy) {
            DebugLog.log("isSelfCopy is TRUE, skipping (this is our own copy action)")
            isSelfCopy = false
            return
        }

        DebugLog.log("ClipboardManager: $clipboardManager")
        DebugLog.log("Last clip text: '${lastClipText?.take(50)}'")

        try {
            val clip = clipboardManager.primaryClip
            DebugLog.log("Primary clip: ${clip != null}")

            if (clip != null) {
                DebugLog.log("Clip item count: ${clip.itemCount}")

                if (clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    DebugLog.log("First item: $item")

                    val text = item.text?.toString()
                    DebugLog.log("Clip text: '${text?.take(100)}'")
                    DebugLog.log("Clip text length: ${text?.length}")

                    // 检查是否为空
                    if (text.isNullOrEmpty()) {
                        DebugLog.log("Clip text is NULL or EMPTY, skipping")
                        return
                    }

                    // 检查是否与上次相同
                    if (text == lastClipText) {
                        DebugLog.log("Text is same as last clip, skipping")
                        return
                    }

                    // 新内容，保存
                    DebugLog.log(">>> NEW CLIPBOARD CONTENT DETECTED! <<<")
                    DebugLog.log("Saving to database: '${text.take(100)}...'")
                    lastClipText = text
                    saveToDatabase(text)
                } else {
                    DebugLog.log("Clip item count is 0, skipping")
                }
            } else {
                DebugLog.log("Primary clip is NULL, skipping")
            }
        } catch (e: Exception) {
            DebugLog.logError("Error checking clipboard", e)
        }

        DebugLog.log("--- checkClipboard() finished ---")
    }

    private fun saveToDatabase(content: String) {
        DebugLog.log("saveToDatabase() called with content length: ${content.length}")

        serviceScope.launch {
            try {
                val entry = ClipboardEntry(content = content)
                repository.insert(entry)
                DebugLog.log(">>> Successfully saved to database! <<<")
            } catch (e: Exception) {
                DebugLog.logError("Failed to save to database", e)
            }
        }
    }

    override fun onInterrupt() {
        DebugLog.log("=== onInterrupt Called ===")
    }

    override fun onDestroy() {
        DebugLog.log("=== onDestroy Called ===")
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
            DebugLog.log("Clipboard listener removed")
        } catch (e: Exception) {
            DebugLog.logError("Error removing clipboard listener", e)
        }
        instance = null
        isEnabled = false
        DebugLog.log("Service disabled")
    }
}
