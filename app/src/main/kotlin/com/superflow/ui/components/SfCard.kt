package com.superflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.superflow.ui.theme.SfTheme

/**
 * The app's card (§7.3).
 *
 * Six variants, each with a job. The distinction that matters is not how
 * they look but what they mean: [SfCardVariant.Accent] says "this is about
 * who you are", [SfCardVariant.Warm] says "this is advice", and using them
 * interchangeably would make both meaningless.
 *
 * Elevation is deliberately restrained. The plan calls for level 1 with a
 * subtle shadow on elevated cards, and everything else sits flat with a
 * stroke - a screen where every card floats reads as noisy rather than
 * layered.
 */
enum class SfCardVariant {
    /** Habit and entity cards: the default for a tappable object. */
    Elevated,

    /** Section grouping and inline content. Filled, no shadow. */
    Filled,

    /** Interactive areas that are not objects: pickers, toggles. */
    Outlined,

    /** Identity statements and celebrations. */
    Accent,

    /** Coaching tips and "worth knowing" asides. */
    Warm,

    /**
     * Bottom sheets.
     *
     * A real blur needs a RenderEffect and a backdrop, which a card cannot
     * see. This renders as a high-opacity surface; the blur belongs to the
     * sheet scaffold that has the backdrop, and lives in SfBottomSheet.
     */
    Glass,
}

/**
 * @param accentColor when non-null, draws a 3dp leading border in this
 *                    colour. The entity accent treatment from §6.5 -
 *                    identity, goal, system and habit each get their own,
 *                    which is what makes a mixed list scannable.
 */
@Composable
fun SfCard(
    modifier: Modifier = Modifier,
    variant: SfCardVariant = SfCardVariant.Elevated,
    onClick: (() -> Unit)? = null,
    accentColor: Color? = null,
    shape: RoundedCornerShape = SfTheme.shapes.card,
    contentPadding: Dp = SfTheme.density.cardPadding.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val highContrast = SfTheme.highContrast

    val container = when (variant) {
        SfCardVariant.Elevated -> scheme.surface
        SfCardVariant.Filled -> scheme.surfaceContainer
        SfCardVariant.Outlined -> scheme.surface
        SfCardVariant.Accent -> scheme.primaryContainer
        SfCardVariant.Warm -> scheme.secondaryContainer
        SfCardVariant.Glass -> scheme.surfaceContainerHigh
    }

    val contentColor = when (variant) {
        SfCardVariant.Accent -> scheme.onPrimaryContainer
        SfCardVariant.Warm -> scheme.onSecondaryContainer
        else -> scheme.onSurface
    }

    // In high contrast every card gets a visible edge, including the ones
    // that normally rely on fill alone. Losing the boundary between a card
    // and the page is exactly what the setting exists to prevent.
    val stroke = when {
        highContrast -> BorderStroke(2.dp, scheme.outline)
        variant == SfCardVariant.Outlined -> BorderStroke(1.dp, scheme.outlineVariant)
        variant == SfCardVariant.Elevated -> BorderStroke(1.dp, scheme.outlineVariant)
        else -> null
    }

    val elevation = if (variant == SfCardVariant.Elevated && !highContrast) 1.dp else 0.dp

    // A card that does nothing when tapped must not look tappable, and must
    // not be reachable by keyboard or screen reader as a control - so the
    // clickable modifier is added only when there is something to click.
    // The default indication is the theme's ripple; naming one explicitly
    // would pull in the material (not material3) ripple artifact.
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = container,
        contentColor = contentColor,
        tonalElevation = elevation,
        shadowElevation = elevation,
        border = stroke,
    ) {
        Box(modifier = clickModifier) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (accentColor != null) {
                    // The accent is a border, not a padding inset, so the
                    // content still starts at the card's normal padding and
                    // cards with and without an accent align in a list.
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(accentColor)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding),
                    content = content,
                )
            }
        }
    }
}

/**
 * Clips content to the card shape, for images and charts that bleed to the
 * card edge.
 *
 * Read as a value rather than written as a `Modifier.x()` extension so it
 * can be used inside an ordinary modifier chain: extension functions on
 * Modifier that are themselves @Composable cannot be.
 */
val cardClip: Modifier
    @Composable get() = Modifier.clip(SfTheme.shapes.card)
