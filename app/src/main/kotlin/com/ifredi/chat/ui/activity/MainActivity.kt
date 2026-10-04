package com.ifredi.chat.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.ifredi.chat.R
import com.ifredi.chat.databinding.ActivityMainBinding
import com.ifredi.chat.ui.adapter.ChatListAdapter
import com.ifredi.chat.ui.adapter.UserSearchAdapter
import com.ifredi.chat.ui.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var chatAdapter: ChatListAdapter
    private lateinit var searchAdapter: UserSearchAdapter

    private var currentUserId = ""
    private val searchRunnable = Runnable { viewModel.search(binding.etSearch.text.toString()) }

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

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupRecyclerViews()
        setupSearch()
        setupObservers()

        viewModel.initialize(currentUserId, user.email.orEmpty())
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
            viewModel.signOut()
            goToLogin()
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
        searchAdapter = UserSearchAdapter { user -> viewModel.startChatWith(user) }

        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = chatAdapter
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResults.adapter = searchAdapter
    }

    // Búsqueda con debounce de 300 ms
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.etSearch.removeCallbacks(searchRunnable)
                if (s.isNullOrBlank()) {
                    viewModel.search("")
                } else {
                    binding.etSearch.postDelayed(searchRunnable, 300)
                }
                updateUi()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupObservers() {
        viewModel.chats.observe(this) { chats ->
            chatAdapter.submitList(chats)
            updateUi()
        }

        viewModel.searchResults.observe(this) { users ->
            searchAdapter.submitList(users)
            updateUi()
        }

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
                binding.etSearch.text?.clear()
                openChat(target.chatId, target.partnerId, target.partnerName)
            }
        }
    }

    // Alterna entre lista de chats y resultados de búsqueda, y muestra el estado vacío
    private fun updateUi() {
        val searching = !binding.etSearch.text.isNullOrBlank()
        binding.rvChats.isVisible = !searching
        binding.rvSearchResults.isVisible = searching

        val isEmpty = if (searching) {
            viewModel.searchResults.value.isNullOrEmpty()
        } else {
            viewModel.chats.value.isNullOrEmpty()
        }
        val loading = viewModel.isLoading.value == true

        binding.tvEmpty.isVisible = isEmpty && !loading
        binding.tvEmpty.setText(if (searching) R.string.no_results else R.string.no_chats)
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
}