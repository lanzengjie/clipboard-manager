package com.clipboard.manager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clipboard.manager.R
import com.clipboard.manager.database.ClipboardEntry
import java.text.SimpleDateFormat
import java.util.*

class ClipboardAdapter(
    private val onItemClick: (ClipboardEntry) -> Unit,
    private val onItemLongClick: (ClipboardEntry) -> Unit
) : ListAdapter<ClipboardEntry, ClipboardAdapter.ClipboardViewHolder>(ClipboardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clipboard, parent, false)
        return ClipboardViewHolder(view, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: ClipboardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ClipboardViewHolder(
        itemView: View,
        private val onItemClick: (ClipboardEntry) -> Unit,
        private val onItemLongClick: (ClipboardEntry) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val contentText: TextView = itemView.findViewById(R.id.contentText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)

        fun bind(entry: ClipboardEntry) {
            contentText.text = entry.content
            timestampText.text = formatTimestamp(entry.timestamp)

            itemView.setOnClickListener {
                onItemClick(entry)
            }

            itemView.setOnLongClickListener {
                onItemLongClick(entry)
                true
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
