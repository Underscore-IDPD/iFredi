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
import com.ifredi.chat.ui.util.DateLabels

class MessageAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: (Message) -> Unit = {},
    private val onImageClick: (String) -> Unit = {}
) : ListAdapter<MessageAdapter.Row, MessageAdapter.MessageViewHolder>(RowDiffCallback()) {

    // Mensaje + si debe mostrar el separador de día arriba.
    // Al ser parte del item, DiffUtil detecta cuando un header aparece o desaparece.
    data class Row(val message: Message, val showDateHeader: Boolean)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = MessageItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // onCommitted se ejecuta cuando la lista ya está aplicada al RecyclerView
    fun setMessages(messages: List<Message>, onCommitted: () -> Unit = {}) {
        val rows = messages.mapIndexed { i, message ->
            Row(
                message = message,
                showDateHeader = i == 0 ||
                        !DateLabels.sameDay(messages[i - 1].timestamp, message.timestamp)
            )
        }
        submitList(rows, onCommitted)
    }

    inner class MessageViewHolder(
        private val binding: MessageItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: Row) {
            val message = row.message
            val isOwn = message.isOwn(currentUserId)

            binding.tvDateHeader.visibility = if (row.showDateHeader) View.VISIBLE else View.GONE
            if (row.showDateHeader) {
                binding.tvDateHeader.text =
                    DateLabels.dayLabel(binding.root.context, message.timestamp)
            }

            binding.llSentMessage.visibility = if (isOwn) View.VISIBLE else View.GONE
            binding.llReceivedMessage.visibility = if (isOwn) View.GONE else View.VISIBLE

            if (isOwn) bindSent(message) else bindReceived(message)

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
            bindAvatar(message)
        }

        // Carga la foto de perfil del remitente
        private fun bindAvatar(message: Message) {
            if (!message.senderImageUrl.isNullOrBlank()) {
                Glide.with(binding.ivSenderAvatar.context)
                    .load(message.senderImageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(binding.ivSenderAvatar)
            }
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

                imageView.setOnClickListener {
                    message.imageUrl?.let { url -> onImageClick(url) }
                }
            } else {
                imageView.visibility = View.GONE
                imageView.setOnClickListener(null)
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

    private class RowDiffCallback : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(old: Row, new: Row) = old.message.id == new.message.id
        override fun areContentsTheSame(old: Row, new: Row) = old == new
    }
}