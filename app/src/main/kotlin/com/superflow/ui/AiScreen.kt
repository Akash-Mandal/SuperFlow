package com.superflow.ui

import android.content.Intent
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.superflow.ai.Agent
import com.superflow.ai.Coordinator
import com.superflow.data.AiMessage
import com.superflow.data.Prefs
import com.superflow.domain.Capabilities

/**
 * The AI tab: Ask SuperFlow, Blueprint Studio, Full Control status and the
 * shared Activity trail.
 */
class AiScreen(private val a: MainActivity) : Screen {

    var prefill: String? = null
    private var busy = false
    private lateinit var input: EditText
    private lateinit var transcript: LinearLayout
    private lateinit var scroll: ScrollView

    override fun build(): View {
        val agent = Agent.get(a)

        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp(MATCH, MATCH)
        }

        transcript = a.column(0)
        scroll = ScrollView(a).apply {
            isFillViewport = true
            setPadding(a.dp(20), a.dp(24), a.dp(20), a.dp(8))
            addView(transcript, lp(MATCH, WRAP))
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        paintTranscript()

        root.addView(scroll)
        root.addView(composer(agent))
        return root
    }

    /* ---------------------------------------------------------- transcript */

    private fun paintTranscript() {
        transcript.removeAllViews()

        transcript.addView(a.title("Ask SuperFlow"))
        transcript.addView(a.spacer(4))
        transcript.addView(a.body(
            "Tell me what you want in plain words. I use the same commands the buttons do.",
            14f, Palette.INK_FAINT
        ))

        transcript.addView(statusCard())
        transcript.addView(blueprintCard())

        val messages = a.repo.messages(60)
        if (messages.isEmpty()) {
            transcript.addView(a.heading("TRY"))
            transcript.addView(a.flowRow {
                for (s in listOf("How am I doing?", "Plan tomorrow", "Minimum mode",
                    "List habits", "Focus on write, walk", "Energy 3")) {
                    addView(a.chip(s) { submit(s) })
                }
            })
            transcript.addView(a.card {
                addView(a.body("Offline coach", 15f, Palette.INK, bold = true))
                addView(a.spacer(6))
                addView(a.body(Coordinator.coachCard(a.repo), 14f, Palette.INK_SOFT))
            })
        } else {
            transcript.addView(a.heading("CONVERSATION"))
            for (m in messages) transcript.addView(bubble(m))
        }

        if (busy) {
            transcript.addView(a.softCard(Palette.SURFACE_ALT) {
                addView(a.body("Thinking...", 14f, Palette.INK_SOFT))
            })
        }

        transcript.addView(a.spacer(8))
        transcript.addView(a.row {
            addView(a.ghostButton("Activity") {
                a.startActivity(Intent(a, ActivityLogActivity::class.java))
            }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
            addView(a.ghostButton("AI Engine") {
                a.startActivity(Intent(a, AiEngineActivity::class.java))
            }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(8) } })
            addView(a.ghostButton("Clear") {
                a.repo.clearMessages()
                a.refresh()
            }.apply { layoutParams = lp(0, WRAP, 1f) })
        })
        transcript.addView(a.spacer(16))

        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun bubble(m: AiMessage): View {
        val isUser = m.role == "user"
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(
                if (isUser) Palette.ACCENT_SOFT else Palette.SURFACE,
                a.dp(16),
                if (isUser) null else Palette.LINE,
                if (isUser) 0 else a.dp(1)
            )
            setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12))
            layoutParams = lp(MATCH, WRAP).apply {
                bottomMargin = a.dp(10)
                leftMargin = if (isUser) a.dp(32) else 0
                rightMargin = if (isUser) 0 else a.dp(32)
            }
            addView(a.body(if (isUser) "You" else "SuperFlow", 11f,
                if (isUser) Palette.ACCENT else Palette.INK_FAINT, bold = true))
            addView(a.spacer(4))
            addView(a.body(m.text, 15f, Palette.INK))
            if (!isUser && m.meta.isNotBlank()) {
                addView(a.spacer(6))
                addView(a.body("via ${m.meta} coordinator", 11f, Palette.INK_FAINT))
            }
        }
    }

    /* -------------------------------------------------------- status cards */

    private fun statusCard(): View {
        val p = a.prefs
        val full = p.fullControlActive()
        return a.softCard(if (full) Palette.ACCENT_SOFT else Palette.SURFACE_ALT) {
            addView(a.row {
                addView(a.body(if (full) "Full Control active" else "Full Control not activated",
                    15f, if (full) Palette.ACCENT else Palette.INK_SOFT, bold = true).apply {
                    layoutParams = lp(0, WRAP, 1f)
                })
                addView(a.ghostButton(if (full) "Manage" else "Activate") {
                    a.startActivity(Intent(a, AiEngineActivity::class.java))
                })
            })
            addView(a.spacer(6))
            addView(a.body(
                if (full) "AI can run every registered capability without asking again. " +
                        "Snapshots, Activity and undo stay on."
                else "Activate once to let AI complete multi-step and destructive work without " +
                        "repeated confirmations.",
                13f, Palette.INK_SOFT
            ))
            addView(a.spacer(8))
            addView(a.body(
                "Engine: " + (if (p.localCoordinatorOnly) "Local Coordinator only"
                else if (p.cloudReady()) "${p.providerName} · ${p.model}"
                else "Local Coordinator (no cloud configured)") +
                        " · ${Capabilities.all().size} capabilities",
                12f, Palette.INK_FAINT
            ))
        }
    }

    private fun blueprintCard(): View = a.card {
        background = rounded(Palette.SURFACE, a.dp(18), Palette.ACCENT, a.dp(2))
        addView(a.body("BLUEPRINT STUDIO", 11f, Palette.ACCENT, bold = true))
        addView(a.spacer(6))
        addView(a.body("Compile documents into a workspace", 17f, Palette.INK, bold = true))
        addView(a.spacer(6))
        addView(a.body(
            "Add Markdown, text or pasted notes plus your instructions. SuperFlow extracts a " +
                    "source-linked Requirement Ledger, designs the whole workspace, applies it, " +
                    "verifies the real result and reports every gap and assumption.",
            13f, Palette.INK_SOFT
        ))
        addView(a.primaryButton("Open Blueprint Studio") {
            a.startActivity(Intent(a, BlueprintActivity::class.java))
        })
    }

    /* ----------------------------------------------------------- composer */

    private fun composer(agent: Agent): View {
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Palette.SURFACE)
            setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(14))
            layoutParams = lp(MATCH, WRAP)
        }
        input = a.field("Tell SuperFlow what to do").apply {
            layoutParams = lp(0, WRAP, 1f).apply { rightMargin = a.dp(10); bottomMargin = 0 }
            prefill?.let { setText(it) }
        }
        prefill = null
        val send = TextView(a).apply {
            text = if (busy) "..." else "Send"
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = rounded(if (busy) Palette.INK_FAINT else Palette.ACCENT, a.dp(12))
            setPadding(a.dp(20), a.dp(13), a.dp(20), a.dp(13))
            isClickable = !busy
            setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) submit(text)
            }
        }
        bar.addView(input)
        bar.addView(send)
        return bar
    }

    private fun submit(text: String) {
        if (busy) return
        busy = true
        a.refresh()
        Agent.get(a).send(text) { outcome ->
            a.runOnUiThread {
                busy = false
                a.refresh()
                if (outcome.error != null) a.toast(outcome.error)
            }
        }
    }
}
