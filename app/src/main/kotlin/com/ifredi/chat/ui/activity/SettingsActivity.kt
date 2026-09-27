package com.ifredi.chat.ui.activity

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.ifredi.chat.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var selectedPhotoUri: Uri? = null
    private var selectedSwatch: View? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPhotoUri = it
            Glide.with(this).load(it).centerCrop().into(binding.ivProfilePhoto)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnEditPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.ivProfilePhoto.setOnClickListener { pickImageLauncher.launch("image/*") }

        setupColorSwatches()

        binding.btnSaveSettings.setOnClickListener { saveSettings() }
    }

    // Marca visualmente el swatch tocado y desmarca el anterior
    private fun setupColorSwatches() {
        val swatches = listOf(
            binding.swatchBlue,
            binding.swatchRed,
            binding.swatchGreen,
            binding.swatchYellow
        )

        selectSwatch(binding.swatchBlue)

        swatches.forEach { swatch ->
            swatch.setOnClickListener { selectSwatch(swatch) }
        }
    }

    private fun selectSwatch(swatch: View) {
        selectedSwatch?.apply {
            animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            elevation = 0f
        }
        swatch.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start()
        swatch.elevation = 8f
        selectedSwatch = swatch
    }

    private fun saveSettings() {
        val username = binding.etUsername.text.toString().trim()

        if (username.isEmpty()) {
            Toast.makeText(this, "El nombre de usuario no puede estar vacío", Toast.LENGTH_SHORT).show()
            return
        }

        // TODO: reemplazar por la actualización real en Firestore
        Toast.makeText(this, "Cambios guardados (pendiente de conectar a Firebase)", Toast.LENGTH_SHORT).show()
        finish()
    }
}