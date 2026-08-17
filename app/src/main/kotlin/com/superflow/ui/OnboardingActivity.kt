package com.superflow.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.superflow.data.LifeArea
import com.superflow.data.Prefs
import com.superflow.domain.Actor
import com.superflow.domain.CommandBus
import com.superflow.util.jsonOf

/**
 * Onboarding: from aspiration to first action in under five minutes.
 *
 * Skippable, no account wall, no name required. Notification permission is
 * requested only at the reminder step, with the reason stated.
 */
class OnboardingActivity : Activity() {

    private lateinit var bus: CommandBus
    private lateinit var prefs: Prefs
    private lateinit var host: FrameLayout

    private var step = 0
    private var area = LifeArea.HEALTH
    private var identityText = ""
    private var goalText = ""
    private var whyText = ""
    private var systemText = ""
    private var habitText = ""
    private var tinyText = ""
    private var cueTime = ""
    private var anchorText = ""
    private var rewardText = ""
    private var wantsReminder = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bus = CommandBus.get(this)
        prefs = Prefs.get(this)
        host = FrameLayout(this).apply {
            setBackgroundColor(Palette.BG)
            layoutParams = lp(MATCH, MATCH)
        }
        setContentView(host)
        render()
    }

    private fun render() {
        host.removeAllViews()
        val view = when (step) {
            0 -> welcome()
            1 -> pickArea()
            2 -> identity()
            3 -> goal()
            4 -> system()
            5 -> habit()
            6 -> cue()
            7 -> feel()
            else -> finish0()
        }
        host.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun page(title: String, subtitle: String, block: LinearLayout.() -> Unit): View =
        scroller {
            setPadding(dp(24), dp(48), dp(24), dp(28))
            addView(body("STEP ${step + 1} OF 8", 11f, Palette.ACCENT, bold = true))
            addView(spacer(10))
            addView(title(title, 27f))
            addView(spacer(8))
            addView(body(subtitle, 15f, Palette.INK_SOFT))
            addView(spacer(24))
            block()
        }

    /* ------------------------------------------------------------- screens */

    private fun welcome(): View = scroller {
        setPadding(dp(24), dp(80), dp(24), dp(28))
        addView(title("SuperFlow", 40f))
        addView(spacer(12))
        addView(body("Shape your system. Become your future self, one small action at a time.",
            17f, Palette.INK_SOFT))
        addView(spacer(28))
        addView(softCard(Palette.ACCENT_SOFT) {
            addView(body("A promise", 13f, Palette.ACCENT, bold = true))
            addView(spacer(8))
            addView(body(
                "Everything stays on your device. No account, no ads, no streak guilt, " +
                        "nothing uploaded unless you connect a provider yourself.",
                14f, Palette.INK))
        })
        addView(spacer(8))
        addView(card {
            addView(body("How it works", 15f, Palette.INK, bold = true))
            addView(spacer(10))
            for ((k, v) in listOf(
                "Identity" to "who you are becoming",
                "Goal" to "the outcome that would matter",
                "System" to "the repeatable process",
                "Habit" to "the smallest useful action",
                "Review" to "improve the system, not blame yourself"
            )) {
                addView(row {
                    layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(8) }
                    addView(body(k, 14f, Palette.ACCENT, bold = true).apply {
                        layoutParams = lp(dp(80), WRAP)
                    })
                    addView(body(v, 14f, Palette.INK_SOFT).apply { layoutParams = lp(0, WRAP, 1f) })
                })
            }
        })
        addView(spacer(12))
        addView(primaryButton("Begin") { step = 1; render() })
        addView(spacer(8))
        addView(ghostButton("Skip for now") { complete(skipped = true) })
    }

    private fun pickArea(): View = page(
        "Where do you want to grow?", "One area is enough to start."
    ) {
        val chips = flowRow {}
        fun paint() {
            chips.removeAllViews()
            for (la in LifeArea.values()) {
                chips.addView(chip(la.label, active = la == area) { area = la; paint() })
            }
        }
        paint()
        addView(chips)
        addView(spacer(16))
        addView(primaryButton("Continue") { step = 2; render() })
        addView(back())
    }

    private fun identity(): View = page(
        "Who are you becoming?", "Habits stick when they say something true about you."
    ) {
        val input = field("someone who moves every day", identityText, lines = 2)
        addView(label("I am becoming..."))
        addView(input)
        addView(spacer(8))
        addView(body("Examples: someone who takes care of their body · a person who writes daily · " +
                "someone who is present with their family", 13f, Palette.INK_FAINT))
        addView(spacer(16))
        addView(primaryButton("Continue") {
            identityText = input.text.toString().trim()
            if (identityText.isBlank()) toast("Write something, even roughly")
            else { step = 3; render() }
        })
        addView(back())
    }

    private fun goal(): View = page(
        "What outcome would matter?", "A goal gives direction. Your system does the work."
    ) {
        val g = field("Comfortably walk 5 km", goalText)
        val w = field("Why does this matter to you?", whyText, lines = 3)
        addView(label("Goal")); addView(g)
        addView(label("Why")); addView(w)
        addView(spacer(16))
        addView(primaryButton("Continue") {
            goalText = g.text.toString().trim()
            whyText = w.text.toString().trim()
            if (goalText.isBlank()) toast("Name the outcome") else { step = 4; render() }
        })
        addView(back())
    }

    private fun system(): View = page(
        "What process could produce it?", "Describe the repeatable routine, not the result."
    ) {
        val s = field("Move after breakfast on weekdays", systemText, lines = 2)
        addView(label("System")); addView(s)
        addView(spacer(16))
        addView(primaryButton("Continue") {
            systemText = s.text.toString().trim().ifBlank { "My $goalText routine" }
            step = 5; render()
        })
        addView(back())
    }

    private fun habit(): View = page(
        "Pick one habit", "And the smallest version you could do on your worst day."
    ) {
        val h = field("Walk for 10 minutes", habitText)
        val t = field("Put on my shoes and step outside", tinyText)
        addView(label("Habit")); addView(h)
        addView(label("Tiny start (about two minutes)")); addView(t)
        addView(spacer(8))
        addView(body("The tiny version is not a lesser version. It is the one that survives a bad week.",
            13f, Palette.INK_FAINT))
        addView(spacer(16))
        addView(primaryButton("Continue") {
            habitText = h.text.toString().trim()
            tinyText = t.text.toString().trim()
            if (habitText.isBlank()) toast("Name the habit") else { step = 6; render() }
        })
        addView(back())
    }

    private fun cue(): View = page(
        "What will make you notice it?", "A time and place, or an existing routine to attach to."
    ) {
        val time = field("07:30", cueTime)
        val anchor = field("breakfast", anchorText)
        addView(label("At this time (optional)")); addView(time)
        addView(label("Or after this existing routine (optional)")); addView(anchor)
        addView(spacer(16))
        addView(primaryButton("Continue") {
            cueTime = time.text.toString().trim()
            anchorText = anchor.text.toString().trim()
            step = 7; render()
        })
        addView(back())
    }

    private fun feel(): View = page(
        "What makes it worth it?", "An immediate, honest payoff right after the action."
    ) {
        val r = field("Listen to my favourite playlist", rewardText)
        addView(label("Reward")); addView(r)
        addView(spacer(14))
        addView(card {
            addView(row {
                addView(body("Remind me at my cue time", 15f, Palette.INK).apply {
                    layoutParams = lp(0, WRAP, 1f)
                })
                val c = chip(if (wantsReminder) "On" else "Off", active = wantsReminder)
                c.setOnClickListener { wantsReminder = !wantsReminder; render() }
                addView(c)
            })
            addView(spacer(6))
            addView(body("Android will ask for notification permission so SuperFlow can send " +
                    "that one reminder. Nothing else uses it.", 12f, Palette.INK_FAINT))
        })
        addView(spacer(12))
        addView(primaryButton("Create my system") {
            rewardText = r.text.toString().trim()
            complete(skipped = false)
        })
        addView(back())
    }

    private fun finish0(): View = page("All set", "") {}

    private fun back(): View = ghostButton("Back") {
        if (step > 0) { step--; render() }
    }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(10) }

    /* -------------------------------------------------------------- finish */

    private fun complete(skipped: Boolean) {
        if (!skipped) {
            val identityRes = bus.execute("create_identity",
                jsonOf("statement" to identityText, "lifeArea" to area.name), Actor.USER)
            val identityId = identityRes.data?.optString("id")

            val goalRes = bus.execute("create_goal",
                jsonOf("title" to goalText, "why" to whyText, "identityId" to identityId), Actor.USER)
            val goalId = goalRes.data?.optString("id")

            val sysRes = bus.execute("create_system",
                jsonOf("title" to systemText, "goalId" to goalId), Actor.USER)
            val systemId = sysRes.data?.optString("id")

            bus.execute("create_habit", jsonOf(
                "title" to habitText,
                "tinyStart" to tinyText.ifBlank { "Start for two minutes" },
                "standardVersion" to habitText,
                "cueTime" to cueTime,
                "anchorText" to anchorText,
                "reward" to rewardText,
                "systemId" to systemId,
                "identityId" to identityId,
                "reminder" to wantsReminder,
                "days" to "daily"
            ), Actor.USER)

            if (wantsReminder && android.os.Build.VERSION.SDK_INT >= 33) {
                val perm = "android.permission.POST_NOTIFICATIONS"
                if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(perm), 101)
                }
            }
        }
        prefs.onboarded = true
        com.superflow.notify.Reminders.rescheduleAll(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
