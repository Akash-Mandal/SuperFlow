package com.superflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.superflow.design.Space
import com.superflow.ui.theme.SfTheme

/**
 * A section label, optionally with a trailing action (§12.1).
 *
 * Uppercased at render time rather than in the string resource, so
 * translations and screen readers get the natural-cased text. A screen
 * reader announcing "E X P E R I E N C E" letter by letter is a real
 * failure mode of baked-in capitals.
 *
 * Marked as a heading for accessibility, which is what lets a screen reader
 * user jump between sections instead of swiping through every row.
 */
@Composable
fun SfSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Space.XS.dp,
                end = Space.XS.dp,
                top = SfTheme.density.sectionSpacing.dp,
                bottom = Space.SM.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = SfTheme.type.overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
