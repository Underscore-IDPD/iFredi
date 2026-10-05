package com.ifredi.chat.ui.viewmodel

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.ifredi.chat.data.repository.AuthRepository
import com.ifredi.chat.data.repository.UserRepository

enum class AuthField { NAME, EMAIL, PASSWORD, CONFIRM }

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class FieldError(val field: AuthField, val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    private val _state = MutableLiveData<AuthState>(AuthState.Idle)
    val state: LiveData<AuthState> = _state

    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
    }

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun resetState() {
        _state.value = AuthState.Idle
    }

    fun login(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            _state.value = AuthState.Error("Completa email y contraseña")
            return
        }
        _state.value = AuthState.Loading
        authRepository.login(cleanEmail, password) { result ->
            result
                .onSuccess { _state.value = AuthState.Success }
                .onFailure { e ->
                    _state.value = AuthState.Error("No se pudo iniciar sesión: ${e.localizedMessage}")
                }
        }
    }

    fun register(name: String, email: String, password: String, confirm: String) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()

        validate(cleanName, cleanEmail, password, confirm)?.let {
            _state.value = it
            return
        }

        _state.value = AuthState.Loading
        userRepository.registerUser(cleanEmail, password, cleanName) { result ->
            result
                .onSuccess { _state.value = AuthState.Success }
                .onFailure { e -> _state.value = AuthState.Error(errorMessageFor(e)) }
        }
    }

    private fun validate(
        name: String, email: String, password: String, confirm: String
    ): AuthState.FieldError? = when {
        name.isBlank() ->
            AuthState.FieldError(AuthField.NAME, "Ingresa un nombre de usuario")
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
            AuthState.FieldError(AuthField.EMAIL, "Correo electrónico inválido")
        password.length < MIN_PASSWORD_LENGTH ->
            AuthState.FieldError(AuthField.PASSWORD, "Mínimo $MIN_PASSWORD_LENGTH caracteres")
        password != confirm ->
            AuthState.FieldError(AuthField.CONFIRM, "Las contraseñas no coinciden")
        else -> null
    }

    private fun errorMessageFor(e: Throwable): String = when (e) {
        is FirebaseAuthUserCollisionException -> "Ese correo ya está registrado"
        is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil"
        is FirebaseAuthInvalidCredentialsException -> "Correo electrónico inválido"
        else -> "No se pudo crear la cuenta: ${e.localizedMessage}"
    }
}