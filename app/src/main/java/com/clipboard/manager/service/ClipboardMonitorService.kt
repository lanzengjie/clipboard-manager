package com.clipboard.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clipboard.manager.MainActivity
import com.clipboard.manager.R
import com.clipboard.manager.database.ClipboardDatabase
import com.clipboard.manager.database.ClipboardEntry
import com.clipboard.manager.database.ClipboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ClipboardMonitorService : Service() {

    companion object {
        const val ACTION_SET_SELF_COPY = "com.clipboard.manager.SET_SELF_COPY"
        const val CHANNEL_ID = "clipboard_monitor_channel"
        const val NOTIFICATION_ID = 1
        // 静态标志位，用于标记是否由应用自身触发的复制操作
        @Volatile
        var isSelfCopy = false
    }

    private lateinit var clipboardManager: ClipboardManager
    private lateinit var repository: ClipboardRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // 检查是否是应用自身触发的复制操作
        if (!isSelfCopy) {
            handleClipboardChange()
        } else {
            // 重置标志位
            isSelfCopy = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        val database = ClipboardDatabase.getDatabase(applicationContext)
        repository = ClipboardRepository(database.clipboardDao())

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理设置标志位的 Action
        if (intent != null && ACTION_SET_SELF_COPY == intent.action) {
            isSelfCopy = true
        }

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())

        // 防止重复注册监听器
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            // 忽略未注册的异常
        }
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            // 忽略异常
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.monitoring_active))
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun handleClipboardChange() {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text
            if (!text.isNullOrEmpty()) {
                serviceScope.launch {
                    val entry = ClipboardEntry(content = text.toString())
                    repository.insert(entry)
                }
            }
        }
    }
}
