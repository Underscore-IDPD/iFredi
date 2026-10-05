package com.ifredi.chat.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModelProvider
import com.ifredi.chat.R
import com.ifredi.chat.databinding.ActivityRegisterBinding
import com.ifredi.chat.ui.viewmodel.AuthField
import com.ifredi.chat.ui.viewmodel.AuthState
import com.ifredi.chat.ui.viewmodel.AuthViewModel

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var viewModel: AuthViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        binding.title.text = HtmlCompat.fromHtml(
            getText(R.string.name_logo).toString(),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )

        binding.btnBack.setOnClickListener { finish() }
        binding.tvHaveAccount.setOnClickListener { finish() }
        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.etConfirmPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptRegister(); true } else false
        }

        viewModel.state.observe(this) { state ->
            setLoading(state is AuthState.Loading)
            when (state) {
                is AuthState.Success -> goToMain()
                is AuthState.FieldError -> {
                    showFieldError(state)
                    viewModel.resetState()
                }
                is AuthState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetState()
                }
                else -> Unit
            }
        }
    }

    private fun attemptRegister() {
        listOf(binding.etName, binding.etEmail, binding.etPassword, binding.etConfirmPassword)
            .forEach { it.error = null }

        viewModel.register(
            binding.etName.text.toString(),
            binding.etEmail.text.toString(),
            binding.etPassword.text.toString(),
            binding.etConfirmPassword.text.toString()
        )
    }

    private fun showFieldError(e: AuthState.FieldError) {
        val target = when (e.field) {
            AuthField.NAME -> binding.etName
            AuthField.EMAIL -> binding.etEmail
            AuthField.PASSWORD -> binding.etPassword
            AuthField.CONFIRM -> binding.etConfirmPassword
        }
        target.error = e.message
        target.requestFocus()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.btnRegister.setText(if (loading) R.string.registering else R.string.register)
    }

    private fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}