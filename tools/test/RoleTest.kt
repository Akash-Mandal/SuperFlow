import com.superflow.design.ColorRoles
import com.superflow.design.Contrast
import com.superflow.design.Ramps
import com.superflow.design.SurfaceRoles
import com.superflow.design.TypeRoles
import com.superflow.design.ThemeSelection
import java.io.File

/**
 * Cross-checks the Kotlin colour model against the XML theme resources.
 *
 * The View layer reads colours from XML overlays; the Compose layer reads
 * them from [ColorRoles]. Nothing in the build makes those agree, and a
 * divergence is close to invisible in review - both files look reasonable on
 * their own, and you only notice when the same palette renders differently
 * depending on which layer drew the screen.
 *
 * So this suite parses the actual resource XML and asserts the Kotlin model
 * reproduces it, role by role, palette by palette, in both light and dark.
 */

var pass = 0
var fail = 0

fun check(name: String, cond: Boolean) {
    if (cond) {
        pass++
    } else {
        fail++
        println("  FAIL: $name")
    }
}

fun <T> eq(name: String, actual: T, expected: T) {
    if (actual == expected) {
        pass++
    } else {
        fail++
        println("  FAIL: $name")
        println("        expected: $expected")
        println("        actual:   $actual")
    }
}

private val RES = File("app/src/main/res")

/** `sf_green_50` -> the ARGB int, read from whichever colours file defines it. */
private fun colorTable(night: Boolean): Map<String, Int> {
    val dirs = if (night) listOf("values", "values-night") else listOf("values")
    val out = LinkedHashMap<String, Int>()
    for (dir in dirs) {
        val d = File(RES, dir)
        if (!d.isDirectory) continue
        for (file in d.listFiles()!!.sortedBy { it.name }) {
            if (!file.name.startsWith("colors") || !file.name.endsWith(".xml")) continue
            val text = file.readText()
            val re = Regex("""name="([a-z0-9_]+)"\s*>\s*(#[0-9A-Fa-f]{6,8})\s*<""")
            for (m in re.findAll(text)) {
                var hex = m.groupValues[2].removePrefix("#")
                if (hex.length == 6) hex = "FF$hex"
                // values-night wins, matching resource resolution.
                out[m.groupValues[1]] = hex.toLong(16).toInt()
            }
        }
    }
    return out
}

