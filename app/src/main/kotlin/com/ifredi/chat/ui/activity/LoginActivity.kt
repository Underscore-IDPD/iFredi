package com.ifredi.chat.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ifredi.chat.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Completa email y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: reemplazar por FirebaseAuth.signInWithEmailAndPassword()
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, "chat_prueba")
                putExtra(ChatActivity.EXTRA_PARTNER_ID, "usuario_prueba")
                putExtra(ChatActivity.EXTRA_PARTNER_NAME, "Juan Carlos")
            }
            startActivity(intent)
        }

        binding.tvCreateAccount.setOnClickListener {
            // TODO: navegar a RegisterActivity cuando exista
            Toast.makeText(this, "Pantalla de registro pendiente", Toast.LENGTH_SHORT).show()
        }
    }
}