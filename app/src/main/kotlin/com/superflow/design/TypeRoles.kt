package com.superflow.design

/**
 * The type scale, as data.
 *
 * `values/type.xml` expresses the same scale for the View layer; the Compose
 * typography reads it from here. As with colour, both are kept honest by a
 * test rather than by discipline - [TypeRolesTest] parses type.xml and
 * asserts every step matches.
 *
 * Line height is stored as a multiplier rather than an absolute sp value
 * because that is what the XML uses, and for a good reason: a multiplier
 * scales with the user's font-size preference, so a user at 1.3x gets
 * proportionally more leading rather than cramped text in a fixed box.
 * [Step.lineHeightSp] converts back for Compose, which wants absolutes.
 */
object TypeRoles {

    /** Which typeface a step uses. */
    enum class Family { Sans, Serif, Mono }

    /**
     * One step of the scale.
     *
     * @param sizeSp        font size at 1.0x scale
     * @param lineMultiplier line height as a multiple of the font size
     * @param letterSpacingEm tracking, in em; negative tightens
     * @param weight        400 regular, 500 medium, 600 semibold, 700 bold
     * @param italic        Source Serif italic, used only for identity text
     */
    data class Step(
        val name: String,
        val sizeSp: Int,
        val lineMultiplier: Float,
        val letterSpacingEm: Float,
        val weight: Int,
        val family: Family = Family.Sans,
        val italic: Boolean = false,
    ) {
        /** Line height in sp at 1.0x font scale, rounded to the nearest sp. */
        val lineHeightSp: Int get() = Math.round(sizeSp * lineMultiplier)
    }

    // Names match the XML style names so the test can pair them up, and so
    // that grepping for a style finds both definitions.
    val display = Step("Display", 40, 1.2f, -0.02f, 700)
    val headlineLarge = Step("DisplaySmall", 32, 1.25f, -0.015f, 700)
    val headlineMedium = Step("HeadlineMedium", 24, 1.33f, -0.01f, 700)
    val headlineSmall = Step("HeadlineSmall", 20, 1.4f, -0.005f, 600)
    val titleMedium = Step("TitleMedium", 16, 1.5f, 0f, 600)
    val bodyLarge = Step("BodyLarge", 16, 1.5f, 0f, 400)
    val bodyMedium = Step("BodyMedium", 14, 1.43f, 0f, 400)
    val labelLarge = Step("LabelLarge", 14, 1.43f, 0.005f, 500)
    val labelMedium = Step("LabelMedium", 12, 1.33f, 0.01f, 500)

    /** Tracked and uppercased at render time; the tracking is what makes it work. */
    val overline = Step("Overline", 11, 1.45f, 0.09f, 600)

    /** Tabular figures, so animating counts do not jitter. */
    val data = Step("Data", 13, 1.23f, -0.01f, 500, Family.Mono)
    val dataLarge = Step("DataLarge", 28, 1.15f, -0.02f, 500, Family.Mono)

    /** Serif italic: the signal that this is reflection, not data (§5.4). */
    val identity = Step("Identity", 20, 1.5f, 0f, 400, Family.Serif, italic = true)
    val journal = Step("Journal", 17, 1.6f, 0f, 400, Family.Serif)

    val all = listOf(
        display, headlineLarge, headlineMedium, headlineSmall, titleMedium,
        bodyLarge, bodyMedium, labelLarge, labelMedium, overline, data,
        dataLarge, identity, journal,
    )

    /**
     * Whether the scale is monotonic where it should be.
     *
     * A type scale whose steps cross over is confusing to use - "headline"
     * should never render smaller than "title". Body and label sizes may tie
     * with each other by design (both 14sp), so this checks the headline
     * spine only.
     */
    val headlineSpine = listOf(display, headlineLarge, headlineMedium, headlineSmall, titleMedium)

    fun spineDescends(): Boolean =
        headlineSpine.zipWithNext().all { (a, b) -> a.sizeSp > b.sizeSp }

    /**
     * Minimum readable size. Anything below 11sp is too small for body text
     * on a phone, and Overline at 11sp is the deliberate floor.
     */
    const val MIN_SIZE_SP = 11
}
