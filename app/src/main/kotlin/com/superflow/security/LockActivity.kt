package com.superflow.security

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.superflow.R
import com.superflow.data.Prefs
import com.superflow.ui.common.snack

/**
 * PIN lock screen shown on launch when [AppLock] is enabled.
 *
 * A successful unlock calls [AppLock.onUnlocked] and finishes; a back press
 * leaves the app rather than bypassing the lock.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var attempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(R.layout.activity_lock)

        val root = findViewById<View>(R.id.lock_root)
        val title = findViewById<TextView>(R.id.lock_title)
        val field = findViewById<TextInputEditText>(R.id.pin_field)
        val fieldLayout = findViewById<TextInputLayout>(R.id.pin_layout)
        val unlock = findViewById<MaterialButton>(R.id.btn_unlock)

        title.text = getString(R.string.app_name)

        unlock.setOnClickListener {
            val pin = field.text?.toString().orEmpty()
            if (AppLock.checkPin(prefs, pin)) {
                AppLock.onUnlocked()
                setResult(RESULT_OK)
                finish()
            } else {
                attempts++
                fieldLayout.error = "Incorrect PIN${if (attempts >= 3) " — take a breath" else ""}"
                field.text?.clear()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    override fun onBackPressed() {
        // Do not allow bypassing the lock with back.
        super.onBackPressed()
        finishAffinity()
    }
}
