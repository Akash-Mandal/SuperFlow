package com.superflow.ui.journal

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.superflow.R
import com.superflow.data.Repository
import com.superflow.ui.common.SfTheme
import com.superflow.data.model.JournalEntry
import com.superflow.ui.common.snack
import com.superflow.core.time.SfTime

/**
 * Journal: free-form reflection with guided prompts (Section 5.5).
 *
 * Features:
 *  - Guided prompts (5 options)
 *  - Mood slider (1-5)
 *  - Free-form text entry
 *  - Auto-tags with today's date
 */
class JournalActivity : AppCompatActivity() {

    private lateinit var repo: Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SfTheme.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal)
        repo = Repository.get(this)

        val root = findViewById<android.view.View>(R.id.journal_root) ?: findViewById(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(top = bars.top, bottom = maxOf(bars.bottom, ime.bottom))
            insets
        }

        val promptGroup = findViewById<ChipGroup>(R.id.journal_prompts)
        val content = findViewById<TextInputEditText>(R.id.journal_content)
        val moodSlider = findViewById<Slider>(R.id.journal_mood)
        val saveBtn = findViewById<MaterialButton>(R.id.journal_save)
        val cancelBtn = findViewById<MaterialButton>(R.id.journal_cancel)

        // Guided prompts (chips)
        val prompts = listOf(
            "What worked today?",
            "What would you tell your past self?",
            "What evidence did you collect about who you're becoming?",
            "What was the best moment today?",
            "What is one thing you are grateful for?"
        )
        for (prompt in prompts) {
            promptGroup.addView(Chip(this).apply {
                text = prompt
                isCheckable = false
                isClickable = true
                setEnsureMinTouchTargetSize(false)
                setOnClickListener {
                    content.setText(prompt + "\n\n")
                    content.setSelection(content.text!!.length)
                }
            })
        }

        saveBtn.setOnClickListener {
            val text = content.text?.toString()?.trim()
            if (text.isNullOrBlank()) {
                snack("Write something first")
                return@setOnClickListener
            }
            val entry = JournalEntry(
                date = SfTime.format(repo.clock.today()),
                content = text,
                mood = moodSlider.value.toInt().coerceIn(1, 5)
            )
            repo.saveJournalEntry(entry)

            // Offer to remember key insights as AiMemory
            if (entry.content.length > 50) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Remember this?")
                    .setMessage("Would you like me to remember key insights from this entry?")
                    .setPositiveButton("Remember") { _, _ ->
                        val memory = com.superflow.data.model.AiMemory(
                            category = com.superflow.data.model.MemoryCategory.USER_CONTEXT,
                            content = entry.content.take(200),
                            importance = (entry.mood ?: 3)
                        )
                        repo.saveMemory(memory)
                        Toast.makeText(this, "I'll remember that", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("No thanks", null)
                    .show()
            }

            snack("Journal entry saved for ${SfTime.humanDay(repo.clock.today())}")
            finish()
        }

        cancelBtn.setOnClickListener { finish() }
    }
}