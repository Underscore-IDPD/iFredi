package com.ifredi.chat.ui.activity

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.ifredi.chat.databinding.ActivityChatBinding
import com.ifredi.chat.ui.adapter.MessageAdapter
import com.ifredi.chat.ui.viewmodel.ChatViewModel

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var messageAdapter: MessageAdapter

    private var currentUserId: String = ""
    private var chatPartnerId: String = ""
    private var chatPartnerName: String = ""
    private var chatId: String = ""

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
        if (currentUserId.isNotEmpty()) {
            viewModel.markChatAsRead()
        }
    }

    override fun onPause() {
        super.onPause()
        // El estado offline definitivo se marca en onCleared() del ViewModel
    }

    override fun onDestroy() {
        super.onDestroy()
        // Los listeners de Firestore se remueven en ChatViewModel.onCleared()
    }

    /**
     * Setup inicial
     */

    // Configura el AppBarLayout con el nombre del usuario
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.title = chatPartnerName.ifBlank { "Chat" }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    // Inicializa el adapter y el layout manager del RecyclerView
    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(currentUserId) { message ->
            showMessageOptions(message)
        }
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
            messageAdapter.setMessages(messages)
            if (messages.isNotEmpty()) {
                binding.rvMessages.scrollToPosition(messages.size - 1)
            }
        }

        viewModel.isUserTyping.observe(this) { isTyping ->
            binding.tvTypingIndicator.visibility =
                if (isTyping) android.view.View.VISIBLE else android.view.View.GONE
            binding.tvTypingIndicator.text = "$chatPartnerName está escribiendo..."
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
            binding.toolbar.subtitle = chat.getDisplayName(currentUserId)
        }
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

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(arrayOf("Eliminar", "Copiar")) { _, which ->
                when (which) {
                    0 -> viewModel.deleteMessage(message)
                    1 -> copyToClipboard(message.text)
                }
            }
            .show()
    }

    // Copia el texto del mensaje al portapapeles
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("mensaje", text))
        Toast.makeText(this, "Mensaje copiado", Toast.LENGTH_SHORT).show()
    }
}