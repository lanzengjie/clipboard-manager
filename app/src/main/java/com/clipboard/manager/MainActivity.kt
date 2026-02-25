package com.clipboard.manager

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.clipboard.manager.adapter.ClipboardAdapter
import com.clipboard.manager.database.ClipboardEntry
import com.clipboard.manager.databinding.ActivityMainBinding
import com.clipboard.manager.service.ClipboardAccessibilityService
import com.clipboard.manager.ui.ClipboardViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ClipboardViewModel
    private lateinit var adapter: ClipboardAdapter
    private var isMonitoring = false

    companion object {
        private const val PREFS_NAME = "ClipboardManagerPrefs"
        private const val KEY_MONITORING = "is_monitoring"
        private const val KEY_BATTERY_DIALOG_SHOWN = "battery_dialog_shown"
        private const val KEY_PERMISSIONS_SHOWN = "permissions_dialog_shown"
    }

    // 权限请求 launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ClipboardViewModel::class.java]

        // Restore monitoring state
        restoreMonitoringState()

        setupRecyclerView()
        setupButtons()
        observeData()

        // 检查电池优化
        checkBatteryOptimization()

        // 检查并请求必要权限
        checkAndRequestPermissions()

        // 检查辅助功能权限
        checkAccessibilityPermission()
    }

    override fun onResume() {
        super.onResume()
        // 每次返回页面时检查辅助功能状态
        updateMonitoringState()
    }

    private fun checkAccessibilityPermission() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accessibilityShown = prefs.getBoolean("accessibility_dialog_shown", false)

        if (!accessibilityShown && !isAccessibilityServiceEnabled()) {
            showAccessibilityPermissionDialog()
        }
        prefs.edit().putBoolean("accessibility_dialog_shown", true).apply()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (enabledServices != null) {
            return enabledServices.contains(packageName)
        }
        return false
    }

    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.accessibility_permission_title)
            .setMessage(R.string.accessibility_permission_message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateMonitoringState() {
        // 检查辅助功能是否启用
        val accessibilityEnabled = ClipboardAccessibilityService.isEnabled

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMonitoring = prefs.getBoolean(KEY_MONITORING, false)

        // 如果之前是开启状态但辅助功能未启用，则关闭
        if (savedMonitoring && !accessibilityEnabled) {
            isMonitoring = false
            saveMonitoringState()
            updateMonitoringUI()
        } else {
            isMonitoring = savedMonitoring
            updateMonitoringUI()
        }
    }

    private fun checkAndRequestPermissions() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PERMISSIONS_SHOWN, false)) {
            return
        }

        val permissionsToRequest = mutableListOf<String>()

        // 通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 剪贴板后台读取权限 (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.READ_CLIPBOARD_IN_BACKGROUND")
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add("android.permission.READ_CLIPBOARD_IN_BACKGROUND")
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        prefs.edit().putBoolean(KEY_PERMISSIONS_SHOWN, true).apply()
    }

    private fun restoreMonitoringState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isMonitoring = prefs.getBoolean(KEY_MONITORING, false)
        updateMonitoringUI()
    }

    private fun saveMonitoringState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MONITORING, isMonitoring).apply()
    }

    private fun setupRecyclerView() {
        adapter = ClipboardAdapter(
            onItemClick = { entry -> copyToClipboard(entry) },
            onItemLongClick = { entry -> showDeleteDialog(entry) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.buttonToggleMonitoring.setOnClickListener {
            toggleMonitoring()
        }

        binding.buttonClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }
    }

    private fun observeData() {
        viewModel.allEntries.observe(this) { entries ->
            adapter.submitList(entries)
            updateEmptyState(entries.isEmpty())
        }
    }

    private fun toggleMonitoring() {
        // 检查辅助功能是否启用
        if (!ClipboardAccessibilityService.isEnabled) {
            showAccessibilityPermissionDialog()
            return
        }

        isMonitoring = !isMonitoring

        if (isMonitoring) {
            Toast.makeText(this, getString(R.string.monitoring_active), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.monitoring_inactive), Toast.LENGTH_SHORT).show()
        }

        saveMonitoringState()
        updateMonitoringUI()
    }

    private fun updateMonitoringUI() {
        if (isMonitoring) {
            binding.buttonToggleMonitoring.text = getString(R.string.stop_monitoring)
            binding.statusText.text = getString(R.string.monitoring_active)
            binding.statusIndicator.setBackgroundResource(R.drawable.status_indicator_active)
        } else {
            binding.buttonToggleMonitoring.text = getString(R.string.start_monitoring)
            binding.statusText.text = getString(R.string.monitoring_inactive)
            binding.statusIndicator.setBackgroundResource(R.drawable.status_indicator_inactive)
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = android.view.View.VISIBLE
            binding.recyclerView.visibility = android.view.View.GONE
        } else {
            binding.emptyStateLayout.visibility = android.view.View.GONE
            binding.recyclerView.visibility = android.view.View.VISIBLE
        }
    }

    private fun copyToClipboard(entry: ClipboardEntry) {
        // 如果正在监听，先设置标志位防止触发循环
        if (isMonitoring) {
            ClipboardAccessibilityService.isSelfCopy = true
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("clipboard", entry.content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.copy_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteDialog(entry: ClipboardEntry) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.delete(entry)
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_history))
            .setMessage(getString(R.string.confirm_clear))
            .setPositiveButton(getString(R.string.clear_history)) { _, _ ->
                viewModel.deleteAll()
                Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun checkBatteryOptimization() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dialogShown = prefs.getBoolean(KEY_BATTERY_DIALOG_SHOWN, false)

        if (!dialogShown) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.battery_optimization_title))
                    .setMessage(getString(R.string.battery_optimization_message))
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            // 某些设备可能不支持，跳转到设置页面
                            try {
                                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (e2: Exception) {
                                // 忽略
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()

                prefs.edit().putBoolean(KEY_BATTERY_DIALOG_SHOWN, true).apply()
            }
        }
    }
}
