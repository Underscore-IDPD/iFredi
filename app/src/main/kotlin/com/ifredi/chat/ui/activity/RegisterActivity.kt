package com.ifredi.chat.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.ifredi.chat.R
import com.ifredi.chat.data.repository.UserRepository
import com.ifredi.chat.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val userRepository = UserRepository()

    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.title.text = HtmlCompat.fromHtml(
            getText(R.string.name_logo).toString(),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )

        binding.btnBack.setOnClickListener { finish() }
        binding.tvHaveAccount.setOnClickListener { finish() }
        binding.btnRegister.setOnClickListener { attemptRegister() }

        binding.etConfirmPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister()
                true
            } else false
        }
    }

    private fun attemptRegister() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirmPassword.text.toString()

        if (!validate(name, email, password, confirm)) return

        setLoading(true)
        userRepository.registerUser(email, password, name) { result ->
            result
                .onSuccess { goToMain() }
                .onFailure { e ->
                    setLoading(false)
                    Toast.makeText(this, errorMessageFor(e), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun validate(name: String, email: String, password: String, confirm: String): Boolean {
        binding.etName.error = null
        binding.etEmail.error = null
        binding.etPassword.error = null
        binding.etConfirmPassword.error = null

        return when {
            name.isBlank() -> {
                binding.etName.error = "Ingresa un nombre de usuario"
                binding.etName.requestFocus()
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.etEmail.error = "Correo electrónico inválido"
                binding.etEmail.requestFocus()
                false
            }
            password.length < MIN_PASSWORD_LENGTH -> {
                binding.etPassword.error = "Mínimo $MIN_PASSWORD_LENGTH caracteres"
                binding.etPassword.requestFocus()
                false
            }
            password != confirm -> {
                binding.etConfirmPassword.error = "Las contraseñas no coinciden"
                binding.etConfirmPassword.requestFocus()
                false
            }
            else -> true
        }
    }

    private fun errorMessageFor(e: Throwable): String = when (e) {
        is FirebaseAuthUserCollisionException -> "Ese correo ya está registrado"
        is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil"
        is FirebaseAuthInvalidCredentialsException -> "Correo electrónico inválido"
        else -> "No se pudo crear la cuenta: ${e.localizedMessage}"
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