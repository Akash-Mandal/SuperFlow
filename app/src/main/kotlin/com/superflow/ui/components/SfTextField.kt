package com.superflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.superflow.design.Space
import com.superflow.ui.theme.SfTheme

/**
 * The app's text input (§12.1).
 *
 * Adds three things to the Material field: a character counter that appears
 * only when it starts to matter, inline validation, and an error that is
 * announced rather than only coloured.
 *
 * @param maxLength when set, the field stops accepting input at the limit
 *                  rather than accepting it and complaining. Truncating what
 *                  someone typed is worse than not accepting it, but both
 *                  are worse than telling them the limit is approaching -
 *                  hence the counter.
 * @param error     when non-null, the field renders as invalid and the
 *                  message is attached to the field's accessibility node, so
 *                  a screen reader announces it on focus. Colour alone would
 *                  leave a colour-blind user with an unexplained red box.
 */
@Composable
fun SfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    error: String? = null,
    maxLength: Int? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    enabled: Boolean = true,
) {
    val isError = error != null

    // The counter appears at 80% of the limit. Showing it from the first
    // character turns every field into a test; showing it only at the limit
    // is too late to be useful.
    val counterThreshold = maxLength?.let { (it * 0.8f).toInt() }
    val showCounter = maxLength != null && value.length >= (counterThreshold ?: Int.MAX_VALUE)

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                val clamped = if (maxLength != null && next.length > maxLength) {
                    next.take(maxLength)
                } else {
                    next
                }
                onValueChange(clamped)
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    // Captured into a local first: inside this scope `error`
                    // is both the parameter and SemanticsPropertyReceiver's
                    // error() function, and the two reading the same is how
                    // a subtle bug hides.
                    val message = error
                    if (message != null) error(message)
                },
            enabled = enabled,
            isError = isError,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = singleLine,
            minLines = minLines,
            shape = SfTheme.shapes.field,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                errorBorderColor = MaterialTheme.colorScheme.error,
            ),
        )

        // The supporting row holds the message and the counter. It animates
        // in rather than appearing instantly so the layout below does not
        // jump as the user types past the threshold.
        AnimatedVisibility(
            visible = error != null || supportingText != null || showCounter,
            enter = fadeIn(SfTheme.motion.tween(SfTheme.motion.fast)) +
                expandVertically(SfTheme.motion.tween(SfTheme.motion.fast)),
            exit = fadeOut(SfTheme.motion.tween(SfTheme.motion.fast)) +
                shrinkVertically(SfTheme.motion.tween(SfTheme.motion.fast)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.MD.dp, end = Space.MD.dp, top = Space.XS.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = error ?: supportingText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (showCounter && maxLength != null) {
                    Text(
                        text = "${value.length}/$maxLength",
                        style = SfTheme.type.data,
                        color = if (value.length >= maxLength) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
