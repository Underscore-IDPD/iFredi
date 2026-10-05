package com.ifredi.chat.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.ifredi.chat.R
import com.ifredi.chat.databinding.ActivityMainBinding
import com.ifredi.chat.ui.adapter.ChatListAdapter
import com.ifredi.chat.ui.fragment.NewChatBottomSheet
import com.ifredi.chat.ui.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var chatAdapter: ChatListAdapter

    private var currentUserId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si no hay sesión, se va al login
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            goToLogin()
            return
        }
        currentUserId = user.uid

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.title.text = HtmlCompat.fromHtml(
            getText(R.string.contrast_logo).toString(),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupRecyclerViews()
        setupSearch()
        binding.fabNewChat.setOnClickListener {
            NewChatBottomSheet().show(supportFragmentManager, NewChatBottomSheet.TAG)
        }
        setupObservers()

        viewModel.initialize(currentUserId, user.email.orEmpty())

        askNotificationPermission()

        fetchAndSaveFcmToken(currentUserId)
    }

    /**
     * Menú
     */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        R.id.action_logout -> {
            viewModel.signOut { goToLogin() }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /**
     * Setup
     */

    private fun setupRecyclerViews() {
        chatAdapter = ChatListAdapter(currentUserId) { chat ->
            openChat(
                chatId = chat.id,
                partnerId = chat.getOtherUserId(currentUserId).orEmpty(),
                partnerName = chat.getDisplayName(currentUserId)
            )
        }
        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = chatAdapter
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { updateUi() }
    }

    private fun setupObservers() {
        viewModel.chats.observe(this) { updateUi() }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.isVisible = loading
            updateUi()
        }

        viewModel.errorMessage.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        viewModel.openChat.observe(this) { target ->
            if (target != null) {
                viewModel.consumeOpenChat()
                (supportFragmentManager.findFragmentByTag(NewChatBottomSheet.TAG) as? DialogFragment)?.dismiss()
                binding.etSearch.text?.clear()
                openChat(target.chatId, target.partnerId, target.partnerName)
            }
        }
    }

    private fun updateUi() {
        val query = binding.etSearch.text?.toString().orEmpty().trim()
        val all = viewModel.chats.value.orEmpty()
        val shown = if (query.isEmpty()) all
        else all.filter { it.getDisplayName(currentUserId).contains(query, ignoreCase = true) }

        chatAdapter.submitList(shown)

        val loading = viewModel.isLoading.value == true
        binding.layoutEmpty.isVisible = shown.isEmpty() && !loading
        binding.tvEmpty.setText(if (query.isEmpty()) R.string.no_chats else R.string.no_results)
        binding.tvEmptyHint.isVisible = query.isEmpty()
    }


    /**
     * Navegación
     */

    private fun openChat(chatId: String, partnerId: String, partnerName: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
            putExtra(ChatActivity.EXTRA_PARTNER_ID, partnerId)
            putExtra(ChatActivity.EXTRA_PARTNER_NAME, partnerName)
        }
        startActivity(intent)
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    /**
     * Notificaciones
     */

    // Declarar el launcher para pedir el permiso
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Las notificaciones están desactivadas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun fetchAndSaveFcmToken(userId: String) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(userId).update("fcmToken", token)
            }
        }
    }
}