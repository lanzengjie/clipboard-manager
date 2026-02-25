package com.clipboard.manager.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.clipboard.manager.database.ClipboardDatabase
import com.clipboard.manager.database.ClipboardEntry
import com.clipboard.manager.database.ClipboardRepository
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
        checkClipboard()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val database = ClipboardDatabase.getDatabase(applicationContext)
        repository = ClipboardRepository(database.clipboardDao())

        isEnabled = true

        // 获取当前剪贴板内容
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            lastClipText = clip.getItemAt(0).text?.toString()
        }

        // 注册剪贴板监听
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 当窗口内容变化时检查剪贴板
        // 这是一个额外的检查，确保不会错过剪贴板变化
    }

    private fun checkClipboard() {
        // 检查是否是应用自身触发的复制操作
        if (isSelfCopy) {
            isSelfCopy = false
            return
        }

        try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()

                // 检查是否为空或与上次相同
                if (!text.isNullOrEmpty() && text != lastClipText) {
                    lastClipText = text
                    saveToDatabase(text)
                }
            }
        } catch (e: Exception) {
            // 忽略权限错误
        }
    }

    private fun saveToDatabase(content: String) {
        serviceScope.launch {
            val entry = ClipboardEntry(content = content)
            repository.insert(entry)
        }
    }

    override fun onInterrupt() {
        // 服务被中断
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            // 忽略
        }
        instance = null
        isEnabled = false
    }
}
