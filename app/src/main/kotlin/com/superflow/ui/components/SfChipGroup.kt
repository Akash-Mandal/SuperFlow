package com.superflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.superflow.design.Space
import com.superflow.ui.theme.SfTheme

/**
 * An adaptive chip group (§12.1).
 *
 * Wraps onto as many lines as it needs rather than scrolling horizontally.
 * A horizontally scrolling chip row hides options off-screen with no
 * affordance, and users routinely never discover them.
 *
 * @param singleSelect when true, behaves like a radio group: picking one
 *                     clears the rest, and the current choice cannot be
 *                     deselected by tapping it again. When false, chips
 *                     toggle independently.
 */
data class SfChip(
    val id: String,
    val label: String,
    /** Colour dot before the label, for level and entity chips. */
    val leadingColor: Color? = null,
    val enabled: Boolean = true,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SfChipGroup(
    chips: List<SfChip>,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    singleSelect: Boolean = false,
    /**
     * Announced after each chip's own label, e.g. "Standard, filter by
     * level". Named groupLabel rather than label because FilterChip has a
     * `label` slot and a shadowed name here would be genuinely ambiguous to
     * read.
     */
    groupLabel: String? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.SM.dp),
        verticalArrangement = Arrangement.spacedBy(Space.SM.dp),
    ) {
        for (chip in chips) {
            val isSelected = chip.id in selected
            FilterChip(
                selected = isSelected,
                enabled = chip.enabled,
                onClick = {
                    val next = when {
                        // In single-select, tapping the current choice is a
                        // no-op rather than a deselect: a filter group with
                        // nothing selected usually means "show everything",
                        // and users hit that state by accident.
                        singleSelect && isSelected -> selected
                        singleSelect -> setOf(chip.id)
                        isSelected -> selected - chip.id
                        else -> selected + chip.id
                    }
                    if (next != selected) onSelectionChange(next)
                },
                label = { Text(chip.label, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = if (chip.leadingColor != null) {
                    { SfDot(color = chip.leadingColor) }
                } else {
                    null
                },
                shape = SfTheme.shapes.pill,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = chip.enabled,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    // High contrast thickens every chip edge, since the
                    // selected state is otherwise carried by fill alone.
                    borderWidth = if (SfTheme.highContrast) 2.dp else 1.dp,
                ),
                modifier = Modifier
                    // Chips are small targets; the 48dp minimum is not
                    // negotiable even when the label is one character.
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        this.selected = isSelected
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                        if (groupLabel != null) contentDescription = "${chip.label}, $groupLabel"
                    },
            )
        }
    }
}

/** The colour dot used as a chip's leading icon. */
@Composable
private fun SfDot(color: Color) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .defaultMinSize(minWidth = 10.dp, minHeight = 10.dp)
            // The dot repeats information the label already carries, so it
            // is hidden from screen readers rather than announced as an
            // unlabelled image.
            .clearAndSetSemantics { },
    ) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}
