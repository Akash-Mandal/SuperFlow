package com.superflow.ui.sheets

import androidx.fragment.app.FragmentManager

/** Two-field sheet for an if-then Obstacle Plan. */
object ObstacleSheet {
    fun show(fm: FragmentManager, onSave: (String, String) -> Unit) {
        EntityEditorSheet.system(
            fm, "", "", emptyList(), null
        ) { _, _, _ -> }
        // Reuse the generic editor with obstacle wording.
        TextInputSheet.show(fm, "Obstacle plan", "If this happens…",
            subtitle = "Decide the fallback before you need it.") { ifText ->
            if (ifText.isNotBlank()) {
                TextInputSheet.show(fm, "Then I will…", "then…") { thenText ->
                    if (thenText.isNotBlank()) onSave(ifText.trim(), thenText.trim())
                }
            }
        }
    }
}
