package com.clipboard.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.clipboard.manager.adapter.ClipboardAdapter
import com.clipboard.manager.database.ClipboardEntry
import com.clipboard.manager.databinding.ActivityMainBinding
import com.clipboard.manager.ui.ClipboardViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ClipboardViewModel
    private lateinit var adapter: ClipboardAdapter
    private var isSearching = false

    private lateinit var clipboardManager: ClipboardManager
    private var lastClipboardContent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ClipboardViewModel::class.java]

        setupRecyclerView()
        setupButtons()
        setupSearch()
        observeData()

        // 初始化剪贴板监听
        setupClipboardListener()

        // 读取当前剪贴板内容并保存
        readCurrentClipboard()
    }

    private fun setupClipboardListener() {
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // 保存当前剪贴板内容
        lastClipboardContent = getCurrentClipboardText()

        // 添加剪贴板变化监听
        clipboardManager.addPrimaryClipChangedListener {
            val currentText = getCurrentClipboardText()
            if (currentText.isNotEmpty() && currentText != lastClipboardContent) {
                lastClipboardContent = currentText
                // 自动保存新内容
                viewModel.insert(ClipboardEntry(content = currentText))
            }
        }
    }

    private fun getCurrentClipboardText(): String {
        return try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString() ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    override fun onResume() {
        super.onResume()
        // 检查剪贴板是否有变化
        val currentText = getCurrentClipboardText()
        if (currentText.isNotEmpty() && currentText != lastClipboardContent) {
            lastClipboardContent = currentText
            viewModel.insert(ClipboardEntry(content = currentText))
        }
    }

    private fun readCurrentClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip

            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty()) {
                    lastClipboardContent = text
                    // 保存到数据库
                    viewModel.insert(ClipboardEntry(content = text))
                }
            }
        } catch (e: Exception) {
            // 静默处理异常
        }
    }

    private fun setupRecyclerView() {
        adapter = ClipboardAdapter(
            onCopyClick = { entry -> copyToClipboard(entry) },
            onFavoriteClick = { entry -> toggleFavorite(entry) },
            onNoteClick = { entry -> showNoteDialog(entry) },
            onDeleteClick = { entry -> showDeleteDialog(entry) },
            onItemClick = { entry -> showDetailDialog(entry) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.buttonClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }

        // 手动刷新按钮
        binding.buttonGetClipboard.setOnClickListener {
            readCurrentClipboard()
            Toast.makeText(this, "已刷新剪贴板", Toast.LENGTH_SHORT).show()
        }

        // 导出按钮
        binding.buttonExport.setOnClickListener {
            showExportDialog()
        }
    }

    private fun showExportDialog() {
        val options = arrayOf(
            getString(R.string.export_as_text),
            getString(R.string.export_as_json)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_export_format))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportAsText()
                    1 -> exportAsJson()
                }
            }
            .show()
    }

    private fun exportAsText() {
        viewModel.allEntries.value?.let { entries ->
            if (entries.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_data_to_export), Toast.LENGTH_SHORT).show()
                return
            }

            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val content = buildString {
                    appendLine("剪贴板历史导出")
                    appendLine("导出时间: ${sdf.format(Date())}")
                    appendLine("=" .repeat(50))
                    appendLine()

                    entries.forEachIndexed { index, entry ->
                        appendLine("${index + 1}. ${if (entry.isFavorite) "★ " else ""}")
                        appendLine("内容: ${entry.content}")
                        if (entry.note.isNotEmpty()) {
                            appendLine("备注: ${entry.note}")
                        }
                        appendLine("时间: ${sdf.format(Date(entry.timestamp))}")
                        appendLine("-".repeat(30))
                        appendLine()
                    }
                }

                shareFile(content, "clipboard_export.txt", "text/plain")
                Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportAsJson() {
        viewModel.allEntries.value?.let { entries ->
            if (entries.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_data_to_export), Toast.LENGTH_SHORT).show()
                return
            }

            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val jsonArray = JSONArray()

                entries.forEach { entry ->
                    val jsonObject = JSONObject().apply {
                        put("content", entry.content)
                        put("note", entry.note)
                        put("favorite", entry.isFavorite)
                        put("timestamp", entry.timestamp)
                        put("datetime", sdf.format(Date(entry.timestamp)))
                    }
                    jsonArray.put(jsonObject)
                }

                val jsonContent = JSONObject().apply {
                    put("export_time", sdf.format(Date()))
                    put("total_count", entries.size)
                    put("entries", jsonArray)
                }.toString(2)

                shareFile(jsonContent, "clipboard_export.json", "application/json")
                Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareFile(content: String, fileName: String, mimeType: String) {
        try {
            val file = File(getExternalFilesDir(null), fileName)
            file.writeText(content)

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_data)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                viewModel.search(query)
                isSearching = query.isNotEmpty()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeData() {
        viewModel.searchResults.observe(this) { entries ->
            adapter.submitList(entries)
            updateEmptyState(entries.isEmpty())
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.emptyText.text = if (isSearching) {
                getString(R.string.no_search_results)
            } else {
                getString(R.string.no_clipboard_data)
            }
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun copyToClipboard(entry: ClipboardEntry) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("clipboard", entry.content)
        clipboard.setPrimaryClip(clip)
        lastClipboardContent = entry.content
        Toast.makeText(this, getString(R.string.copy_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun toggleFavorite(entry: ClipboardEntry) {
        viewModel.updateFavorite(entry)
        if (entry.isFavorite) {
            Toast.makeText(this, getString(R.string.removed_from_favorite), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.added_to_favorite), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNoteDialog(entry: ClipboardEntry) {
        val editText = android.widget.EditText(this).apply {
            setText(entry.note)
            hint = getString(R.string.note_hint)
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (entry.note.isEmpty()) getString(R.string.add_note) else getString(R.string.edit_note))
            .setView(editText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val note = editText.text.toString()
                viewModel.updateNote(entry, note)
                Toast.makeText(this, getString(R.string.note_saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDetailDialog(entry: ClipboardEntry) {
        val scrollView = ScrollView(this).apply {
            setPadding(32, 16, 32, 16)
        }

        val textView = TextView(this).apply {
            text = entry.content
            textSize = 15f
            setTextColor(getColor(R.color.on_surface))
        }
        scrollView.addView(textView)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.detail))
            .setView(scrollView)
            .setPositiveButton(getString(R.string.copy)) { _, _ ->
                copyToClipboard(entry)
            }
            .setNeutralButton(getString(R.string.close), null)
            .create()

        dialog.show()
    }

    private fun showDeleteDialog(entry: ClipboardEntry) {
        MaterialAlertDialogBuilder(this)
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
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.clear_history))
            .setMessage(getString(R.string.confirm_clear))
            .setPositiveButton(getString(R.string.clear_history)) { _, _ ->
                viewModel.deleteAll()
                Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除剪贴板监听
        clipboardManager.removePrimaryClipChangedListener {}
    }
}
