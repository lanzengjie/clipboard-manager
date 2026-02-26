package com.clipboard.manager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clipboard.manager.R
import com.clipboard.manager.database.ClipboardEntry
import java.text.SimpleDateFormat
import java.util.*

class ClipboardAdapter(
    private val onCopyClick: (ClipboardEntry) -> Unit,
    private val onFavoriteClick: (ClipboardEntry) -> Unit,
    private val onNoteClick: (ClipboardEntry) -> Unit,
    private val onDeleteClick: (ClipboardEntry) -> Unit,
    private val onItemClick: (ClipboardEntry) -> Unit
) : ListAdapter<ClipboardEntry, ClipboardAdapter.ClipboardViewHolder>(ClipboardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clipboard, parent, false)
        return ClipboardViewHolder(view, onCopyClick, onFavoriteClick, onNoteClick, onDeleteClick, onItemClick)
    }

    override fun onBindViewHolder(holder: ClipboardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ClipboardViewHolder(
        itemView: View,
        private val onCopyClick: (ClipboardEntry) -> Unit,
        private val onFavoriteClick: (ClipboardEntry) -> Unit,
        private val onNoteClick: (ClipboardEntry) -> Unit,
        private val onDeleteClick: (ClipboardEntry) -> Unit,
        private val onItemClick: (ClipboardEntry) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val contentText: TextView = itemView.findViewById(R.id.contentText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val noteText: TextView = itemView.findViewById(R.id.noteText)
        private val favoriteButton: ImageButton = itemView.findViewById(R.id.favoriteButton)
        private val noteButton: ImageButton = itemView.findViewById(R.id.noteButton)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(entry: ClipboardEntry) {
            contentText.text = entry.content
            timestampText.text = formatTimestamp(entry.timestamp)

            // 显示/隐藏备注
            if (entry.note.isNotEmpty()) {
                noteText.text = entry.note
                noteText.visibility = View.VISIBLE
            } else {
                noteText.visibility = View.GONE
            }

            // 更新收藏按钮图标
            val favoriteIcon = if (entry.isFavorite) {
                R.drawable.ic_favorite
            } else {
                R.drawable.ic_favorite_border
            }
            favoriteButton.setImageResource(favoriteIcon)

            // 收藏按钮点击事件
            favoriteButton.setOnClickListener {
                onFavoriteClick(entry)
            }

            // 备注按钮点击事件
            noteButton.setOnClickListener {
                onNoteClick(entry)
            }

            // 复制按钮点击事件
            copyButton.setOnClickListener {
                onCopyClick(entry)
            }

            // 删除按钮点击事件
            deleteButton.setOnClickListener {
                onDeleteClick(entry)
            }

            // 整个卡片点击查看详情
            itemView.setOnClickListener {
                onItemClick(entry)
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            return try {
                if (timestamp <= 0) {
                    ""
                } else {
                    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            } catch (e: Exception) {
                ""
            }
        }
    }

    class ClipboardDiffCallback : DiffUtil.ItemCallback<ClipboardEntry>() {
        override fun areItemsTheSame(oldItem: ClipboardEntry, newItem: ClipboardEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ClipboardEntry, newItem: ClipboardEntry): Boolean {
            return oldItem == newItem
        }
    }
}
