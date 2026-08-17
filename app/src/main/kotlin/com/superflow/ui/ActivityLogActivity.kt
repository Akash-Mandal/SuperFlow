package com.superflow.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.superflow.data.AuditEntry
import com.superflow.domain.CommandBus
import com.superflow.util.Dates

/**
 * The shared Activity trail.
 *
 * Every change is here, whoever made it, with per-action and grouped undo.
 * This is the accountability that lets Full Control skip confirmations.
 */
class ActivityLogActivity : Activity() {

    private lateinit var bus: CommandBus
    private lateinit var host: FrameLayout
    private var filter = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bus = CommandBus.get(this)
        host = FrameLayout(this).apply {
            setBackgroundColor(Palette.BG)
            layoutParams = lp(MATCH, MATCH)
        }
        setContentView(host)
        render()
    }

    private fun render() {
        host.removeAllViews()
        host.addView(build(), FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun build(): View = scroller {
        setPadding(dp(20), dp(28), dp(20), dp(28))

        addView(title("Activity", 26f))
        addView(spacer(6))
        addView(body("Every change SuperFlow made, and who made it.", 14f, Palette.INK_FAINT))
        addView(spacer(14))

        addView(flowRow {
            for (f in listOf("ALL", "USER", "AI", "SYSTEM")) {
                addView(chip(f.lowercase().replaceFirstChar { it.uppercase() }, active = filter == f) {
                    filter = f; render()
                })
            }
        })

        val entries = bus.repo.audit(300).filter { filter == "ALL" || it.actor == filter }
        if (entries.isEmpty()) {
            addView(card {
                addView(body("Nothing recorded yet.", 15f, Palette.INK))
                addView(spacer(4))
                addView(body("Actions appear here the moment anything changes.",
                    13f, Palette.INK_FAINT))
            })
        }

        // Group consecutive entries that belong to the same AI run.
        val groups = LinkedHashMap<String, MutableList<AuditEntry>>()
        for (e in entries) {
            val key = e.groupId ?: e.id
            groups.getOrPut(key) { ArrayList() }.add(e)
        }

        for ((key, list) in groups) {
            if (list.size > 1) {
                addView(card {
                    background = rounded(Palette.SURFACE, dp(18), Palette.ACCENT, dp(1))
                    addView(row {
                        addView(body("Grouped run · ${list.size} actions", 14f, Palette.ACCENT, bold = true)
                            .apply { layoutParams = lp(0, WRAP, 1f) })
                        if (list.any { !it.undone }) {
                            addView(ghostButton("Undo all") {
                                val res = bus.undoGroup(key)
                                toast(res.message)
                                render()
                            })
                        }
                    })
                    addView(spacer(10))
                    for (e in list) addView(entryRow(e, compact = true))
                })
            } else {
                addView(card { addView(entryRow(list.first(), compact = false)) })
            }
        }

        addView(spacer(12))
        addView(row {
            addView(ghostButton("Clear trail", Palette.DANGER) {
                Dialogs.confirm(this@ActivityLogActivity,
                    "Clear the activity history? Undo data will be lost.") {
                    bus.repo.clearAudit()
                    render()
                }
            }.apply { layoutParams = lp(0, WRAP, 1f).apply { rightMargin = dp(8) } })
            addView(ghostButton("Close") { finish() }
                .apply { layoutParams = lp(0, WRAP, 1f) })
        })
        addView(spacer(24))
    }

    private fun entryRow(e: AuditEntry, compact: Boolean): View = row {
        layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(if (compact) 8 else 0) }
        addView(iconDot(when (e.actor) {
            "AI" -> Palette.ACCENT
            "SYSTEM" -> Palette.WARM
            else -> Palette.INK_FAINT
        }, 8))
        addView(column {
            layoutParams = lp(0, WRAP, 1f)
            addView(body(e.summary, if (compact) 13f else 15f,
                if (e.undone) Palette.INK_FAINT else Palette.INK, bold = !compact))
            addView(body(
                "${e.actor.lowercase()} · ${Dates.stamp(e.createdAt)}" +
                        if (e.undone) " · undone" else "",
                11f, Palette.INK_FAINT
            ))
        })
        if (!e.undone && e.undoPayload.isNotBlank()) {
            addView(ghostButton("Undo") {
                val res = bus.undo(e)
                toast(res.message)
                render()
            })
        }
    }
}