/** Every `<item name=...>@color/x</item>` in one named style. */
private fun styleItems(file: File, styleName: String): Map<String, String> {
    if (!file.isFile) return emptyMap()
    val text = file.readText()
    val start = text.indexOf("""name="$styleName"""")
    if (start < 0) return emptyMap()
    val end = text.indexOf("</style>", start)
    val body = text.substring(start, if (end < 0) text.length else end)
    val re = Regex("""<item name="([A-Za-z:.]+)"\s*>\s*@color/([a-z0-9_]+)\s*<""")
    return re.findAll(body).associate { it.groupValues[1] to it.groupValues[2] }
}

private val paletteStyle = mapOf(
    ThemeSelection.PALETTE_FOREST_ID to "ThemeOverlay.SuperFlow.Palette.Forest",
    ThemeSelection.PALETTE_OCEAN_ID to "ThemeOverlay.SuperFlow.Palette.Ocean",
    ThemeSelection.PALETTE_DUSK_ID to "ThemeOverlay.SuperFlow.Palette.Dusk",
    ThemeSelection.PALETTE_MONO_ID to "ThemeOverlay.SuperFlow.Palette.Mono",
)

private val paletteName = mapOf(
    ThemeSelection.PALETTE_CALM_ID to "Calm",
    ThemeSelection.PALETTE_FOREST_ID to "Forest",
    ThemeSelection.PALETTE_OCEAN_ID to "Ocean",
    ThemeSelection.PALETTE_DUSK_ID to "Dusk",
    ThemeSelection.PALETTE_MONO_ID to "Mono",
)

fun main() {
    println("== RoleTest ==")
    println()

    // ---------------------------------------------------------------- ramps

    println("Ramps mirror the XML")
    val lightColors = colorTable(night = false)
    for ((rampName, steps) in Ramps.all) {
        for ((tone, value) in steps) {
            val key = "${rampName}_$tone"
            val fromXml = lightColors[key]
            check("$key defined in XML", fromXml != null)
            if (fromXml != null) {
                eq("$key matches XML", String.format("%08X", value), String.format("%08X", fromXml))
            }
        }
    }

    // Regenerating must be idempotent: every ramp the XML defines under a
    // palette prefix should be present in Ramps, or the Compose layer is
    // quietly missing a colour the View layer has.
    val xmlRamps = lightColors.keys
        .mapNotNull { Regex("""^(sf_[a-z_]+?)_\d+$""").find(it)?.groupValues?.get(1) }
        .toSet()
    val paletteRamps = xmlRamps.filter {
        it.startsWith("sf_forest") || it.startsWith("sf_ocean") ||
            it.startsWith("sf_dusk") || it.startsWith("sf_mono")
    }
    for (ramp in paletteRamps) {
        check("Ramps includes $ramp", Ramps.all.containsKey(ramp))
    }
    println()

    // ------------------------------------------------- roles match the XML

    for (isDark in listOf(false, true)) {
        val mode = if (isDark) "dark" else "light"
        val colors = colorTable(night = isDark)
        val file = File(RES, if (isDark) "values-night/themes_palette.xml" else "values/themes_palette.xml")
        println("Palette overlays reproduce the XML ($mode)")

        for ((paletteId, styleName) in paletteStyle) {
            val items = styleItems(file, styleName)
            check("$styleName found in $mode XML", items.isNotEmpty())
            if (items.isEmpty()) continue

            val scheme = ColorRoles.schemeFor(paletteId, isDark)
            val name = paletteName[paletteId]

            // Only assert on roles the XML actually restates. A role the
            // overlay omits is inherited from the base theme, and the Kotlin
            // model synthesises its own value for it - that is a deliberate
            // difference, not a drift, because a Compose ColorScheme has no
            // inheritance to fall back on.
            fun compare(attr: String, actual: Int) {
                val ref = items[attr] ?: return
                val expected = colors[ref]
                check("$name $mode $attr resolves $ref", expected != null)
                if (expected != null) {
                    eq(
                        "$name $mode $attr",
                        String.format("%08X", actual),
                        String.format("%08X", expected)
                    )
                }
            }

            compare("colorPrimary", scheme.primary)
            compare("colorOnPrimary", scheme.onPrimary)
            compare("colorPrimaryContainer", scheme.primaryContainer)
            compare("colorOnPrimaryContainer", scheme.onPrimaryContainer)
            compare("colorPrimaryInverse", scheme.primaryInverse)
            compare("colorSecondary", scheme.secondary)
            compare("colorOnSecondary", scheme.onSecondary)
            compare("colorSecondaryContainer", scheme.secondaryContainer)
            compare("colorOnSecondaryContainer", scheme.onSecondaryContainer)
            compare("colorTertiary", scheme.tertiary)
            compare("colorTertiaryContainer", scheme.tertiaryContainer)
            compare("colorOnTertiaryContainer", scheme.onTertiaryContainer)
            compare("sfLevelTiny", scheme.levels[0])
            compare("sfLevelMinimum", scheme.levels[1])
            compare("sfLevelStandard", scheme.levels[2])
            compare("sfLevelStretch", scheme.levels[3])
            compare("sfSuccess", scheme.success)
            compare("sfSuccessContainer", scheme.successContainer)
        }
        println()
    }

    // ------------------------------------------------------- scheme sanity

    println("Every palette produces a usable scheme")
    for (paletteId in ColorRoles.paletteIds) {
        val name = paletteName[paletteId]
        for (isDark in listOf(false, true)) {
            val mode = if (isDark) "dark" else "light"
            val s = ColorRoles.schemeFor(paletteId, isDark)

            // Text on an accent is the most common contrast failure in a
            // themed app, and it is the one users notice immediately.
            check(
                "$name $mode onPrimary legible on primary",
                Contrast.ratio(s.onPrimary, s.primary) >= 4.5
            )
            check(
                "$name $mode onSecondary legible on secondary",
                Contrast.ratio(s.onSecondary, s.secondary) >= 4.5
            )
            check(
                "$name $mode onPrimaryContainer legible on primaryContainer",
                Contrast.ratio(s.onPrimaryContainer, s.primaryContainer) >= 4.5
            )
            check(
                "$name $mode onSecondaryContainer legible on secondaryContainer",
                Contrast.ratio(s.onSecondaryContainer, s.secondaryContainer) >= 4.5
            )
            check(
                "$name $mode onTertiaryContainer legible on tertiaryContainer",
                Contrast.ratio(s.onTertiaryContainer, s.tertiaryContainer) >= 4.5
            )

            // The ladder has to read as four distinct steps.
            eq("$name $mode has four levels", s.levels.size, 4)
            check("$name $mode levels are distinct", ColorRoles.levelsAreDistinct(s))

            // Every role opaque: a stray alpha would composite against
            // whatever is behind it and silently lower contrast.
            val roles = listOf(
                s.primary, s.onPrimary, s.primaryContainer, s.onPrimaryContainer,
                s.primaryInverse, s.secondary, s.onSecondary, s.secondaryContainer,
                s.onSecondaryContainer, s.tertiary, s.onTertiary, s.tertiaryContainer,
                s.onTertiaryContainer, s.success, s.successContainer
            ) + s.levels
            check(
                "$name $mode all roles opaque",
                roles.all { (it ushr 24) and 0xFF == 0xFF }
            )
        }
    }
    println()

    println("Unknown palette falls back to Calm")
    for (isDark in listOf(false, true)) {
        eq(
            "unknown palette == Calm (dark=$isDark)",
            ColorRoles.schemeFor(99, isDark),
            ColorRoles.schemeFor(ThemeSelection.PALETTE_CALM_ID, isDark)
        )
        eq(
            "negative palette == Calm (dark=$isDark)",
            ColorRoles.schemeFor(-1, isDark),
            ColorRoles.schemeFor(ThemeSelection.PALETTE_CALM_ID, isDark)
        )
    }
    println()

    println("Light and dark are genuinely different")
    for (paletteId in ColorRoles.paletteIds) {
        val light = ColorRoles.schemeFor(paletteId, false)
        val dark = ColorRoles.schemeFor(paletteId, true)
        check("${paletteName[paletteId]} light != dark", light != dark)
        // The ladder must reverse, or dark mode would put the faintest tone
        // on the most demanding rung.
        check(
            "${paletteName[paletteId]} ladder reverses in dark",
            light.levels.first() != dark.levels.first()
        )
    }

    // ------------------------------------------------- surfaces match XML

    println("Surfaces reproduce the base theme XML")
    for (isDark in listOf(false, true)) {
        val mode = if (isDark) "dark" else "light"
        val colors = colorTable(night = isDark)
        val themeFile = File(RES, if (isDark) "values-night/themes.xml" else "values/themes.xml")
        val base = styleItems(themeFile, "Theme.SuperFlow")
        check("Theme.SuperFlow found in $mode XML", base.isNotEmpty())

        // Warm is the flavour baked into the night base theme, so it is the
        // one that must match values-night exactly.
        val s = SurfaceRoles.surfacesFor(isDark, ThemeSelection.DARK_WARM_ID)

        fun compare(attr: String, actual: Int) {
            val ref = base[attr] ?: return
            val expected = colors[ref] ?: return
            eq("$mode $attr", String.format("%08X", actual), String.format("%08X", expected))
        }

        compare("android:colorBackground", s.background)
        compare("colorOnBackground", s.onBackground)
        compare("colorSurface", s.surface)
        compare("colorOnSurface", s.onSurface)
        compare("colorSurfaceVariant", s.surfaceVariant)
        compare("colorOnSurfaceVariant", s.onSurfaceVariant)
        compare("colorOutline", s.outline)
        compare("colorOutlineVariant", s.outlineVariant)
        compare("colorSurfaceInverse", s.inverseSurface)
        compare("colorOnSurfaceInverse", s.inverseOnSurface)
        compare("colorError", s.error)
        compare("colorOnError", s.onError)
        compare("colorErrorContainer", s.errorContainer)
        compare("colorOnErrorContainer", s.onErrorContainer)
    }
    println()

    println("Inline theme literals still match")
    // These four are written as hex in the theme XML with no @color name, so
    // they are duplicated in Kotlin. Pin them, or a future XML edit silently
    // desyncs the Compose scheme.
    val lightTheme = File(RES, "values/themes.xml").readText()
    val nightTheme = File(RES, "values-night/themes.xml").readText()
    check(
        "light onErrorContainer literal",
        lightTheme.contains(
            String.format("#%08X", SurfaceRoles.ON_ERROR_CONTAINER_LIGHT), ignoreCase = true
        )
    )
    check(
        "dark onError literal",
        nightTheme.contains(String.format("#%08X", SurfaceRoles.ON_ERROR_DARK), ignoreCase = true)
    )
    println()

    println("Dark flavours differ only in their surfaces")
    val warm = SurfaceRoles.surfacesFor(true, ThemeSelection.DARK_WARM_ID)
    val oled = SurfaceRoles.surfacesFor(true, ThemeSelection.DARK_OLED_ID)
    val midnight = SurfaceRoles.surfacesFor(true, ThemeSelection.DARK_MIDNIGHT_ID)
    check("OLED background is true black", oled.background == 0xFF000000.toInt())
    check("warm != oled", warm.background != oled.background)
    check("warm != midnight", warm.background != midnight.background)
    check("oled != midnight", oled.background != midnight.background)
    for ((name, s) in listOf("warm" to warm, "oled" to oled, "midnight" to midnight)) {
        // Text colours are shared across flavours; only surfaces move.
        eq("$name shares onSurface", s.onSurface, warm.onSurface)
        eq("$name shares error", s.error, warm.error)
        check("$name text is legible", SurfaceRoles.textPairsPass(s))
    }
    // An unknown flavour must behave like the default rather than crash.
    eq("unknown dark flavour falls back to warm", SurfaceRoles.surfacesFor(true, 99), warm)
    println()

    println("Light surfaces are legible")
    check("light text pairs pass", SurfaceRoles.textPairsPass(SurfaceRoles.surfacesFor(false)))
    println()

    // ------------------------------------------------------- type scale

    println("Type scale reproduces type.xml")
    val typeXml = File(RES, "values/type.xml").readText()
    for (step in TypeRoles.all) {
        val re = Regex(
            """<style name="Text\.SuperFlow\.${step.name}"[^>]*>(.*?)</style>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val body = re.find(typeXml)?.groupValues?.get(1)
        check("${step.name} exists in type.xml", body != null)
        if (body == null) continue

        fun attr(key: String): String? =
            Regex("""name="(?:android:)?$key"\s*>([^<]+)<""").find(body)?.groupValues?.get(1)

        eq("${step.name} size", attr("textSize"), "${step.sizeSp}sp")

        // Line multiplier: the XML omits it only when it equals the default.
        val mult = attr("lineSpacingMultiplier")
        if (mult != null) {
            eq("${step.name} line multiplier", mult.toFloat(), step.lineMultiplier)
        }

        // Letter spacing is omitted when zero.
        val tracking = attr("letterSpacing")?.toFloat() ?: 0f
        eq("${step.name} letter spacing", tracking, step.letterSpacingEm)

        // DataLarge inherits its weight and family from Data.
        val weight = attr("textFontWeight")?.toInt()
        if (weight != null) eq("${step.name} weight", weight, step.weight)

        val family = attr("fontFamily")?.removePrefix("@font/")
        if (family != null) {
            val expected = when (step.family) {
                TypeRoles.Family.Sans -> "inter"
                TypeRoles.Family.Serif -> "source_serif"
                TypeRoles.Family.Mono -> "jetbrains_mono"
            }
            eq("${step.name} family", family, expected)
        }
    }
    println()

    println("Type scale is coherent")
    check("headline spine descends", TypeRoles.spineDescends())
    for (step in TypeRoles.all) {
        check(
            "${step.name} is at least ${TypeRoles.MIN_SIZE_SP}sp",
            step.sizeSp >= TypeRoles.MIN_SIZE_SP
        )
        // Leading below the font size would overlap lines; above 2x is a
        // layout mistake rather than a style.
        check("${step.name} leading is sane", step.lineMultiplier in 1.0f..2.0f)
        check("${step.name} weight is on the ramp", step.weight in listOf(400, 500, 600, 700))
        // Tracking beyond +-0.1em stops being tracking and starts being a gap.
        check("${step.name} tracking is sane", kotlin.math.abs(step.letterSpacingEm) <= 0.1f)
        check("${step.name} line height rounds sanely", step.lineHeightSp >= step.sizeSp)
    }
    eq("only Identity is italic", TypeRoles.all.count { it.italic }, 1)
    check("Identity is the italic one", TypeRoles.identity.italic)
    eq("mono steps", TypeRoles.all.count { it.family == TypeRoles.Family.Mono }, 2)
    eq("serif steps", TypeRoles.all.count { it.family == TypeRoles.Family.Serif }, 2)
    // Distinct names, or the Compose typography map would silently lose one.
    eq("step names are unique", TypeRoles.all.map { it.name }.toSet().size, TypeRoles.all.size)
    println()

    println("passed=$pass failed=$fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
