package com.superflow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.superflow.R
import com.superflow.design.TypeRoles

/**
 * The type scale as Compose styles.
 *
 * Every metric comes from [TypeRoles], which is pinned against
 * `values/type.xml`, so a heading is the same size whether it is drawn by a
 * TextView or a Composable. Nothing here invents a number.
 *
 * Three families, each with one job (§5.1):
 *
 *   Inter           all UI text; neutral grotesque with a tall x-height
 *   Source Serif 4  identity statements and journal entries only
 *   JetBrains Mono  numerals in stats and chart axes, for tabular figures
 *
 * The serif is doing semantic work rather than decorating: it marks the
 * places where the user is reflecting rather than reading data.
 */

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val SourceSerifFamily = FontFamily(
    Font(R.font.source_serif_regular, FontWeight.Normal),
    Font(R.font.source_serif_semibold, FontWeight.SemiBold),
    Font(R.font.source_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

private fun familyFor(family: TypeRoles.Family) = when (family) {
    TypeRoles.Family.Sans -> InterFamily
    TypeRoles.Family.Serif -> SourceSerifFamily
    TypeRoles.Family.Mono -> JetBrainsMonoFamily
}

/** Builds a Compose [TextStyle] from a [TypeRoles.Step]. */
private fun TypeRoles.Step.toTextStyle(): TextStyle = TextStyle(
    fontFamily = familyFor(family),
    fontWeight = FontWeight(weight),
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    fontSize = sizeSp.sp,
    // Compose wants an absolute line height. Using sp here keeps it scaling
    // with the user's font-size preference, which is the property the XML
    // gets from lineSpacingMultiplier.
    lineHeight = lineHeightSp.sp,
    letterSpacing = letterSpacingEm.em,
)

/**
 * The app's own type roles, for the steps Material has no slot for.
 *
 * Material's [Typography] has thirteen slots and the app's scale has
 * fourteen steps, several of which mean something Material has no concept of
 * - there is no "this is a journal entry" role in Material. Rather than
 * bending Display Large to mean "big number", the extra steps travel here.
 */
@Immutable
data class SfTypeStyles(
    val display: TextStyle,
    val overline: TextStyle,
    val data: TextStyle,
    val dataLarge: TextStyle,
    val identity: TextStyle,
    val journal: TextStyle,
)

/**
 * Typography variants (§5).
 *
 * Default follows the scale exactly. Large is not a separate design - it is
 * the same scale with every step bumped, for users who want bigger text
 * without changing the system font scale for every app on the device.
 */
enum class SfTypographyVariant(val label: String, val scale: Float) {
    Default("Default", 1.0f),
    Large("Large", 1.15f),
}

private fun TextStyle.scaled(factor: Float): TextStyle =
    if (factor == 1.0f) this else copy(
        fontSize = fontSize * factor,
        lineHeight = lineHeight * factor,
    )

internal fun materialTypography(variant: SfTypographyVariant): Typography {
    val f = variant.scale
    return Typography(
        // The scale has one Display step, mapped to Material's Large slot;
        // the Medium and Small slots take the headline steps so that a
        // component reaching for displayMedium gets something sensible
        // rather than a 40sp surprise.
        displayLarge = TypeRoles.display.toTextStyle().scaled(f),
        displayMedium = TypeRoles.headlineLarge.toTextStyle().scaled(f),
        displaySmall = TypeRoles.headlineMedium.toTextStyle().scaled(f),

        headlineLarge = TypeRoles.headlineLarge.toTextStyle().scaled(f),
        headlineMedium = TypeRoles.headlineMedium.toTextStyle().scaled(f),
        headlineSmall = TypeRoles.headlineSmall.toTextStyle().scaled(f),

        titleLarge = TypeRoles.headlineSmall.toTextStyle().scaled(f),
        titleMedium = TypeRoles.titleMedium.toTextStyle().scaled(f),
        titleSmall = TypeRoles.labelLarge.toTextStyle().scaled(f),

        bodyLarge = TypeRoles.bodyLarge.toTextStyle().scaled(f),
        bodyMedium = TypeRoles.bodyMedium.toTextStyle().scaled(f),
        bodySmall = TypeRoles.labelMedium.toTextStyle().scaled(f),

        labelLarge = TypeRoles.labelLarge.toTextStyle().scaled(f),
        labelMedium = TypeRoles.labelMedium.toTextStyle().scaled(f),
        labelSmall = TypeRoles.overline.toTextStyle().scaled(f),
    )
}

/**
 * @param serifAccents when false, identity and journal text uses the sans
 *                     face. The metrics are unchanged, so turning the serif
 *                     off never reflows a screen - it only changes the
 *                     voice. Some readers find serif harder at small sizes,
 *                     and that is a legibility need, not a taste.
 * @param monoFigures  when false, numerals use the sans face. Mono keeps
 *                     animating counts from jittering, but its digits are
 *                     wider, and users who prefer a compact layout can trade
 *                     that away.
 */
internal fun sfTypeStyles(
    variant: SfTypographyVariant,
    serifAccents: Boolean = true,
    monoFigures: Boolean = true,
): SfTypeStyles {
    val f = variant.scale

    fun TypeRoles.Step.style(): TextStyle {
        val base = toTextStyle().scaled(f)
        return when {
            !serifAccents && family == TypeRoles.Family.Serif ->
                // Keep the italic: it is what marks the text as reflective,
                // and it survives the family change.
                base.copy(fontFamily = InterFamily)
            !monoFigures && family == TypeRoles.Family.Mono ->
                base.copy(fontFamily = InterFamily)
            else -> base
        }
    }

    return SfTypeStyles(
        display = TypeRoles.display.style(),
        overline = TypeRoles.overline.style(),
        data = TypeRoles.data.style(),
        dataLarge = TypeRoles.dataLarge.style(),
        identity = TypeRoles.identity.style(),
        journal = TypeRoles.journal.style(),
    )
}

/**
 * Centres a numeric style's figures.
 *
 * Convenience for stat cards, where a mono figure inside a fixed-width box
 * otherwise sits slightly left because of the trailing letter-spacing.
 */
fun TextStyle.centredFigures(): TextStyle = copy(textAlign = TextAlign.Center)
