package com.ifredi.chat.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ifredi.chat.databinding.ActivityLoginBinding
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModelProvider
import com.ifredi.chat.R
import com.ifredi.chat.ui.viewmodel.AuthState
import com.ifredi.chat.ui.viewmodel.AuthViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        if (viewModel.isLoggedIn()) {
            goToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.title.text = HtmlCompat.fromHtml(
            getText(R.string.name_logo).toString(),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )

        binding.btnLogin.setOnClickListener {
            viewModel.login(
                binding.etEmail.text.toString(),
                binding.etPassword.text.toString()
            )
        }
        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        viewModel.state.observe(this) { state ->
            binding.btnLogin.isEnabled = state !is AuthState.Loading
            when (state) {
                is AuthState.Success -> goToMain()
                is AuthState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetState()
                }
                else -> Unit
            }
        }
    }

    private fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}