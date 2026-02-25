package com.clipboard.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.clipboard.manager.adapter.ClipboardAdapter
import com.clipboard.manager.database.ClipboardEntry
import com.clipboard.manager.databinding.ActivityMainBinding
import com.clipboard.manager.service.ClipboardMonitorService
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
    }

    private fun restoreMonitoringState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isMonitoring = prefs.getBoolean(KEY_MONITORING, false)
        updateMonitoringUI()

        if (isMonitoring) {
            startMonitoringService()
        }
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
        isMonitoring = !isMonitoring

        if (isMonitoring) {
            startMonitoringService()
            Toast.makeText(this, getString(R.string.monitoring_active), Toast.LENGTH_SHORT).show()
        } else {
            stopMonitoringService()
            Toast.makeText(this, getString(R.string.monitoring_inactive), Toast.LENGTH_SHORT).show()
        }

        saveMonitoringState()
        updateMonitoringUI()
    }

    private fun startMonitoringService() {
        val intent = Intent(this, ClipboardMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, ClipboardMonitorService::class.java)
        stopService(intent)
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
            ClipboardMonitorService.isSelfCopy = true
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
