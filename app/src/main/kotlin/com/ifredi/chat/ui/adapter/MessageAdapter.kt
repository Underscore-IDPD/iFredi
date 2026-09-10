package com.ifredi.chat.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.ifredi.chat.R
import com.ifredi.chat.data.Message
import com.ifredi.chat.data.MessageStatus
import com.ifredi.chat.databinding.MessageItemBinding

class MessageAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: (Message) -> Unit = {}
) : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = MessageItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // Actualiza la lista de mensajes en el adaptador
    fun setMessages(messages: List<Message>) {
        submitList(messages)
    }

    inner class MessageViewHolder(
        private val binding: MessageItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            val isOwn = message.isOwn(currentUserId)

            binding.llSentMessage.visibility = if (isOwn) View.VISIBLE else View.GONE
            binding.llReceivedMessage.visibility = if (isOwn) View.GONE else View.VISIBLE

            if (isOwn) {
                bindSent(message)
            } else {
                bindReceived(message)
            }

            itemView.setOnLongClickListener {
                onMessageLongClick(message)
                true
            }
        }

        private fun bindSent(message: Message) {
            binding.tvSentMessage.text = message.text
            binding.tvSentMessage.visibility = if (message.text.isBlank()) View.GONE else View.VISIBLE
            binding.tvSentTime.text = message.getFormattedTime()

            bindImage(message, binding.ivSentImage)
            binding.ivSentStatus.setImageResource(statusIcon(message.status))
        }

        private fun bindReceived(message: Message) {
            binding.tvSenderName.text = message.senderName
            binding.tvReceivedMessage.text = message.text
            binding.tvReceivedMessage.visibility =
                if (message.text.isBlank()) View.GONE else View.VISIBLE
            binding.tvReceivedTime.text = message.getFormattedTime()

            bindImage(message, binding.ivReceivedImage)
        }

        private fun bindImage(message: Message, imageView: android.widget.ImageView) {
            if (message.isImage()) {
                imageView.visibility = View.VISIBLE
                Glide.with(imageView.context)
                    .load(message.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_image_rounded)
                    .error(R.drawable.ic_broken_image)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(imageView)
            } else {
                imageView.visibility = View.GONE
            }
        }

        private fun statusIcon(status: MessageStatus): Int = when (status) {
            MessageStatus.PENDING -> R.drawable.ic_status_pending
            MessageStatus.SENT -> R.drawable.ic_status_sent
            MessageStatus.DELIVERED -> R.drawable.ic_status_delivered
            MessageStatus.READ -> R.drawable.ic_status_read
            MessageStatus.FAILED -> R.drawable.ic_status_failed
        }
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(old: Message, new: Message) = old.id == new.id
        override fun areContentsTheSame(old: Message, new: Message) = old == new
    }
}