package com.superflow

import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.superflow.data.Prefs

/**
 * The lock screen.
 *
 * Unlocks with a PIN, biometrics, or both depending on the setting. On API
 * 26–27 (no framework biometric prompt) it falls back to PIN automatically.
 * The PIN itself is stored only as a salted SHA-256 in the secrets file.
 */
class AppLockActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var biometricShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(R.layout.activity_app_lock)

        val subtitle = findViewById<TextView>(R.id.lock_subtitle)
        val useBiometric = prefs.appLockMethod != "pin" && AppLock.biometricAvailable(this)
        if (useBiometric) {
            subtitle.text = "Unlock with biometrics, or enter your PIN"
        } else if (prefs.appLockMethod == "biometric" && !AppLock.biometricAvailable(this)) {
            subtitle.text = "Biometrics unavailable — enter your PIN"
        }

        val input = findViewById<TextInputEditText>(R.id.pin_input)
        val unlock = findViewById<MaterialButton>(R.id.unlock_btn)
        unlock.setOnClickListener { attemptPin(input) }
        input.setOnEditorActionListener { _, _, _ -> attemptPin(input); true }
    }

    override fun onStart() {
        super.onStart()
        val useBiometric = prefs.appLockMethod != "pin" && AppLock.biometricAvailable(this)
        if (useBiometric && !biometricShown) {
            biometricShown = true
            showBiometric()
        }
    }

    private fun attemptPin(input: TextInputEditText) {
        val pin = input.text?.toString().orEmpty()
        if (prefs.verifyPin(pin)) {
            AppLock.unlock()
            finish()
        } else {
            input.error = "Wrong PIN"
            input.setText("")
        }
    }

    private fun showBiometric() {
        if (Build.VERSION.SDK_INT < 28) return
        val prompt = android.hardware.biometrics.BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult
                ) {
                    AppLock.unlock()
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // The user can fall back to the PIN field.
                }

                override fun onAuthenticationFailed() {
                    // One bad read is not an error; let them try again.
                }
            }
        )
        val info = android.hardware.biometrics.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SuperFlow")
            .setSubtitle("Confirm it is you")
            .setNegativeButtonText("Use PIN")
            .build()
        runCatching { prompt.authenticate(info) }
    }
}
