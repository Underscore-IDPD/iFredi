package com.ifredi.chat.ui.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ifredi.chat.R
import com.ifredi.chat.data.Chat
import com.ifredi.chat.databinding.ItemChatBinding

class ChatListAdapter(
    private val currentUserId: String,
    private val onChatClick: (Chat) -> Unit
) : ListAdapter<Chat, ChatListAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(
        private val binding: ItemChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            binding.tvName.text = chat.getDisplayName(currentUserId)
            binding.tvLastMessage.text =
                chat.lastMessage.ifBlank { binding.root.context.getString(R.string.no_messages_yet) }

            val ctx = binding.root.context
            val unread = chat.unreadFor(currentUserId)
            val hasUnread = unread > 0

            binding.tvTime.text = chat.getFormattedLastMessageTime()
            binding.tvTime.isVisible = true
            binding.tvTime.setTextColor(ctx.getColor(if (hasUnread) R.color.primary else R.color.text_muted))

            binding.tvLastMessage.setTextColor(
                ctx.getColor(if (hasUnread) R.color.text_main else R.color.text_secondary)
            )
            binding.tvLastMessage.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)

            binding.tvUnread.isVisible = hasUnread
            binding.tvUnread.text = if (unread > 99) "99+" else unread.toString()

            binding.tvUnread.isVisible = chat.unreadFor(currentUserId) > 0
            binding.tvUnread.text = chat.unreadFor(currentUserId).toString()

            Glide.with(binding.ivAvatar)
                .load(chat.groupImageUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(binding.ivAvatar)

            binding.root.setOnClickListener { onChatClick(chat) }
        }
    }

    private class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Chat, newItem: Chat) = oldItem == newItem
    }
}