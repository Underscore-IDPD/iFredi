package com.ifredi.chat.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.ifredi.chat.databinding.ActivityLoginBinding
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser != null) {
            goToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Completa email y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { goToMain() }
                .addOnFailureListener { e ->
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(
                        this,
                        "No se pudo iniciar sesión: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        binding.tvCreateAccount.setOnClickListener {
            // TODO: navegar a RegisterActivity cuando exista
            Toast.makeText(this, "Pantalla de registro pendiente", Toast.LENGTH_SHORT).show()
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