package com.ifredi.chat.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.ifredi.chat.R
import com.ifredi.chat.databinding.ActivityChatBinding
import com.ifredi.chat.ui.adapter.MessageAdapter
import com.ifredi.chat.ui.viewmodel.ChatViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ifredi.chat.databinding.DialogMessageOptionsBinding
import com.ifredi.chat.notification.ChatSession

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var messageAdapter: MessageAdapter


    private var currentUserId: String = ""
    private var chatPartnerId: String = ""
    private var chatPartnerName: String = ""
    private var chatId: String = ""

    private var firstMessageId: String? = null
    private var lastMessageId: String? = null
    private var messageCount = 0

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_PARTNER_ID = "extra_partner_id"
        const val EXTRA_PARTNER_NAME = "extra_partner_name"
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImageSelected(it) }
    }

    /**
     * Ciclo de vida
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
        chatPartnerId = intent.getStringExtra(EXTRA_PARTNER_ID) ?: ""
        chatPartnerName = intent.getStringExtra(EXTRA_PARTNER_NAME) ?: ""

        if (currentUserId.isEmpty() || chatId.isEmpty()) {
            Toast.makeText(this, "No se pudo abrir el chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupViewModelObservers()
        setupInputListeners()

        viewModel.initialize(currentUserId, chatId)
    }

    override fun onStart() {
        super.onStart()
        ChatSession.openChatId = chatId
        NotificationManagerCompat.from(this).cancel(chatId.hashCode())
        viewModel.onScreenVisible(true)
    }

    override fun onStop() {
        super.onStop()
        if (ChatSession.openChatId == chatId) ChatSession.openChatId = null
        viewModel.onScreenVisible(false)
        viewModel.stopTyping() // del cambio del indicador "escribiendo"
    }

    /**
     * Setup inicial
     */

    // Configura el AppBarLayout con el nombre y avatar de la otra persona
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.tvPartnerName.text = chatPartnerName.ifBlank { "Chat" }
        binding.tvPartnerStatus.text = getString(R.string.offline)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    // Carga el avatar de la otra persona
    private fun bindPartnerAvatar(imageUrl: String?) {
        if (!imageUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(imageUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(binding.ivPartnerAvatar)
        }
    }

    // Inicializa el adapter y el layout manager del RecyclerView
    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(
            currentUserId = currentUserId,
            onMessageLongClick = { message ->
                showMessageOptions(message)
            },
            onImageClick = { imageUrl ->
                val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                    putExtra(FullScreenImageActivity.EXTRA_IMAGE_URL, imageUrl)
                }
                startActivity(intent)
            }
        )

        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    // Escucha los cambios en los LiveData del ViewModel
    private fun setupViewModelObservers() {
        viewModel.messages.observe(this) { messages ->
            val lm = binding.rvMessages.layoutManager as LinearLayoutManager
            val first = messages.firstOrNull()?.id
            val last = messages.lastOrNull()?.id
            val added = messages.size - messageCount

            val prependedOlder = firstMessageId != null && first != firstMessageId &&
                    last == lastMessageId && added > 0
            val appendedNewer = last != lastMessageId && messages.isNotEmpty()

            // El ancla se captura ANTES de enviar la lista nueva
            val anchorPos = lm.findFirstVisibleItemPosition()
            val anchorTop = if (anchorPos != RecyclerView.NO_POSITION) {
                lm.findViewByPosition(anchorPos)?.top ?: 0
            } else 0

            firstMessageId = first
            lastMessageId = last
            messageCount = messages.size

            // El scroll se aplica cuando DiffUtil ya terminó de commitear la lista
            messageAdapter.setMessages(messages) {
                when {
                    prependedOlder && anchorPos != RecyclerView.NO_POSITION ->
                        lm.scrollToPositionWithOffset(anchorPos + added, anchorTop)
                    appendedNewer ->
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                }
            }
        }
        viewModel.isUserTyping.observe(this) { isTyping ->
            binding.tvTypingIndicator.visibility =
                if (isTyping) android.view.View.VISIBLE else android.view.View.GONE
            binding.tvTypingIndicator.text = if (isTyping) getString(R.string.typing) else ""
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility =
                if (loading) android.view.View.VISIBLE else android.view.View.GONE
        }

        viewModel.errorMessage.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        viewModel.chat.observe(this) { chat ->
            binding.tvPartnerName.text = chat.getDisplayName(currentUserId)
            // El avatar real
        }

        viewModel.partnerUser.observe(this) { user ->
            if (user != null) {
                // Update the status text dynamically
                binding.tvPartnerStatus.text = if (user.isActive()) {
                    "En línea"
                } else {
                    getString(R.string.offline) // Or format user.lastSeen for "Última vez..."
                }

                // Update the avatar
                bindPartnerAvatar(user.profileImageUrl)
            }
        }

        viewModel.canLoadOlder.observe(this) { binding.btnLoadOlder.isVisible = it }
        viewModel.loadingOlder.observe(this) { loading ->
            binding.btnLoadOlder.setText(
                if (loading) R.string.loading_older_messages else R.string.load_older_messages
            )
            binding.btnLoadOlder.isEnabled = !loading
        }
        binding.btnLoadOlder.setOnClickListener { viewModel.loadOlderMessages() }
    }

    // Listeners de los botones y del campo de texto
    private fun setupInputListeners() {
        binding.btnSendMessage.setOnClickListener {
            val text = binding.etMessage.text.toString()
            viewModel.sendMessage(text)
            binding.etMessage.text?.clear()
        }

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    viewModel.sendTypingIndicator()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnAttachImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    /**
     * Imágenes
     */

    // Sube la imagen seleccionada y la envía como mensaje
    private fun onImageSelected(uri: Uri) {
        viewModel.uploadImage(uri) { downloadUrl ->
            if (downloadUrl != null) {
                viewModel.sendMessage(text = "", imageUrl = downloadUrl)
            }
        }
    }

    /**
     * Opciones de mensaje
     */

    // Muestra el diálogo de opciones (eliminar, copiar) para un mensaje propio
    private fun showMessageOptions(message: com.ifredi.chat.data.Message) {
        if (!message.isOwn(currentUserId)) return

        val dialog = BottomSheetDialog(this)
        val sheetBinding = DialogMessageOptionsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.btnCopy.setOnClickListener {
            copyToClipboard(message.text)
            dialog.dismiss()
        }

        sheetBinding.btnDelete.setOnClickListener {
            viewModel.deleteMessage(message)
            dialog.dismiss()
        }

        dialog.show()
    }

    // Copia el texto del mensaje al portapapeles
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("mensaje", text))
        Toast.makeText(this, "Mensaje copiado", Toast.LENGTH_SHORT).show()
    }
}