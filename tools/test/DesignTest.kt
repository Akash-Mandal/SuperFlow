import com.superflow.data.Prefs
import com.superflow.design.*
import kotlin.math.abs

var pass = 0
var fail = 0
fun check(name: String, cond: Boolean) {
    if (cond) { pass++; println("  ok   $name") } else { fail++; println("  FAIL $name") }
}
fun eq(name: String, a: Any?, b: Any?) = check("$name  ($a == $b)", a == b)

fun main() {
    println("Design token invariants")

    // ------------------------------------------------------------ spacing
    println("Spacing scale")
    eq("nine steps", Space.scale.size, 9)
    check("strictly ascending", Space.scale.toList().zipWithNext().all { it.first < it.second })
    check("all positive", Space.scale.all { it > 0 })
    // The XML dimens and these constants are two copies of one scale; if the
    // values below change, res/values/dimens.xml must change with them.
    eq("2xs", Space.XXS, 2); eq("xs", Space.XS, 4); eq("sm", Space.SM, 8)
    eq("md", Space.MD, 12); eq("base", Space.BASE, 16); eq("lg", Space.LG, 24)
    eq("xl", Space.XL, 32); eq("2xl", Space.XXL, 48); eq("3xl", Space.XXXL, 64)

    println("Spacing snap")
    eq("snap exact", Space.snap(16), 16)
    eq("snap below floor", Space.snap(0), 2)
    eq("snap negative", Space.snap(-40), 2)
    eq("snap above ceiling", Space.snap(999), 64)
    eq("snap 15 -> 16", Space.snap(15), 16)
    eq("snap 11 -> 12", Space.snap(11), 12)
    eq("snap 5 -> 4", Space.snap(5), 4)
    // Ties round down, toward the tighter spacing: 14 sits exactly between
    // 12 and 16, and 10 exactly between 8 and 12.
    eq("tie 14 rounds down to 12", Space.snap(14), 12)
    eq("tie 10 rounds down to 8", Space.snap(10), 8)
    eq("tie 3 rounds down to 2", Space.snap(3), 2)
    check("snap always lands on the scale",
        (0..80).all { Space.snap(it) in Space.scale })

    // ------------------------------------------------------------- radius
    println("Radius scale")
    check("ascending", listOf(Radius.NONE, Radius.XXS, Radius.XS, Radius.SM,
        Radius.MD, Radius.LG, Radius.XL).zipWithNext().all { it.first < it.second })
    eq("card radius matches shapes.xml", Radius.MD, 18)
    check("FULL is a sentinel, not a size", Radius.FULL < 0)

    // --------------------------------------------------------------- type
    println("Type scale")
    val steps = listOf(TypeScale.OVERLINE, TypeScale.LABEL_M, TypeScale.BODY_M,
        TypeScale.BODY_L, TypeScale.TITLE_L, TypeScale.HEADLINE_M,
        TypeScale.HEADLINE_L, TypeScale.DISPLAY)
    check("type steps ascend", steps.zipWithNext().all { it.first < it.second })
    check("nothing below 11sp", steps.all { it >= 11 })
    eq("display", TypeScale.DISPLAY, 40)
    eq("body large", TypeScale.BODY_L, 16)

    // ------------------------------------------------------------- motion
    println("Motion scaling")
    eq("standard is 1x", Motion.scaleFor(Motion.STANDARD), 1f)
    eq("reduced is half", Motion.scaleFor(Motion.REDUCED), 0.5f)
    eq("none is zero", Motion.scaleFor(Motion.NONE), 0f)
    eq("expressive is 1.25x", Motion.scaleFor(Motion.EXPRESSIVE), 1.25f)

    eq("normal duration at standard", Motion.duration(Motion.NORMAL, Motion.STANDARD), 250)
    eq("normal duration reduced", Motion.duration(Motion.NORMAL, Motion.REDUCED), 125)
    eq("normal duration expressive", Motion.duration(Motion.NORMAL, Motion.EXPRESSIVE), 312)
    eq("duration is 0 when motion off", Motion.duration(Motion.NORMAL, Motion.NONE), 0)

    // A zero-length animator still posts a frame, so 0 must mean "skip" and
    // must only ever be produced by the disabled path -- never by rounding.
    eq("tiny duration never rounds into the skip sentinel",
        Motion.duration(1, Motion.REDUCED), 1)
    check("only the disabled path yields 0", (1..600).all {
        Motion.duration(it, Motion.REDUCED) > 0 })

    println("System animation setting")
    check("system off disables motion", Motion.isDisabled(Motion.STANDARD, true))
    check("pref off disables motion", Motion.isDisabled(Motion.NONE, false))
    check("both on is enabled", !Motion.isDisabled(Motion.STANDARD, false))
    eq("system off zeroes duration",
        Motion.duration(Motion.NORMAL, Motion.EXPRESSIVE, true), 0)

    println("Stagger")
    eq("first item has no delay", Motion.staggerDelay(0, Motion.STANDARD), 0)
    eq("second item", Motion.staggerDelay(1, Motion.STANDARD), 40)
    eq("fourth item", Motion.staggerDelay(3, Motion.STANDARD), 120)
    eq("stagger caps", Motion.staggerDelay(8, Motion.STANDARD),
        Motion.staggerDelay(50, Motion.STANDARD))
    check("capped stagger stays under half a second",
        Motion.staggerDelay(9999, Motion.EXPRESSIVE) < 500)
    eq("no stagger when motion off", Motion.staggerDelay(5, Motion.NONE), 0)
    check("stagger is monotonic up to the cap",
        (0..8).map { Motion.staggerDelay(it, Motion.STANDARD) }
            .zipWithNext().all { it.first <= it.second })

    // The plan's 800ms orchestration budget (20, Motion Quality), asserted
    // rather than aspired to.
    eq("budget is the plan's number", Motion.ORCHESTRATION_BUDGET, 800)
    eq("nothing to wait for when motion is off",
        Motion.orchestrationMs(Motion.NORMAL, Motion.NONE), 0)
    check("a normal entrance fits at every level",
        listOf(Motion.REDUCED, Motion.STANDARD, Motion.EXPRESSIVE)
            .all { Motion.fitsBudget(Motion.NORMAL, it) })
    check("a slow entrance does not fit at expressive",
        !Motion.fitsBudget(Motion.SLOW, Motion.EXPRESSIVE))
    check("the entrance ceiling is honest",
        Motion.fitsBudget(Motion.ENTRANCE_MAX, Motion.EXPRESSIVE) &&
            !Motion.fitsBudget(Motion.ENTRANCE_MAX + 40, Motion.EXPRESSIVE))
    check("normal sits under the ceiling", Motion.NORMAL <= Motion.ENTRANCE_MAX)
    // The whole point of capping the stagger: a forty-row list costs the
    // same as an eight-row one.
    eq("list length does not change the total",
        Motion.orchestrationMs(Motion.NORMAL, Motion.STANDARD),
        Motion.staggerDelay(400, Motion.STANDARD) +
            Motion.duration(Motion.NORMAL, Motion.STANDARD))
    check("reduced motion is strictly faster than standard",
        Motion.orchestrationMs(Motion.NORMAL, Motion.REDUCED) <
            Motion.orchestrationMs(Motion.NORMAL, Motion.STANDARD))

    // ------------------------------------------------------------ haptics
    println("Haptic vocabulary")
    check("at least eight patterns", Haptics.all.size >= 8)
    check("names are unique", Haptics.all.map { it.name }.toSet().size == Haptics.all.size)
    check("no empty patterns", Haptics.all.all { it.steps.isNotEmpty() })
    check("amplitudes are in range",
        Haptics.all.all { p -> p.steps.all { it.second in 0f..1f } })
    check("durations are positive",
        Haptics.all.all { p -> p.steps.all { it.first > 0 } })
    // Haptics that outstay their welcome read as a malfunction.
    check("no pattern exceeds 250ms", Haptics.all.all { it.durationMs <= 250 })
    check("every pattern actually vibrates",
        Haptics.all.all { p -> p.steps.any { it.second > 0f } })

    println("Haptic scaling")
    eq("complete has three steps", Haptics.COMPLETE.steps.size, 3)
    check("complete crescendos",
        Haptics.COMPLETE.steps[2].second > Haptics.COMPLETE.steps[0].second)
    check("undo decrescendos",
        Haptics.UNDO.steps.last().second < Haptics.UNDO.steps.first().second)
    check("milestone ascends", Haptics.MILESTONE.steps
        .filter { it.second > 0f }.map { it.second }
        .zipWithNext().all { it.first < it.second })

    val soft = Haptics.COMPLETE.scaled(0.6f)
    check("scaling returns a pattern", soft != null)
    check("scaling lowers amplitude",
        soft!!.steps[0].second < Haptics.COMPLETE.steps[0].second)
    check("scaling preserves timing",
        soft.steps.map { it.first } == Haptics.COMPLETE.steps.map { it.first })
    check("scaling clamps to 1.0",
        Haptics.MILESTONE.scaled(10f)!!.steps.all { it.second <= 1f })
    check("zero intensity yields null, not a silent buzz",
        Haptics.COMPLETE.scaled(0f) == null)
    check("negative intensity yields null", Haptics.COMPLETE.scaled(-1f) == null)

    // ------------------------------------------------------------ density
    println("Density metrics")
    val c = Density.metrics(Density.COMPACT)
    val m = Density.metrics(Density.COMFORTABLE)
    val s = Density.metrics(Density.SPACIOUS)
    check("padding ascends",
        c.cardPadding < m.cardPadding && m.cardPadding < s.cardPadding)
    check("row height ascends",
        c.listItemHeight < m.listItemHeight && m.listItemHeight < s.listItemHeight)
    check("line spacing ascends",
        c.lineSpacing < m.lineSpacing && m.lineSpacing < s.lineSpacing)
    // Motor accessibility: no density may drop a row below the 48dp target.
    check("no density breaches the 48dp touch target",
        listOf(c, m, s).all { it.listItemHeight >= 48 })
    eq("unknown density falls back to comfortable", Density.metrics(99), m)
    eq("negative density falls back to comfortable", Density.metrics(-3), m)
    check("compact metrics match themes_density.xml",
        c.cardPadding == 12 && c.listItemHeight == 48 && c.lineSpacing == 1.15f)
    check("spacious metrics match themes_density.xml",
        s.cardPadding == 24 && s.listItemHeight == 64 && s.lineSpacing == 1.40f)

    // ------------------------------------------------------------- levels
    println("Habit ladder")
    eq("four levels", Levels.ordered.size, 4)
    eq("tiny is first", Levels.ordinalOf("tiny"), 0)
    eq("stretch is last", Levels.ordinalOf("stretch"), 3)
    eq("case insensitive", Levels.ordinalOf("STANDARD"), 2)
    eq("whitespace tolerated", Levels.ordinalOf("  minimum  "), 1)
    eq("unknown is -1", Levels.ordinalOf("nonsense"), -1)
    eq("null is -1", Levels.ordinalOf(null), -1)
    check("weights ascend", Levels.ordered.map { Levels.weight(it) }
        .zipWithNext().all { it.first < it.second })
    // Showing up at all is the point of the ladder: tiny must still count.
    check("tiny still counts", Levels.weight("tiny") > 0f)
    eq("standard is the reference", Levels.weight("standard"), 1f)
    check("stretch exceeds standard", Levels.weight("stretch") > 1f)
    eq("unknown weighs nothing", Levels.weight("bogus"), 0f)

    // ---------------------------------------------------------- theming
    // The design package mirrors the Prefs constants so it can stay free of
    // the data layer. These assertions are the only thing stopping the two
    // copies from drifting apart, which would silently apply the wrong
    // palette rather than fail loudly.
    println("Theme constants track Prefs")
    eq("calm", ThemeSelection.PALETTE_CALM_ID, Prefs.PALETTE_CALM)
    eq("forest", ThemeSelection.PALETTE_FOREST_ID, Prefs.PALETTE_FOREST)
    eq("ocean", ThemeSelection.PALETTE_OCEAN_ID, Prefs.PALETTE_OCEAN)
    eq("dusk", ThemeSelection.PALETTE_DUSK_ID, Prefs.PALETTE_DUSK)
    eq("mono", ThemeSelection.PALETTE_MONO_ID, Prefs.PALETTE_MONO)
    eq("warm dark", ThemeSelection.DARK_WARM_ID, Prefs.DARK_WARM)
    eq("oled", ThemeSelection.DARK_OLED_ID, Prefs.DARK_OLED)
    eq("midnight", ThemeSelection.DARK_MIDNIGHT_ID, Prefs.DARK_MIDNIGHT)
    eq("compact", ThemeSelection.DENSITY_COMPACT_ID, Prefs.DENSITY_COMPACT)
    eq("comfortable", ThemeSelection.DENSITY_COMFORTABLE_ID, Prefs.DENSITY_COMFORTABLE)
    eq("spacious", ThemeSelection.DENSITY_SPACIOUS_ID, Prefs.DENSITY_SPACIOUS)

    println("Overlay selection")
    // All defaults: the base theme already is this, so nothing to overlay.
    eq("defaults need no overlay", ThemeSelection.overlaysFor(
        Prefs.PALETTE_CALM, Prefs.DARK_WARM, Prefs.DENSITY_COMFORTABLE,
        isDark = false, highContrast = false).size, 0)
    eq("warm dark needs no overlay", ThemeSelection.overlaysFor(
        Prefs.PALETTE_CALM, Prefs.DARK_WARM, Prefs.DENSITY_COMFORTABLE,
        isDark = true, highContrast = false).size, 0)
    eq("palette only", ThemeSelection.overlaysFor(
        Prefs.PALETTE_OCEAN, Prefs.DARK_WARM, Prefs.DENSITY_COMFORTABLE,
        isDark = false, highContrast = false), listOf(ThemeSelection.PALETTE_OCEAN))
    // A dark flavour is meaningless in light mode and must not leak into it.
    eq("dark flavour ignored in light mode", ThemeSelection.overlaysFor(
        Prefs.PALETTE_CALM, Prefs.DARK_OLED, Prefs.DENSITY_COMFORTABLE,
        isDark = false, highContrast = false).size, 0)
    check("dark flavour applies in dark mode", ThemeSelection.overlaysFor(
        Prefs.PALETTE_CALM, Prefs.DARK_OLED, Prefs.DENSITY_COMFORTABLE,
        isDark = true, highContrast = false).contains(ThemeSelection.DARK_OLED))
    eq("everything at once", ThemeSelection.overlaysFor(
        Prefs.PALETTE_DUSK, Prefs.DARK_MIDNIGHT, Prefs.DENSITY_COMPACT,
        isDark = true, highContrast = true),
        listOf(ThemeSelection.PALETTE_DUSK, ThemeSelection.DARK_MIDNIGHT,
            ThemeSelection.DENSITY_COMPACT, ThemeSelection.HIGH_CONTRAST))
    // Ordering is load-bearing: the dark flavour overrides surfaces the
    // palette also sets, so it has to be applied after the palette.
    run {
        val o = ThemeSelection.overlaysFor(
            Prefs.PALETTE_FOREST, Prefs.DARK_OLED, Prefs.DENSITY_SPACIOUS,
            isDark = true, highContrast = true)
        check("palette precedes dark flavour",
            o.indexOf(ThemeSelection.PALETTE_FOREST) < o.indexOf(ThemeSelection.DARK_OLED))
        check("dark flavour precedes density",
            o.indexOf(ThemeSelection.DARK_OLED) < o.indexOf(ThemeSelection.DENSITY_SPACIOUS))
        check("contrast is last", o.last() == ThemeSelection.HIGH_CONTRAST)
    }
    // A stored value can outlive a downgrade; it must degrade to the base
    // theme rather than crash or apply a garbage overlay.
    eq("unknown palette falls back", ThemeSelection.overlaysFor(
        99, Prefs.DARK_WARM, Prefs.DENSITY_COMFORTABLE,
        isDark = false, highContrast = false).size, 0)
    eq("negative density falls back", ThemeSelection.overlaysFor(
        Prefs.PALETTE_CALM, Prefs.DARK_WARM, -3,
        isDark = false, highContrast = false).size, 0)

    println("Dynamic colour arbitration")
    check("on by default", ThemeSelection.useDynamicColor(true, Prefs.PALETTE_CALM, true))
    // Picking a palette is deliberate and must beat the wallpaper.
    check("explicit palette wins over wallpaper",
        !ThemeSelection.useDynamicColor(true, Prefs.PALETTE_DUSK, true))
    check("off when unsupported",
        !ThemeSelection.useDynamicColor(true, Prefs.PALETTE_CALM, false))
    check("off when declined",
        !ThemeSelection.useDynamicColor(false, Prefs.PALETTE_CALM, true))

    // ---------------------------------------------------------- catalogue
    println("Settings catalogue")
    val lists = listOf(
        "palettes" to Catalog.palettes,
        "darkVariants" to Catalog.darkVariants,
        "densities" to Catalog.densities,
        "motionLevels" to Catalog.motionLevels,
        "hapticLevels" to Catalog.hapticLevels,
        "startDestinations" to Catalog.startDestinations,
    )
    for ((name, list) in lists) {
        // Ids double as stored preference values, so a duplicate or a gap
        // would make a setting unselectable or silently select the wrong one.
        check("$name ids are unique", list.map { it.id }.toSet().size == list.size)
        check("$name ids are dense from zero", list.map { it.id }.sorted() == list.indices.toList())
        check("$name keys are unique", list.map { it.key }.toSet().size == list.size)
        check("$name has labels", list.all { it.label.isNotBlank() })
        // Every option explains itself: the whole point of the detail line is
        // that a user can tell the options apart without trying each one.
        check("$name has details", list.all { it.detail.isNotBlank() })
        check("$name details are distinct", list.map { it.detail }.toSet().size == list.size)
    }
    eq("five palettes", Catalog.palettes.size, Prefs.PALETTE_COUNT)
    eq("palette order matches ids", Catalog.palettes.map { it.id }, (0..4).toList())
    eq("calm is first", Catalog.palettes.first().id, Prefs.PALETTE_CALM)
    eq("motion levels cover the enum", Catalog.motionLevels.size, 4)
    eq("haptic levels cover the enum", Catalog.hapticLevels.size, 4)
    eq("motion none is the Prefs constant", Catalog.motionLevels.first().id, Prefs.MOTION_NONE)
    eq("haptics off is the Prefs constant", Catalog.hapticLevels.first().id, Prefs.HAPTICS_OFF)
    eq("start today is the Prefs constant",
        Catalog.startDestinations.first().id, Prefs.START_TODAY)

    println("Catalogue lookup")
    eq("looks up by id", Catalog.labelOf(Catalog.palettes, Prefs.PALETTE_DUSK), "Dusk")
    // A stored id can outlive the option that produced it; showing the
    // default beats showing a blank row or crashing the settings screen.
    eq("unknown id falls back", Catalog.labelOf(Catalog.palettes, 99), "Calm")
    eq("negative id falls back", Catalog.labelOf(Catalog.densities, -1), "Compact")
    // The catalogue and the metrics table are indexed by the same ids, so
    // every offered density must map to distinct metrics -- if two entries
    // collided, one option would be a no-op the user could not detect.
    check("every density maps to distinct metrics",
        Catalog.densities.map { Density.metrics(it.id) }.toSet().size == 3)

    // ---- Contrast maths -------------------------------------------------
    // The reference anchors: WCAG defines black-on-white as exactly 21:1 and
    // any colour against itself as 1:1.
    check("contrast black/white is 21", Math.abs(Contrast.ratio(Contrast.BLACK, Contrast.WHITE) - 21.0) < 0.001)
    check("contrast white/black is symmetric", Math.abs(Contrast.ratio(Contrast.WHITE, Contrast.BLACK) - Contrast.ratio(Contrast.BLACK, Contrast.WHITE)) < 1e-9)
    check("contrast self is 1", Math.abs(Contrast.ratio(0xFF3A7D5C.toInt(), 0xFF3A7D5C.toInt()) - 1.0) < 1e-9)
    check("luminance white is 1", Math.abs(Contrast.luminance(Contrast.WHITE) - 1.0) < 1e-9)
    check("luminance black is 0", Math.abs(Contrast.luminance(Contrast.BLACK)) < 1e-9)
    check("luminance ignores alpha", Contrast.luminance(0x00FFFFFF) == Contrast.luminance(0xFFFFFFFF.toInt()))
    check("luminance is monotonic in grey", Contrast.luminance(0xFF404040.toInt()) < Contrast.luminance(0xFF808080.toInt()))
    // Green contributes most to luminance, blue least - the coefficients are
    // not equal weights, and swapping channels must change the answer.
    check("luminance weights channels", Contrast.luminance(0xFF00FF00.toInt()) > Contrast.luminance(0xFFFF0000.toInt()))
    check("luminance blue is darkest primary", Contrast.luminance(0xFF0000FF.toInt()) < Contrast.luminance(0xFFFF0000.toInt()))
    check("meets threshold", Contrast.meets(Contrast.BLACK, Contrast.WHITE, Contrast.AAA_NORMAL))
    check("fails threshold", !Contrast.meets(0xFF959595.toInt(), Contrast.WHITE, Contrast.AA_NORMAL))

    // ---- Swatch check-mark legibility ------------------------------------
    // Every palette swatch shows a check mark when selected, drawn in either
    // black or white. These are the real colour values from
    // res/values/colors_palettes.xml and colors.xml; if a ramp is
    // regenerated and a swatch stops clearing AA, this fails.
    val swatchColors = listOf(
        "calm primary" to 0xFF3A7D5C.toInt(),
        "calm secondary" to 0xFFB4703A.toInt(),
        "forest primary" to 0xFF2D694E.toInt(),
        "forest secondary" to 0xFF685F26.toInt(),
        "ocean primary" to 0xFF176775.toInt(),
        "ocean secondary" to 0xFFC15738.toInt(),
        "dusk primary" to 0xFF65568C.toInt(),
        "dusk secondary" to 0xFFB15F64.toInt(),
        "mono primary" to 0xFF625E59.toInt(),
        "mono secondary" to 0xFF95908B.toInt()
    )
    for ((name, color) in swatchColors) {
        val on = Contrast.onColorFor(color)
        check("swatch check legible on $name", Contrast.meets(on, color, Contrast.AA_NORMAL))
        check("swatch check is black or white on $name", on == Contrast.BLACK || on == Contrast.WHITE)
        // onColorFor must pick the better of the two, not merely an adequate one.
        val other = if (on == Contrast.BLACK) Contrast.WHITE else Contrast.BLACK
        check("swatch check picks best on $name", Contrast.ratio(on, color) >= Contrast.ratio(other, color))
    }

    // The reason onColorFor uses real luminance instead of the common
    // (r*299 + g*587 + b*114) / 1000 brightness average: on these three
    // swatches the two methods disagree, and the naive one picks the
    // less legible colour.
    val naiveDisagrees = listOf(0xFFB4703A.toInt(), 0xFFC15738.toInt(), 0xFFB15F64.toInt())
    for (color in naiveDisagrees) {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val naive = if ((r * 299 + g * 587 + b * 114) / 1000 > 128) Contrast.BLACK else Contrast.WHITE
        check("luminance beats naive average on ${Integer.toHexString(color)}", Contrast.onColorFor(color) != naive)
    }

    // --------------------------------------------------- history states

    println("History state encoding matches the domain layer")
    // These constants must equal what Insights.historyStates emits. If the
    // domain renumbers them, the strip silently renders rest days as misses.
    eq("COMPLETED", HistoryStates.COMPLETED, 1)
    eq("PENDING", HistoryStates.PENDING, 0)
    eq("MISSED", HistoryStates.MISSED, -1)
    eq("SKIPPED", HistoryStates.SKIPPED, -2)
    eq("INACTIVE", HistoryStates.INACTIVE, -3)
    eq("all states distinct", HistoryStates.all.toSet().size, HistoryStates.all.size)

    // Pin against the real producer rather than against these same numbers
    // written twice. Insights.historyStates maps OpportunityStatus to these
    // ints; if that mapping changes, this fails.
    run {
        val src = java.io.File("app/src/main/kotlin/com/superflow/domain/Insights.kt").readText()
        val body = src.substringAfter("fun historyStates").substringBefore("\n    }")
        fun mapped(status: String): Int? =
            Regex("""OpportunityStatus\.$status[^>]*->\s*(-?\d+)""").find(body)
                ?.groupValues?.get(1)?.toInt()
        eq("domain COMPLETED", mapped("COMPLETED"), HistoryStates.COMPLETED)
        eq("domain MISSED", mapped("MISSED"), HistoryStates.MISSED)
        eq("domain SKIPPED_PLANNED", mapped("SKIPPED_PLANNED"), HistoryStates.SKIPPED)
        eq("domain PENDING", mapped("PENDING"), HistoryStates.PENDING)
        eq("domain NOT_SCHEDULED", mapped("NOT_SCHEDULED"), HistoryStates.INACTIVE)
    }
    for (state in HistoryStates.all) {
        check("state $state has a label", HistoryStates.labelFor(state).isNotBlank())
        check("state $state emphasis in range", HistoryStates.emphasisFor(state) in 0f..1f)
    }
    // An unknown value must not crash or claim to be a real state.
    check("unknown state falls back", HistoryStates.labelFor(42).isNotBlank())
    check("unknown state is faint", HistoryStates.emphasisFor(42) < 0.2f)
    // Completed must read strongest, or the strip inverts its meaning.
    check(
        "completed is the strongest",
        HistoryStates.all.all { HistoryStates.emphasisFor(HistoryStates.COMPLETED) >= HistoryStates.emphasisFor(it) }
    )
    println()

    println("Completion rate ignores days that were never opportunities")
    check("only completed and missed count", HistoryStates.countsAsOpportunity(HistoryStates.COMPLETED))
    check("missed counts", HistoryStates.countsAsOpportunity(HistoryStates.MISSED))
    check("skipped does not count", !HistoryStates.countsAsOpportunity(HistoryStates.SKIPPED))
    check("inactive does not count", !HistoryStates.countsAsOpportunity(HistoryStates.INACTIVE))
    check("pending does not count", !HistoryStates.countsAsOpportunity(HistoryStates.PENDING))

    eq("empty history has no rate", HistoryStates.completionRate(emptyList()), null)
    eq(
        "all-unscheduled has no rate",
        HistoryStates.completionRate(listOf(-3, -3, -3)),
        null
    )
    eq(
        "perfect week is 1.0",
        HistoryStates.completionRate(listOf(1, 1, 1, 1)),
        1.0
    )
    eq(
        "half is 0.5",
        HistoryStates.completionRate(listOf(1, -1, 1, -1)),
        0.5
    )
    eq(
        "rest days do not dilute the rate",
        HistoryStates.completionRate(listOf(1, -2, 1, -3, 0)),
        1.0
    )
    println()

    println("Streaks survive rest days but not misses")
    eq("empty streak", HistoryStates.currentStreak(emptyList()), 0)
    eq("single done", HistoryStates.currentStreak(listOf(1)), 1)
    eq("three in a row", HistoryStates.currentStreak(listOf(1, 1, 1)), 3)
    eq("a miss ends it", HistoryStates.currentStreak(listOf(1, 1, -1)), 0)
    eq("miss earlier is fine", HistoryStates.currentStreak(listOf(-1, 1, 1)), 2)
    // The point of the rule: a planned rest day is following the plan, not
    // breaking it, so it must not reset the count.
    eq("rest day bridges", HistoryStates.currentStreak(listOf(1, -2, 1)), 2)
    eq("unscheduled bridges", HistoryStates.currentStreak(listOf(1, -3, 1)), 2)
    eq("today pending does not end it", HistoryStates.currentStreak(listOf(1, 1, 0)), 2)
    eq("only rest days is zero", HistoryStates.currentStreak(listOf(-2, -3, 0)), 0)
    eq("miss then rest stays zero", HistoryStates.currentStreak(listOf(1, -1, -2)), 0)
    println()

    // ------------------------------------------------------ chart geometry

    println("Nice ceilings produce readable axes")
    eq("zero", ChartGeometry.niceCeiling(0.0), 1.0)
    eq("negative", ChartGeometry.niceCeiling(-5.0), 1.0)
    eq("exactly 1", ChartGeometry.niceCeiling(1.0), 1.0)
    eq("1.5 -> 2", ChartGeometry.niceCeiling(1.5), 2.0)
    eq("3 -> 5", ChartGeometry.niceCeiling(3.0), 5.0)
    eq("7 -> 10", ChartGeometry.niceCeiling(7.0), 10.0)
    eq("37 -> 50", ChartGeometry.niceCeiling(37.0), 50.0)
    eq("74 -> 100", ChartGeometry.niceCeiling(74.0), 100.0)
    eq("100 stays", ChartGeometry.niceCeiling(100.0), 100.0)
    // The property that matters: never below the data, never absurdly above.
    for (v in listOf(0.3, 1.0, 2.7, 9.9, 13.0, 61.0, 480.0, 1001.0)) {
        val c = ChartGeometry.niceCeiling(v)
        check("ceiling >= value for $v", c >= v)
        check("ceiling < 10x value for $v", c < v * 10)
    }
    println()

    println("Axis ticks span zero to the ceiling")
    for (maxV in listOf(1.0, 7.0, 37.0, 100.0, 480.0)) {
        val ticks = ChartGeometry.axisTicks(maxV)
        check("ticks for $maxV start at zero", ticks.first() == 0.0)
        check("ticks for $maxV reach the data", ticks.last() >= maxV)
        check("ticks for $maxV ascend", ticks.zipWithNext().all { (a, b) -> b > a })
        check("ticks for $maxV are not absurd", ticks.size in 2..16)
    }
    eq("degenerate max", ChartGeometry.axisTicks(0.0), listOf(0.0))
    println()

    println("Normalise clamps rather than overflowing")
    eq("zero max is zero", ChartGeometry.normalise(5.0, 0.0), 0f)
    eq("half", ChartGeometry.normalise(5.0, 10.0), 0.5f)
    eq("full", ChartGeometry.normalise(10.0, 10.0), 1f)
    eq("outlier clamps", ChartGeometry.normalise(50.0, 10.0), 1f)
    eq("negative clamps", ChartGeometry.normalise(-5.0, 10.0), 0f)
    println()

    println("Bar metrics degrade sensibly as bars multiply")
    run {
        val wide = ChartGeometry.barMetrics(300f, 7)
        check("7 bars in 300 are comfortable", wide.barWidth > 30f)
        check("7 bars keep the preferred gap", wide.gap == 6f)
        check("7 bars do not overflow", !wide.overflow)

        val tight = ChartGeometry.barMetrics(300f, 60)
        check("60 bars stay at least the minimum", tight.barWidth >= 3f)
        check("60 bars shrink the gap instead", tight.gap < 6f)
        check("60 bars do not overflow", !tight.overflow)

        val absurd = ChartGeometry.barMetrics(100f, 200)
        check("200 bars in 100px flags overflow", absurd.overflow)

        eq("zero bars", ChartGeometry.barMetrics(300f, 0).barWidth, 0f)
        eq("zero width", ChartGeometry.barMetrics(0f, 7).barWidth, 0f)

        // Bars plus gaps must fit the space they were given.
        for (n in listOf(1, 2, 7, 14, 30, 52)) {
            val m = ChartGeometry.barMetrics(300f, n)
            val total = m.barWidth * n + m.gap * (n - 1)
            check("$n bars fit in 300px", total <= 300.5f)
        }
    }
    println()

    println("Bar hit testing covers the gaps")
    run {
        eq("left edge", ChartGeometry.barIndexAt(0f, 300f, 7), 0)
        eq("right edge", ChartGeometry.barIndexAt(299f, 300f, 7), 6)
        eq("past the end", ChartGeometry.barIndexAt(400f, 300f, 7), null)
        eq("before the start", ChartGeometry.barIndexAt(-1f, 300f, 7), null)
        eq("no bars", ChartGeometry.barIndexAt(10f, 300f, 0), null)
        // Every pixel in the plot must resolve to some bar: a touch that
        // lands in a gap and does nothing feels broken.
        var x = 0f
        while (x < 300f) {
            check("x=$x hits a bar", ChartGeometry.barIndexAt(x, 300f, 7) != null)
            x += 7.5f
        }
        // And the index must agree with where the bar was actually drawn.
        for (i in 0 until 7) {
            val off = ChartGeometry.barOffset(i, 300f, 7)
            val m = ChartGeometry.barMetrics(300f, 7)
            val centre = off + m.barWidth / 2f
            eq("bar $i centre hits bar $i", ChartGeometry.barIndexAt(centre, 300f, 7), i)
        }
    }
    println()

    println("Heatmap weeks pad to full columns")
    run {
        val w = ChartGeometry.heatmapWeeks(List(14) { 1 })
        eq("14 days is 2 weeks", w.size, 2)
        check("every column is 7 tall", w.all { it.size == 7 })

        val offset = ChartGeometry.heatmapWeeks(List(7) { 1 }, firstWeekday = 3)
        eq("offset spills into 2 columns", offset.size, 2)
        eq("first three are padding", offset[0].take(3), listOf(null, null, null))

        val partial = ChartGeometry.heatmapWeeks(List(10) { 1 })
        eq("10 days is 2 columns", partial.size, 2)
        eq("trailing padding", partial[1].count { it == null }, 4)

        eq("empty", ChartGeometry.heatmapWeeks(emptyList()), emptyList<List<Int?>>())
        // Offsets outside 0..6 must not corrupt the grid.
        check("offset clamps high", ChartGeometry.heatmapWeeks(List(7) { 1 }, 99).all { it.size == 7 })
        check("offset clamps low", ChartGeometry.heatmapWeeks(List(7) { 1 }, -5).all { it.size == 7 })

        check("cell size positive", ChartGeometry.heatmapCellSize(300f, 10) > 0f)
        check("degenerate cell size", ChartGeometry.heatmapCellSize(0f, 0) >= 1f)
    }
    println()

    println("Rolling mean starts at the left edge")
    run {
        val r = ChartGeometry.rollingMean(listOf(1.0, 2.0, 3.0, 4.0), 2)
        eq("same length as input", r.size, 4)
        eq("first is itself", r[0], 1.0)
        eq("second averages two", r[1], 1.5)
        eq("last averages two", r[3], 3.5)
        eq("empty input", ChartGeometry.rollingMean(emptyList(), 7), emptyList<Double>())
        eq("window 1 is identity", ChartGeometry.rollingMean(listOf(1.0, 5.0), 1), listOf(1.0, 5.0))
        // A window longer than the data averages everything available.
        eq("oversized window", ChartGeometry.rollingMean(listOf(2.0, 4.0), 90), listOf(2.0, 3.0))
    }
    println()

    println("Correlation refuses to overclaim")
    run {
        eq("too few points", ChartGeometry.correlation(listOf(1.0, 2.0), listOf(1.0, 2.0)), null)
        eq(
            "mismatched lengths",
            ChartGeometry.correlation(listOf(1.0, 2.0, 3.0), listOf(1.0)),
            null
        )
        eq(
            "no variance in x",
            ChartGeometry.correlation(listOf(3.0, 3.0, 3.0, 3.0), listOf(1.0, 2.0, 3.0, 4.0)),
            null
        )
        eq(
            "no variance in y",
            ChartGeometry.correlation(listOf(1.0, 2.0, 3.0, 4.0), listOf(3.0, 3.0, 3.0, 3.0)),
            null
        )
        val perfect = ChartGeometry.correlation(
            listOf(1.0, 2.0, 3.0, 4.0), listOf(2.0, 4.0, 6.0, 8.0)
        )
        check("perfect positive is 1", perfect != null && abs(perfect - 1.0) < 1e-9)
        val inverse = ChartGeometry.correlation(
            listOf(1.0, 2.0, 3.0, 4.0), listOf(8.0, 6.0, 4.0, 2.0)
        )
        check("perfect negative is -1", inverse != null && abs(inverse + 1.0) < 1e-9)
        for (r in listOf(perfect, inverse)) {
            check("stays in range", r != null && r in -1.0..1.0)
        }

        // Small samples must never be described as a finding, however
        // strong the arithmetic looks.
        eq("null is not enough data", ChartGeometry.correlationLabel(null, 100), "Not enough data yet")
        eq("13 samples is not enough", ChartGeometry.correlationLabel(0.9, 13), "Not enough data yet")
        check("14 samples may speak", ChartGeometry.correlationLabel(0.9, 14) != "Not enough data yet")
        eq("weak reads as no pattern", ChartGeometry.correlationLabel(0.1, 60), "No clear pattern")
        check("labels never say because", ChartGeometry.correlationLabel(0.9, 60).contains("tendency"))
        check(
            "negative labels say inverse",
            ChartGeometry.correlationLabel(-0.9, 60).contains("inverse")
        )
    }
    println()

    println("Axis labels thin out instead of overlapping")
    run {
        eq("few labels all show", ChartGeometry.labelStride(5, 300f), 1)
        check("many labels stride out", ChartGeometry.labelStride(90, 300f) > 1)
        eq("degenerate count", ChartGeometry.labelStride(0, 300f), 1)
        eq("degenerate width", ChartGeometry.labelStride(10, 0f), 1)
        // The most recent point is always labelled.
        check("last always shows", ChartGeometry.showLabel(89, 90, 8))
        check("first shows", ChartGeometry.showLabel(0, 90, 8))
        check("stride 1 shows all", ChartGeometry.showLabel(37, 90, 1))
        // And thinning must actually reduce the count below what fits.
        val stride = ChartGeometry.labelStride(90, 300f)
        val shown = (0 until 90).count { ChartGeometry.showLabel(it, 90, stride) }
        check("thinned labels fit", shown <= 90 / stride + 2)
    }
    println()

    println("Ranges give flat series a visible band")
    run {
        eq("empty range", ChartGeometry.rangeOf(emptyList()), 0.0..1.0)
        val flat = ChartGeometry.rangeOf(listOf(5.0, 5.0, 5.0))
        check("flat series is padded", flat.endInclusive > flat.start)
        check("flat series contains its value", 5.0 in flat)
        val zeros = ChartGeometry.rangeOf(listOf(0.0, 0.0))
        check("all-zero is padded", zeros.endInclusive > zeros.start)
        val normal = ChartGeometry.rangeOf(listOf(1.0, 9.0, 4.0))
        eq("normal low", normal.start, 1.0)
        eq("normal high", normal.endInclusive, 9.0)

        eq("fraction at start", ChartGeometry.fractionIn(1.0, 1.0..9.0), 0f)
        eq("fraction at end", ChartGeometry.fractionIn(9.0, 1.0..9.0), 1f)
        eq("fraction midway", ChartGeometry.fractionIn(5.0, 1.0..9.0), 0.5f)
        eq("fraction clamps", ChartGeometry.fractionIn(99.0, 1.0..9.0), 1f)
        eq("degenerate range centres", ChartGeometry.fractionIn(5.0, 5.0..5.0), 0.5f)
    }
    println()

    println("Percent and zoom clamp")
    eq("percent rounds", ChartGeometry.percent(0.666), 67)
    eq("percent floor", ChartGeometry.percent(-1.0), 0)
    eq("percent ceiling", ChartGeometry.percent(2.0), 100)
    eq("zoom min", ChartGeometry.clampZoom(0.1f), 1f)
    eq("zoom max", ChartGeometry.clampZoom(99f), 4f)
    eq("zoom passthrough", ChartGeometry.clampZoom(2.5f), 2.5f)
    println()

    println("Heatmap cell indices round-trip")
    // SfHeatmap converts a tap to (col,row) then back to a series index, and
    // separately converts a selected index to (col,row) to draw the ring.
    // Those two conversions must be exact inverses or the ring lands on a
    // different day than the label describes. Checked for every weekday
    // offset, since that is the term that makes it non-obvious.
    for (offset in 0..6) {
        for (n in listOf(1, 7, 10, 14, 30, 90)) {
            val grid = ChartGeometry.heatmapWeeks((0 until n).toList(), offset)
            grid.forEachIndexed { col, week ->
                week.forEachIndexed { row, value ->
                    if (value != null) {
                        val recovered = col * 7 + row - offset
                        eq("tap n=$n off=$offset col=$col row=$row", recovered, value)
                    }
                }
            }
            for (sel in 0 until n) {
                val padded = sel + offset
                val col = padded / 7
                val row = padded % 7
                eq("ring n=$n off=$offset sel=$sel", grid[col][row], sel)
            }
        }
    }
    println()

    // ------------------------------------------------------------ periods

    println("Periods cover sensible windows")
    eq("four periods", Periods.all.size, 4)
    eq("ids are dense from zero", Periods.all.map { it.id }, listOf(0, 1, 2, 3))
    check("labels unique", Periods.all.map { it.label }.toSet().size == 4)
    check("days ascend", Periods.all.zipWithNext().all { (a, b) -> b.days > a.days })
    for (p in Periods.all) {
        check("${p.label} has positive days", p.days > 0)
        check("${p.label} has a positive bucket", p.barBucket >= 1)
        // The whole point of bucketing: no chart should ask a phone to draw
        // hundreds of distinguishable bars.
        check("${p.label} draws a readable number of bars", p.barCount in 1..60)
    }
    eq("week is daily", Periods.week.barBucket, 1)
    eq("year buckets monthly", Periods.year.barBucket, 30)
    eq("unknown id falls back to month", Periods.byId(99), Periods.month)
    eq("negative id falls back", Periods.byId(-1), Periods.month)
    println()

    println("Sample thresholds gate claims")
    check("5 days supports a rate", Periods.canClaim(5, Periods.MinSamples.COMPLETION_RATE))
    check("4 days does not", !Periods.canClaim(4, Periods.MinSamples.COMPLETION_RATE))
    check("13 days cannot correlate", !Periods.canClaim(13, Periods.MinSamples.CORRELATION))
    check("14 days can", Periods.canClaim(14, Periods.MinSamples.CORRELATION))
    // Correlation thresholds must agree between the two places that gate it.
    eq(
        "correlation threshold matches ChartGeometry",
        ChartGeometry.correlationLabel(0.9, Periods.MinSamples.CORRELATION - 1),
        "Not enough data yet"
    )
    check(
        "at the threshold ChartGeometry speaks",
        ChartGeometry.correlationLabel(0.9, Periods.MinSamples.CORRELATION) != "Not enough data yet"
    )
    // A trend compares two windows, so it must need more than a single rate.
    check(
        "trend needs more than a rate",
        Periods.MinSamples.TREND > Periods.MinSamples.COMPLETION_RATE
    )
    println()

    println("Caveats are attached until the sample is comfortable")
    eq("below minimum", Periods.caveatFor(3, 5), "Not enough data yet")
    eq("just above minimum is caveated", Periods.caveatFor(6, 5), "Based on 6 days so far")
    eq("comfortably above is clean", Periods.caveatFor(50, 5), null)
    eq("exactly at minimum is caveated", Periods.caveatFor(5, 5), "Based on 5 days so far")
    eq("exactly at twice is clean", Periods.caveatFor(10, 5), null)
    println()

    println("Bucketing averages rather than samples")
    run {
        val ninety = (1..90).map { it.toDouble() }
        val bucketed = Periods.bucket(ninety, Periods.quarter)
        eq("90 days into weeks", bucketed.size, 13)
        // First week is days 1..7, mean 4.
        eq("first bucket is the mean", bucketed.first(), 4.0)
        // A partial trailing bucket averages what it has rather than being
        // dropped - those are the most recent days.
        eq("trailing partial kept", bucketed.size, (90 + 6) / 7)

        eq("daily period is untouched", Periods.bucket(ninety, Periods.week), ninety)
        eq("empty stays empty", Periods.bucket(emptyList(), Periods.year), emptyList<Double>())

        // Bucketing must never invent or lose signal: the mean of the
        // buckets should track the mean of the input.
        val flat = List(90) { 0.5 }
        check(
            "flat input stays flat",
            Periods.bucket(flat, Periods.quarter).all { abs(it - 0.5) < 1e-9 }
        )
    }
    println()

    println("Windows never pad short data with zeros")
    run {
        val three = listOf(1.0, 1.0, 1.0)
        eq("short input returned whole", Periods.window(three, Periods.month), three)
        eq("short input not padded", Periods.window(three, Periods.year).size, 3)
        val long = (1..500).map { it.toDouble() }
        eq("long input truncated", Periods.window(long, Periods.month).size, 30)
        // Truncation keeps the most recent days, not the oldest.
        eq("keeps the recent end", Periods.window(long, Periods.week).last(), 500.0)
        eq("drops the old end", Periods.window(long, Periods.week).first(), 494.0)
    }
    println()


    // ============================================================ journey
    println("Journey hierarchy is a real chain")
    run {
        eq("four levels", JourneyTree.Kind.ordered.size, 4)
        eq("breadcrumb", JourneyTree.chainLabel, "Identity \u2192 Goal \u2192 System \u2192 Habit")
        check("ranks are 0..3", JourneyTree.Kind.ordered.mapIndexed { i, k -> k.rank == i }.all { it })
        eq("identity has no parent", JourneyTree.Kind.IDENTITY.parent, null)
        eq("habit has no child", JourneyTree.Kind.HABIT.child, null)
        eq("goal sits under identity", JourneyTree.Kind.GOAL.parent, JourneyTree.Kind.IDENTITY)
        eq("system sits under goal", JourneyTree.Kind.SYSTEM.parent, JourneyTree.Kind.GOAL)
        eq("habit sits under system", JourneyTree.Kind.HABIT.parent, JourneyTree.Kind.SYSTEM)
        // Section 6.5 fixes the accent per entity type; ui/ resolves these.
        eq("identity accent", JourneyTree.Kind.IDENTITY.accent, "primary")
        eq("goal accent", JourneyTree.Kind.GOAL.accent, "secondary")
        eq("system accent", JourneyTree.Kind.SYSTEM.accent, "tertiary")
        eq("habit accent", JourneyTree.Kind.HABIT.accent, "neutral")
        check("keys round-trip", JourneyTree.Kind.ordered.all { JourneyTree.Kind.byKey(it.key) == it })
        eq("unknown key is null", JourneyTree.Kind.byKey("project"), null)
        // Every chain step is reversible: child.parent == self.
        check("parent and child agree", JourneyTree.Kind.ordered.all { k ->
            k.child?.parent == k || k.child == null
        })
    }
    println()

    fun jn(
        id: String, kind: JourneyTree.Kind, parent: String? = null,
        title: String = id, active: Boolean = true, archived: Boolean = false
    ) = JourneyTree.Node(id, kind, parent, title, active = active, archived = archived)

    val fullChain = listOf(
        jn("i1", JourneyTree.Kind.IDENTITY, title = "Someone who moves daily"),
        jn("g1", JourneyTree.Kind.GOAL, "i1", "Walk 5km"),
        jn("s1", JourneyTree.Kind.SYSTEM, "g1", "Morning loop"),
        jn("h1", JourneyTree.Kind.HABIT, "s1", "Walk 10 minutes"),
    )
    val allOpen = fullChain.map { JourneyTree.expansionKey(it.kind, it.id) }.toSet()

    println("A complete chain flattens in order")
    run {
        val t = JourneyTree.build(fullChain, allOpen)
        eq("four rows", t.linked.size, 4)
        eq("nothing unlinked", t.unlinked.size, 0)
        eq("order", t.linked.map { it.node.id }, listOf("i1", "g1", "s1", "h1"))
        eq("depths", t.linked.map { it.depth }, listOf(0, 1, 2, 3))
        check("every row is last of its siblings", t.linked.all { it.last })
        check("no orphans", t.linked.none { it.orphan })
        eq("identity counts the habit", t.linked[0].habitCount, 1)
        eq("identity counts descendants", t.linked[0].descendantCount, 3)
        eq("identity has one child", t.linked[0].childCount, 1)
        eq("habit has no descendants", t.linked[3].descendantCount, 0)
        check("nothing dormant", t.linked.none { it.dormant })
        eq("chain reaches four", t.summary.deepestChain, 4)
        eq("summary habits", t.summary.habits, 1)
        eq("summary active habits", t.summary.activeHabits, 1)
        eq("summary unlinked", t.summary.unlinked, 0)
        check("keys are unique", t.rows.map { it.key }.toSet().size == t.rows.size)
        check("not empty", !t.isEmpty)
    }
    println()

    println("Collapsing hides children but never loses their count")
    run {
        val t = JourneyTree.build(fullChain, setOf(JourneyTree.expansionKey(JourneyTree.Kind.IDENTITY, "i1")))
        eq("two rows shown", t.linked.map { it.node.id }, listOf("i1", "g1"))
        // The habit is three levels down behind a collapsed goal, and the
        // identity still reports it. A count that only worked while the
        // subtree happened to be open would be useless.
        eq("collapsed subtree still counted", t.linked[0].habitCount, 1)
        eq("collapsed goal is expandable", t.linked[1].expandable, true)
        eq("collapsed goal not expanded", t.linked[1].expanded, false)

        val none = JourneyTree.build(fullChain, emptySet())
        eq("fully collapsed shows roots only", none.linked.map { it.node.id }, listOf("i1"))
        eq("root still counts everything", none.linked[0].descendantCount, 3)
    }
    println()

    println("Every node survives the flattening")
    run {
        // The property that matters: whatever the links look like, and
        // whatever is expanded, no entity the user created can vanish.
        val messy = listOf(
            jn("i1", JourneyTree.Kind.IDENTITY),
            jn("g1", JourneyTree.Kind.GOAL, "i1"),
            jn("g2", JourneyTree.Kind.GOAL, "nope"),          // dangling ref
            jn("g3", JourneyTree.Kind.GOAL, null),            // never linked
            jn("s1", JourneyTree.Kind.SYSTEM, "g1"),
            jn("s2", JourneyTree.Kind.SYSTEM, "g2"),          // parent dangles
            jn("h1", JourneyTree.Kind.HABIT, "s1"),
            jn("h2", JourneyTree.Kind.HABIT, "i1"),           // wrong-kind link
            jn("h3", JourneyTree.Kind.HABIT, ""),             // blank ref
        )
        val open = messy.map { JourneyTree.expansionKey(it.kind, it.id) }.toSet()
        val t = JourneyTree.build(messy, open)
        eq("all nine present", t.rows.size, messy.size)
        eq("all nine distinct", t.rows.map { it.key }.toSet().size, messy.size)
        eq("ids preserved", t.rows.map { it.node.id }.toSet(), messy.map { it.id }.toSet())

        // A habit pointing at an identity is a two-rank jump; treated as
        // unlinked rather than drawn under a missing system.
        check("wrong-kind link is unlinked", t.unlinked.any { it.node.id == "h2" })
        check("blank ref is unlinked", t.unlinked.any { it.node.id == "h3" })
        check("dangling ref is unlinked", t.unlinked.any { it.node.id == "g2" })
        check("null ref is unlinked", t.unlinked.any { it.node.id == "g3" })
        // s2 hangs off g2, which is itself unlinked - so it appears under
        // g2 in the unlinked section, not as its own root.
        eq("unlinked roots grouped by kind",
            t.unlinked.filter { it.depth == 0 }.map { it.node.kind.rank },
            t.unlinked.filter { it.depth == 0 }.map { it.node.kind.rank }.sorted())
        check("orphan roots are flagged", t.unlinked.filter { it.depth == 0 }.all { it.orphan })
        check("children of orphans are not themselves flagged",
            t.unlinked.filter { it.depth > 0 }.none { it.orphan })
        eq("summary counts the breaks", t.summary.unlinked, 4)
    }
    println()

    println("Deepest chain only counts identity-rooted paths")
    run {
        val rootless = listOf(
            jn("g1", JourneyTree.Kind.GOAL),
            jn("s1", JourneyTree.Kind.SYSTEM, "g1"),
            jn("h1", JourneyTree.Kind.HABIT, "s1"),
        )
        eq("three deep but unrooted", JourneyTree.build(rootless).summary.deepestChain, 0)
        eq("a lone identity is one", JourneyTree.build(listOf(jn("i", JourneyTree.Kind.IDENTITY))).summary.deepestChain, 1)
        eq("identity plus goal is two",
            JourneyTree.build(listOf(jn("i", JourneyTree.Kind.IDENTITY), jn("g", JourneyTree.Kind.GOAL, "i")))
                .summary.deepestChain, 2)
        eq("empty tree", JourneyTree.build(emptyList()).summary.deepestChain, 0)
        check("empty tree is empty", JourneyTree.build(emptyList()).isEmpty)
    }
    println()

    println("Dormancy follows activity, not archival alone")
    run {
        val paused = listOf(
            jn("i1", JourneyTree.Kind.IDENTITY),
            jn("g1", JourneyTree.Kind.GOAL, "i1"),
            jn("s1", JourneyTree.Kind.SYSTEM, "g1"),
            jn("h1", JourneyTree.Kind.HABIT, "s1", active = false),
        )
        val open = paused.map { JourneyTree.expansionKey(it.kind, it.id) }.toSet()
        val t = JourneyTree.build(paused, open)
        check("whole chain reads dormant", t.linked.all { it.dormant })
        eq("no active habits counted", t.linked[0].habitCount, 0)
        // Descendants are still there; dormant is a weight, not a filter.
        eq("descendants unchanged", t.linked[0].descendantCount, 3)
        eq("summary sees the habit", t.summary.habits, 1)
        eq("summary sees it as inactive", t.summary.activeHabits, 0)

        val archived = paused.dropLast(1) + jn("h1", JourneyTree.Kind.HABIT, "s1", archived = true)
        eq("archived does not count as active", JourneyTree.build(archived, open).summary.activeHabits, 0)

        val mixed = paused + jn("h2", JourneyTree.Kind.HABIT, "s1")
        val tm = JourneyTree.build(mixed, open)
        check("one live habit revives the chain", tm.linked.filter { it.node.kind != JourneyTree.Kind.HABIT }.none { it.dormant })
        check("the paused habit stays dormant", tm.linked.first { it.node.id == "h1" }.dormant)
        check("the live habit is not dormant", tm.linked.first { it.node.id == "h2" }.dormant.not())
    }
    println()

    println("Sibling order and connectors")
    run {
        val many = listOf(
            jn("i1", JourneyTree.Kind.IDENTITY),
            jn("g1", JourneyTree.Kind.GOAL, "i1"),
            jn("g2", JourneyTree.Kind.GOAL, "i1"),
            jn("g3", JourneyTree.Kind.GOAL, "i1"),
        )
        val t = JourneyTree.build(many, setOf(JourneyTree.expansionKey(JourneyTree.Kind.IDENTITY, "i1")))
        eq("input order kept", t.linked.map { it.node.id }, listOf("i1", "g1", "g2", "g3"))
        eq("only the last goal is last", t.linked.filter { it.node.kind == JourneyTree.Kind.GOAL }.map { it.last },
            listOf(false, false, true))
        eq("three children", t.linked[0].childCount, 3)
        // Reversed input reverses display: the list is the ordering.
        val rev = JourneyTree.build(listOf(many[0], many[3], many[2], many[1]),
            setOf(JourneyTree.expansionKey(JourneyTree.Kind.IDENTITY, "i1")))
        eq("reordering input reorders rows", rev.linked.map { it.node.id }, listOf("i1", "g3", "g2", "g1"))
    }
    println()

    println("Expansion state")
    run {
        val e0 = emptySet<String>()
        val e1 = JourneyTree.toggle(e0, JourneyTree.Kind.GOAL, "x")
        eq("toggle adds", e1, setOf("goal:x"))
        eq("toggle removes", JourneyTree.toggle(e1, JourneyTree.Kind.GOAL, "x"), e0)
        // Same id, different kind: independent entries.
        val e2 = JourneyTree.toggle(e1, JourneyTree.Kind.HABIT, "x")
        eq("kinds do not collide", e2.size, 2)
        val shared = listOf(
            jn("x", JourneyTree.Kind.IDENTITY),
            jn("x", JourneyTree.Kind.GOAL, "x"),
        )
        val t = JourneyTree.build(shared, setOf("identity:x"))
        eq("shared id, only the identity opens", t.linked.map { it.node.id }, listOf("x", "x"))
        eq("goal stays collapsed", t.linked[1].expanded, false)

        eq("default opens identities",
            JourneyTree.defaultExpansion(fullChain).contains("identity:i1"), true)
        check("small tree also opens goals",
            JourneyTree.defaultExpansion(fullChain).contains("goal:g1"))
        val big = (1..20).map { jn("g$it", JourneyTree.Kind.GOAL, "i1") } + jn("i1", JourneyTree.Kind.IDENTITY)
        check("large tree opens identities only",
            JourneyTree.defaultExpansion(big).none { it.startsWith("goal:") })
        check("large tree still opens the identity",
            JourneyTree.defaultExpansion(big).contains("identity:i1"))

        // Deep-linking to a habit must open everything above it, or the
        // reveal scrolls to a row that is not rendered.
        val path = JourneyTree.revealPath(fullChain, JourneyTree.Kind.HABIT, "h1")
        eq("reveal opens three ancestors", path, setOf("identity:i1", "goal:g1", "system:s1"))
        val revealed = JourneyTree.build(fullChain, path)
        check("the target is now rendered", revealed.linked.any { it.node.id == "h1" })
        eq("reveal of an unknown node is empty",
            JourneyTree.revealPath(fullChain, JourneyTree.Kind.HABIT, "nope"), emptySet<String>())
        eq("reveal of a root is empty",
            JourneyTree.revealPath(fullChain, JourneyTree.Kind.IDENTITY, "i1"), emptySet<String>())
    }
    println()

    println("Gaps are guidance, not a chore list")
    run {
        val none = JourneyTree.gaps(emptyList())
        check("empty app asks for an identity", none.any { it.kind == JourneyTree.Kind.IDENTITY })
        check("empty app asks for a habit", none.any { it.kind == JourneyTree.Kind.HABIT })
        check("identity comes first", none.first().kind == JourneyTree.Kind.IDENTITY)

        val complete = JourneyTree.gaps(fullChain)
        eq("a complete chain has nothing to fix", complete, emptyList<JourneyTree.Gap>())

        val dangling = fullChain + jn("g9", JourneyTree.Kind.GOAL, null, "Read more")
        val g = JourneyTree.gaps(dangling)
        check("a dangling goal is flagged", g.any { it.nodeId == "g9" })
        check("the prompt names the missing level", g.first { it.nodeId == "g9" }.title.contains("identity"))
        check("the prompt quotes the entity", g.first { it.nodeId == "g9" }.title.contains("Read more"))

        val childless = fullChain + jn("i9", JourneyTree.Kind.IDENTITY, null, "Someone who reads")
        val c = JourneyTree.gaps(childless)
        check("a childless identity is flagged", c.any { it.nodeId == "i9" })
        check("dangling outranks childless",
            JourneyTree.gaps(dangling + jn("i9", JourneyTree.Kind.IDENTITY, null, "Someone who reads"))
                .first().nodeId == "g9")

        // Cap: fifteen problems is not fifteen prompts.
        val messy = (1..15).map { jn("g$it", JourneyTree.Kind.GOAL, null, "Goal $it") }
        eq("capped at three", JourneyTree.gaps(messy).size, 3)
        eq("cap is configurable", JourneyTree.gaps(messy, 5).size, 5)
        eq("limit zero yields nothing", JourneyTree.gaps(messy, 0).size, 0)
        check("every gap has body copy", JourneyTree.gaps(messy).all { it.body.isNotBlank() })
    }
    println()

    println("Reordering stays within a sibling group")
    run {
        val many = listOf(
            jn("i1", JourneyTree.Kind.IDENTITY),
            jn("g1", JourneyTree.Kind.GOAL, "i1"),
            jn("g2", JourneyTree.Kind.GOAL, "i1"),
            jn("g3", JourneyTree.Kind.GOAL, "i1"),
        )
        val open = setOf("identity:i1")
        val rows = JourneyTree.build(many, open).linked
        check("sibling move allowed", JourneyTree.canMove(rows, 1, 3))
        check("moving onto itself is a no-op", !JourneyTree.canMove(rows, 2, 2))
        check("cross-kind move refused", !JourneyTree.canMove(rows, 0, 1))
        check("out-of-range refused", !JourneyTree.canMove(rows, 1, 99))
        check("negative refused", !JourneyTree.canMove(rows, -1, 1))

        val moved = JourneyTree.move(many, rows, 1, 3)
        eq("g1 moved to the end",
            JourneyTree.build(moved, open).linked.map { it.node.id }, listOf("i1", "g2", "g3", "g1"))
        eq("nothing was lost", moved.size, many.size)
        val back = JourneyTree.move(moved, JourneyTree.build(moved, open).linked, 3, 1)
        eq("the move is reversible", back.map { it.id }, many.map { it.id })
        eq("a refused move changes nothing", JourneyTree.move(many, rows, 0, 1), many)

        // Two goals under different identities are not siblings.
        val split = listOf(
            jn("i1", JourneyTree.Kind.IDENTITY), jn("i2", JourneyTree.Kind.IDENTITY),
            jn("g1", JourneyTree.Kind.GOAL, "i1"), jn("g2", JourneyTree.Kind.GOAL, "i2"),
        )
        val srows = JourneyTree.build(split, setOf("identity:i1", "identity:i2")).linked
        val a = srows.indexOfFirst { it.node.id == "g1" }
        val b = srows.indexOfFirst { it.node.id == "g2" }
        check("re-parenting is not a drag", !JourneyTree.canMove(srows, a, b))
    }
    println()

    println("Connection labels")
    run {
        val t = JourneyTree.build(fullChain, allOpen)
        eq("identity label", JourneyTree.connectionLabel(t.linked[0]), "1 goal \u00b7 1 habit")
        eq("system label", JourneyTree.connectionLabel(t.linked[2]), "1 habit \u00b7 1 habit")
        eq("habit label is empty", JourneyTree.connectionLabel(t.linked[3]), "")
        val two = listOf(
            jn("i1", JourneyTree.Kind.IDENTITY),
            jn("g1", JourneyTree.Kind.GOAL, "i1"), jn("g2", JourneyTree.Kind.GOAL, "i1"),
        )
        eq("plural goals", JourneyTree.connectionLabel(JourneyTree.build(two, setOf("identity:i1")).linked[0]), "2 goals")
        eq("empty identity has no label",
            JourneyTree.connectionLabel(JourneyTree.build(listOf(jn("i", JourneyTree.Kind.IDENTITY))).linked[0]), "")
    }
    println()

    // ================================================== Phase 4: navigation
    println("Navigation: four tabs, settings is not one of them")
    run {
        eq("four primary destinations", Navigation.tabCount, 4)
        eq("order", Navigation.tabs.map { it.key },
            listOf("today", "journey", "insights", "studio"))
        check("settings is a route, not a tab", Navigation.tabOf("settings") == null)
        check("settings still resolves",
            Navigation.destinationOf("settings") ==
                Navigation.Destination.ToRoute(Navigation.Route.SETTINGS))
        eq("indices match position", Navigation.tabs.map { it.index }, listOf(0, 1, 2, 3))
        check("keys are unique", Navigation.tabs.map { it.key }.toSet().size == 4)
        check("every tab is described", Navigation.tabs.all { it.label.isNotBlank() && it.detail.isNotBlank() })
    }
    println()

    println("Navigation: keys from the wild still land somewhere sensible")
    run {
        // A notification scheduled before the merge, or a pinned shortcut,
        // carries the old key for the life of the install.
        eq("coach becomes studio", Navigation.tabOf("coach"), Navigation.Tab.STUDIO)
        eq("blueprint becomes studio", Navigation.tabOf("blueprint"), Navigation.Tab.STUDIO)
        eq("engine becomes studio", Navigation.tabOf("engine"), Navigation.Tab.STUDIO)
        eq("case is ignored", Navigation.tabOf("JOURNEY"), Navigation.Tab.JOURNEY)
        eq("whitespace is trimmed", Navigation.tabOf("  today "), Navigation.Tab.TODAY)
        check("nonsense goes nowhere", Navigation.destinationOf("wat") == null)
        check("null goes nowhere", Navigation.destinationOf(null) == null)
        check("empty goes nowhere", Navigation.destinationOf("") == null)
        eq("index is clamped, never thrown", Navigation.tabAt(99), Navigation.Tab.STUDIO)
        eq("negative index is clamped", Navigation.tabAt(-5), Navigation.Tab.TODAY)
    }
    println()

    println("Navigation: migrating a stored five-tab index")
    run {
        eq("today survives", Navigation.migrateTabIndex(0), Navigation.Tab.TODAY)
        eq("journey survives", Navigation.migrateTabIndex(1), Navigation.Tab.JOURNEY)
        eq("insights survives", Navigation.migrateTabIndex(2), Navigation.Tab.INSIGHTS)
        eq("coach becomes studio", Navigation.migrateTabIndex(3), Navigation.Tab.STUDIO)
        // The user who set Settings as their start destination did not ask
        // for Studio; they get the safe default instead.
        eq("settings falls back to today", Navigation.migrateTabIndex(4), Navigation.Tab.TODAY)
        eq("garbage falls back to today", Navigation.migrateTabIndex(77), Navigation.Tab.TODAY)
    }
    println()

    println("Navigation: adaptive placement")
    run {
        eq("phone portrait gets a bottom bar",
            Navigation.placementFor(392, 850), Navigation.NavPlacement.BOTTOM)
        eq("phone landscape gets a rail",
            Navigation.placementFor(850, 392), Navigation.NavPlacement.WIDE_RAIL)
        // A narrow but short window - a small phone on its side, or a
        // freeform window - still gets the rail, because a bottom bar plus
        // the gesture inset would eat the content.
        eq("short and narrow gets a rail",
            Navigation.placementFor(560, 400), Navigation.NavPlacement.RAIL)
        eq("unfolded foldable gets a rail",
            Navigation.placementFor(700, 900), Navigation.NavPlacement.RAIL)
        eq("tablet gets a wide rail",
            Navigation.placementFor(1024, 1366), Navigation.NavPlacement.WIDE_RAIL)
        check("two panes only when expanded", !Navigation.twoPane(700) && Navigation.twoPane(900))
        eq("no rail width for a bottom bar", Navigation.railWidth(Navigation.NavPlacement.BOTTOM), null)
        check("rails are at least a touch target wide",
            Navigation.railWidth(Navigation.NavPlacement.RAIL)!! >= Accessibility.MIN_TARGET_DP)
        eq("narrow content is not stretched", Navigation.contentWidth(392), 392)
        eq("wide content is capped", Navigation.contentWidth(1366), Navigation.MAX_CONTENT_WIDTH)
        check("width classes are ordered",
            Navigation.widthClass(500) == Navigation.WidthClass.COMPACT &&
            Navigation.widthClass(700) == Navigation.WidthClass.MEDIUM &&
            Navigation.widthClass(1000) == Navigation.WidthClass.EXPANDED)
    }
    println()

    println("Navigation: labels never remove information")
    run {
        val never = Navigation.TabLabels.NEVER
        check("icons-only hides the label",
            !Navigation.showsLabel(never, false, Navigation.NavPlacement.BOTTOM))
        check("a wide rail labels anyway",
            Navigation.showsLabel(never, false, Navigation.NavPlacement.WIDE_RAIL))
        check("selected-only labels the selection",
            Navigation.showsLabel(Navigation.TabLabels.SELECTED_ONLY, true, Navigation.NavPlacement.BOTTOM))
        check("selected-only hides the rest",
            !Navigation.showsLabel(Navigation.TabLabels.SELECTED_ONLY, false, Navigation.NavPlacement.BOTTOM))
        // Whatever the visual setting, the screen reader hears the name.
        val spoken = Navigation.describeTab(Navigation.Tab.JOURNEY, selected = false)
        check("spoken label survives icons-only", spoken.contains("Journey"))
        check("spoken position", spoken.contains("tab 2 of 4"))
        check("spoken state", spoken.contains("not selected"))
        check("selected reads as selected",
            Navigation.describeTab(Navigation.Tab.TODAY, true).endsWith("selected"))
        eq("three label styles", Navigation.tabLabelOptions.size, 3)
        eq("unknown id falls back to labels", Navigation.tabLabels(9), Navigation.TabLabels.ALWAYS)
    }
    println()

    println("Navigation: shortcuts and gestures")
    run {
        check("every shortcut points somewhere real", Navigation.shortcutsResolve())
        check("at most four shortcuts", Navigation.shortcuts.size <= 4)
        check("shortcut ids are unique",
            Navigation.shortcuts.map { it.id }.toSet().size == Navigation.shortcuts.size)
        val all = Navigation.allGestures
        check("all gestures on by default", all.size == Navigation.Gesture.entries.size)
        check("an enabled gesture fires",
            Navigation.gestureEnabled(Navigation.Gesture.SWIPE_CHECK, all, false))
        check("a disabled gesture does not",
            !Navigation.gestureEnabled(Navigation.Gesture.SWIPE_CHECK, emptySet(), false))
        // Confirmations exist so nothing destructive happens by accident; a
        // swipe that skips a habit without asking would defeat them.
        check("confirmations suppress the destructive gesture",
            !Navigation.gestureEnabled(Navigation.Gesture.SWIPE_SKIP, all, true))
        check("confirmations leave harmless gestures alone",
            Navigation.gestureEnabled(Navigation.Gesture.SWIPE_CHECK, all, true))
        check("every gesture has a button alternative",
            Navigation.Gesture.entries.all { Accessibility.hasAlternative(it.key) })
    }
    println()

    // ====================================================== Phase 4: Studio
    println("Studio: transcript assembly")
    run {
        fun turn(id: String, who: StudioModel.Speaker, at: Long = 0L,
                 state: StudioModel.RunState = StudioModel.RunState.NONE,
                 group: String? = null, actions: List<String> = emptyList()) =
            StudioModel.Turn(id, who, "text $id", at, state = state, groupId = group, actions = actions)

        val status = StudioModel.Row.Status("Full Control active", "gpt-4o-mini", "Manage", true)

        val empty = StudioModel.rows(status, emptyList(),
            suggestions = listOf("a", "b"), coach = "Keep going")
        eq("status is always first", empty.first().key, "status")
        eq("quick actions second", empty[1].key, "quick")
        check("empty studio suggests", empty.any { it is StudioModel.Row.Suggestions })
        check("empty studio coaches", empty.any { it is StudioModel.Row.Coach })

        val turns = listOf(
            turn("1", StudioModel.Speaker.USER),
            turn("2", StudioModel.Speaker.ASSISTANT),
            turn("3", StudioModel.Speaker.ASSISTANT),
        )
        val full = StudioModel.rows(status, turns, suggestions = listOf("a"), coach = "c")
        check("a live transcript hides the suggestions",
            full.none { it is StudioModel.Row.Suggestions })
        check("and the coach card", full.none { it is StudioModel.Row.Coach })
        val msgs = full.filterIsInstance<StudioModel.Row.Message>()
        eq("all three turns drawn", msgs.size, 3)
        check("first of a run shows the avatar", msgs[0].showAvatar && msgs[1].showAvatar)
        check("a continued run does not repeat it", !msgs[2].showAvatar)
        check("keys are unique", full.map { it.key }.toSet().size == full.size)
    }
    println()

    println("Studio: long histories fold, and date breaks reset the run")
    run {
        val many = (1..60).map {
            StudioModel.Turn("$it", StudioModel.Speaker.USER, "t$it", it.toLong())
        }
        val rows = StudioModel.rows(
            StudioModel.Row.Status("s", "d", "a", false), many, visibleTurns = 10)
        val fold = rows.filterIsInstance<StudioModel.Row.OlderFold>().single()
        eq("fifty hidden", fold.hidden, 50)
        eq("ten shown", rows.filterIsInstance<StudioModel.Row.Message>().size, 10)
        check("the fold sits above the messages",
            rows.indexOf(fold) < rows.indexOfFirst { it is StudioModel.Row.Message })
        check("the newest turn survives the fold",
            rows.filterIsInstance<StudioModel.Row.Message>().last().turn.id == "60")

        // Same speaker either side of midnight: the separator breaks the run,
        // so the first message of the new day is attributed again.
        val across = listOf(
            StudioModel.Turn("a", StudioModel.Speaker.ASSISTANT, "x", 1L),
            StudioModel.Turn("b", StudioModel.Speaker.ASSISTANT, "y", 2L),
        )
        val dated = StudioModel.rows(StudioModel.Row.Status("s", "d", "a", false), across,
            dayLabel = { if (it == 1L) "Monday" else "Tuesday" })
        eq("two date breaks", dated.filterIsInstance<StudioModel.Row.DateBreak>().size, 2)
        check("the run restarts after a break",
            dated.filterIsInstance<StudioModel.Row.Message>().all { it.showAvatar })
    }
    println()

    println("Studio: message actions offer only what is real")
    run {
        val plain = StudioModel.Turn("1", StudioModel.Speaker.ASSISTANT, "hello", 0L)
        eq("a plain reply can only be copied",
            StudioModel.actionsFor(plain), listOf(StudioModel.MessageAction.COPY))

        val ran = plain.copy(state = StudioModel.RunState.DONE, groupId = "g1",
            actions = listOf("create_habit"))
        check("a command that stuck can be undone",
            StudioModel.MessageAction.UNDO in StudioModel.actionsFor(ran))
        check("and explained", StudioModel.MessageAction.EXPLAIN in StudioModel.actionsFor(ran))
        // Retrying a successful assistant turn would run its commands twice.
        check("but not retried", StudioModel.MessageAction.RETRY !in StudioModel.actionsFor(ran))

        val undone = ran.copy(state = StudioModel.RunState.UNDONE)
        check("an undone command cannot be undone again",
            StudioModel.MessageAction.UNDO !in StudioModel.actionsFor(undone))
        val failed = plain.copy(state = StudioModel.RunState.FAILED)
        check("a failure can be retried",
            StudioModel.MessageAction.RETRY in StudioModel.actionsFor(failed))
        val mine = StudioModel.Turn("2", StudioModel.Speaker.USER, "do a thing", 0L)
        check("my own words can be retried",
            StudioModel.MessageAction.RETRY in StudioModel.actionsFor(mine))
        check("no undo without a group",
            StudioModel.MessageAction.UNDO !in
                StudioModel.actionsFor(plain.copy(state = StudioModel.RunState.DONE)))
    }
    println()

    println("Studio: status chips stay quiet unless there is news")
    run {
        val base = StudioModel.Turn("1", StudioModel.Speaker.ASSISTANT, "x", 0L)
        eq("nothing to report", StudioModel.statusChip(base), null)
        eq("a reply that ran nothing says nothing",
            StudioModel.statusChip(base.copy(state = StudioModel.RunState.DONE)), null)
        eq("one step is named",
            StudioModel.statusChip(base.copy(state = StudioModel.RunState.DONE,
                actions = listOf("create_goal"))), "Ran create_goal")
        eq("several are counted",
            StudioModel.statusChip(base.copy(state = StudioModel.RunState.DONE,
                actions = listOf("a", "b", "c"))), "Ran 3 steps")
        // Waiting on a person must not look like waiting on a machine.
        eq("pending names the person",
            StudioModel.statusChip(base.copy(state = StudioModel.RunState.PENDING)), "Waiting for you")
        eq("running says so",
            StudioModel.statusChip(base.copy(state = StudioModel.RunState.RUNNING)), "Running")
        eq("failure has an error role", StudioModel.statusRole(StudioModel.RunState.FAILED), "error")
        eq("silence is neutral", StudioModel.statusRole(StudioModel.RunState.NONE), "neutral")
        check("busy covers both waits",
            base.copy(state = StudioModel.RunState.RUNNING).busy &&
            base.copy(state = StudioModel.RunState.PENDING).busy &&
            !base.busy)
        check("typing while sending", StudioModel.typing(emptyList(), sending = true))
        check("typing while a turn runs",
            StudioModel.typing(listOf(base.copy(state = StudioModel.RunState.RUNNING)), false))
        check("quiet otherwise", !StudioModel.typing(listOf(base), false))
    }
    println()

    println("Studio: composer")
    run {
        check("blank cannot send", !StudioModel.canSend("   ", false))
        check("busy cannot send", !StudioModel.canSend("hello", true))
        check("normal text sends", StudioModel.canSend("hello", false))
        check("over the limit cannot send",
            !StudioModel.canSend("x".repeat(StudioModel.MAX_INPUT + 1), false))
        check("counter hides early", !StudioModel.showCounter(10))
        check("counter appears near the limit", StudioModel.showCounter(StudioModel.MAX_INPUT))
        check("full control placeholder is imperative",
            StudioModel.placeholder(true, true).startsWith("Tell"))
        check("local-only placeholder says so",
            StudioModel.placeholder(false, false).contains("local"))
        check("quick actions are real prompts",
            StudioModel.quickActions.all { it.prompt.length > it.label.length })
        check("quick action ids unique",
            StudioModel.quickActions.map { it.id }.toSet().size == StudioModel.quickActions.size)
    }
    println()

    println("Studio: voice waveform")
    run {
        val w = StudioModel.waveform(listOf(0.5f, 0.8f, 0.2f), 5)
        eq("always the requested bar count", w.size, 5)
        check("no bar collapses to nothing", w.all { it >= StudioModel.MIN_BAR })
        check("no bar overshoots", w.all { it <= 1f })
        eq("no bars requested, none returned", StudioModel.waveform(listOf(1f), 0).size, 0)
        eq("silence still draws a line", StudioModel.waveform(emptyList(), 3).size, 3)
        // Smoothing: a spike must not be reproduced at full height on the
        // first sample, or the waveform strobes.
        val spike = StudioModel.waveform(listOf(1f), 1)
        check("a spike is damped", spike.single() < 1f)
        check("out-of-range input is clamped, not propagated",
            StudioModel.waveform(listOf(9f), 1).single() <= 1f)
    }
    println()

    // ================================================== Phase 4: onboarding
    println("Onboarding: six steps")
    run {
        eq("six, down from eight", OnboardingFlow.stepCount, 6)
        eq("order", OnboardingFlow.steps.map { it.key },
            listOf("welcome", "identity", "goal", "habit", "cue", "preview"))
        check("every step has an illustration",
            OnboardingFlow.steps.all { it.illustration.isNotBlank() })
        check("illustrations are distinct",
            OnboardingFlow.steps.map { it.illustration }.toSet().size == 6)
        check("every step explains itself",
            OnboardingFlow.steps.all { it.subtitle.isNotBlank() })
        eq("clamped low", OnboardingFlow.stepAt(-1), OnboardingFlow.Step.WELCOME)
        eq("clamped high", OnboardingFlow.stepAt(50), OnboardingFlow.Step.PREVIEW)
        eq("last is the preview", OnboardingFlow.steps.last(), OnboardingFlow.Step.PREVIEW)
        check("preview is last", OnboardingFlow.isLast(OnboardingFlow.Step.PREVIEW))
        eq("nothing after the end", OnboardingFlow.next(OnboardingFlow.Step.PREVIEW), null)
        eq("nothing before the start", OnboardingFlow.previous(OnboardingFlow.Step.WELCOME), null)
    }
    println()

    println("Onboarding: validation asks for one thing at a time")
    run {
        val blank = OnboardingFlow.Answers()
        check("welcome never blocks", OnboardingFlow.canAdvance(OnboardingFlow.Step.WELCOME, blank))
        check("identity blocks when blank",
            !OnboardingFlow.canAdvance(OnboardingFlow.Step.IDENTITY, blank))
        check("whitespace is not an answer",
            !OnboardingFlow.canAdvance(OnboardingFlow.Step.IDENTITY,
                blank.copy(identity = "   ")))
        check("identity passes when written",
            OnboardingFlow.canAdvance(OnboardingFlow.Step.IDENTITY,
                blank.copy(identity = "Someone who moves")))
        // The why is the part that makes a goal stick, but demanding it at
        // the door loses more people than it helps.
        check("goal needs a title, not a why",
            OnboardingFlow.canAdvance(OnboardingFlow.Step.GOAL, blank.copy(goal = "Run 5km")))
        check("cue never blocks", OnboardingFlow.canAdvance(OnboardingFlow.Step.CUE, blank))
        check("preview never blocks", OnboardingFlow.canAdvance(OnboardingFlow.Step.PREVIEW, blank))
        check("at most one requirement per step",
            OnboardingFlow.steps.count { OnboardingFlow.requirement(it) != null } == 3)
        val msg = OnboardingFlow.blockedBecause(OnboardingFlow.Step.IDENTITY, blank)
        check("the message is kind", msg != null && msg.contains("later"))
    }
    println()

    println("Onboarding: chrome")
    run {
        eq("welcome invites", OnboardingFlow.nextLabel(OnboardingFlow.Step.WELCOME), "Begin")
        eq("the end commits",
            OnboardingFlow.nextLabel(OnboardingFlow.Step.PREVIEW), "Start my first day")
        check("no back on the first step", !OnboardingFlow.showsBack(OnboardingFlow.Step.WELCOME))
        check("back in the middle", OnboardingFlow.showsBack(OnboardingFlow.Step.GOAL))
        check("skip is offered early", OnboardingFlow.showsSkip(OnboardingFlow.Step.WELCOME))
        // The progress line is filled to what is done, not to where you are.
        check("progress starts empty",
            abs(OnboardingFlow.progress(OnboardingFlow.Step.WELCOME)) < 0.001f)
        check("progress ends full",
            abs(OnboardingFlow.progress(OnboardingFlow.Step.PREVIEW) - 1f) < 0.001f)
        check("progress only rises",
            OnboardingFlow.steps.map { OnboardingFlow.progress(it) }.zipWithNext()
                .all { it.first < it.second })
        eq("percent at the end", OnboardingFlow.progressPercent(OnboardingFlow.Step.PREVIEW), 100)
        val spoken = OnboardingFlow.describeProgress(OnboardingFlow.Step.GOAL)
        check("position is spoken", spoken.contains("Step 3 of 6"))
    }
    println()

    println("Onboarding: derived values and examples")
    run {
        val a = OnboardingFlow.Answers(goal = "Run 5km")
        eq("system named from the goal", a.systemName(), "My Run 5km routine")
        eq("an explicit name wins", a.copy(system = "Morning loop").systemName(), "Morning loop")
        eq("no goal, generic name", OnboardingFlow.Answers().systemName(), "My routine")
        check("identity examples are present tense",
            OnboardingFlow.identityExamples.none { it.contains("want") || it.contains("will") })
        check("several examples to cycle", OnboardingFlow.identityExamples.size >= 3)
        check("goal examples carry a why",
            OnboardingFlow.goalExamples.all { it.second.isNotBlank() })
        val tiny = OnboardingFlow.tinyStarts("Walk 10 minutes")
        eq("three tiny starts", tiny.size, 3)
        check("they name the habit", tiny.all { it.contains("walk 10 minutes") })
        check("an empty habit still generates something",
            OnboardingFlow.tinyStarts("").all { it.isNotBlank() })
    }
    println()

    println("Onboarding: skipping leaves something to look at")
    run {
        val demo = OnboardingFlow.demoAnswers()
        check("the demo can finish the flow",
            OnboardingFlow.steps.all { OnboardingFlow.canAdvance(it, demo) })
        check("the demo does not nag", !demo.reminder)
        check("the demo has a cue time", demo.cueTime.isNotBlank())
        val p = OnboardingFlow.preview(demo)
        eq("preview shows their habit", p.habitTitle, demo.habit)
        check("preview detail includes the time", p.habitDetail.contains("07:30"))
        val bare = OnboardingFlow.preview(OnboardingFlow.Answers(habit = "Read"))
        eq("a habit with no time still previews", bare.habitDetail, "Whenever you can")
        check("an empty preview falls back rather than blanking",
            OnboardingFlow.preview(OnboardingFlow.Answers()).identity.isNotBlank())
    }
    println()

    println("Onboarding: the permission ask is late and earned")
    run {
        val wants = OnboardingFlow.Answers(cueTime = "07:00", reminder = true)
        check("not on welcome",
            !OnboardingFlow.asksNotificationPermission(OnboardingFlow.Step.WELCOME, wants))
        check("at the cue step",
            OnboardingFlow.asksNotificationPermission(OnboardingFlow.Step.CUE, wants))
        check("not if they declined the reminder",
            !OnboardingFlow.asksNotificationPermission(OnboardingFlow.Step.CUE,
                wants.copy(reminder = false)))
        check("not without a time to remind at",
            !OnboardingFlow.asksNotificationPermission(OnboardingFlow.Step.CUE,
                wants.copy(cueTime = "")))
    }
    println()

    // ====================================================== Phase 4: widget
    println("Widget: picking a size for whatever the launcher hands us")
    run {
        eq("2x2", WidgetLayout.sizeFor(120, 120), WidgetLayout.Size.SMALL)
        eq("4x2", WidgetLayout.sizeFor(260, 120), WidgetLayout.Size.MEDIUM)
        eq("5x2", WidgetLayout.sizeFor(330, 120), WidgetLayout.Size.WIDE)
        eq("4x4", WidgetLayout.sizeFor(260, 260), WidgetLayout.Size.LARGE)
        // The clipping case: wide but only two rows tall. Must not pick Large.
        eq("wide and short is not large",
            WidgetLayout.sizeFor(400, 130), WidgetLayout.Size.WIDE)
        eq("tiny cells degrade rather than crash",
            WidgetLayout.sizeFor(0, 0), WidgetLayout.Size.SMALL)
        eq("absurd sizes still resolve",
            WidgetLayout.sizeFor(4000, 4000), WidgetLayout.Size.LARGE)
        check("small draws a ring", WidgetLayout.usesRing(WidgetLayout.Size.SMALL))
        check("wide draws a bar instead", !WidgetLayout.usesRing(WidgetLayout.Size.WIDE))
        eq("small lists nothing", WidgetLayout.habitRows(WidgetLayout.Size.SMALL, 9), 0)
        eq("medium lists one", WidgetLayout.habitRows(WidgetLayout.Size.MEDIUM, 9), 1)
        eq("large is capped", WidgetLayout.habitRows(WidgetLayout.Size.LARGE, 40),
            WidgetLayout.MAX_LARGE_ROWS)
        eq("never more rows than habits", WidgetLayout.habitRows(WidgetLayout.Size.LARGE, 2), 2)
        check("small has nothing to tap", !WidgetLayout.interactive(WidgetLayout.Size.SMALL))
    }
    println()

    println("Widget: wording of the four states")
    run {
        val morning = WidgetLayout.TimeOfDay.MORNING
        val nothing = WidgetLayout.content(0, 0, null, morning)
        eq("empty day", nothing.headline, "Nothing scheduled")
        check("empty day offers no button", !nothing.showsAction)

        // "0 of 5" first thing in the morning is a scolding.
        val fresh = WidgetLayout.content(0, 5, "Walk", morning)
        eq("a fresh morning is a plan, not a deficit", fresh.headline, "5 to do")
        check("and offers the next one", fresh.showsAction && fresh.subhead == "Walk")
        eq("the same day at night reads differently",
            WidgetLayout.content(0, 5, "Walk", WidgetLayout.TimeOfDay.NIGHT).headline,
            "5 unfinished")

        val part = WidgetLayout.content(2, 5, "Read", morning)
        eq("partial progress counts up", part.headline, "2 of 5 done")
        eq("percent rounds", part.percent, 40)

        val done = WidgetLayout.content(5, 5, null, morning)
        eq("a finished day says so", done.headline, "Day complete")
        eq("and counts the votes", done.subhead, "5 votes cast")
        eq("one vote is singular", WidgetLayout.content(1, 1, null, morning).subhead, "One vote cast")
        eq("finished is 100", done.percent, 100)
        check("finished offers nothing to tap", !done.showsAction)
        check("over-completion does not exceed 100",
            WidgetLayout.content(7, 5, null, morning).percent == 100)
    }
    println()

    println("Widget: time buckets, description and redraw")
    run {
        eq("7am", WidgetLayout.timeOfDay(7), WidgetLayout.TimeOfDay.MORNING)
        eq("1pm", WidgetLayout.timeOfDay(13), WidgetLayout.TimeOfDay.AFTERNOON)
        eq("7pm", WidgetLayout.timeOfDay(19), WidgetLayout.TimeOfDay.EVENING)
        eq("2am", WidgetLayout.timeOfDay(2), WidgetLayout.TimeOfDay.NIGHT)
        eq("out of range is clamped, not crashed",
            WidgetLayout.timeOfDay(99), WidgetLayout.TimeOfDay.NIGHT)
        val c = WidgetLayout.content(2, 5, "Read", WidgetLayout.TimeOfDay.MORNING)
        val said = WidgetLayout.describe(c)
        check("the app is named", said.startsWith("SuperFlow"))
        check("everything visible is spoken",
            said.contains(c.headline) && said.contains(c.subhead))
        // Redraws cost an IPC round trip and a database read.
        check("identical content within the window is dropped",
            !WidgetLayout.shouldRedraw(c, c, 1000L))
        check("changed content redraws",
            WidgetLayout.shouldRedraw(c, WidgetLayout.content(3, 5, "Read",
                WidgetLayout.TimeOfDay.MORNING), 1000L))
        check("the safety net still fires",
            WidgetLayout.shouldRedraw(c, c, WidgetLayout.PERIODIC_MS))
        check("first draw always happens", WidgetLayout.shouldRedraw(null, c, 0L))
    }
    println()

    // ======================================================= Phase 4: sound
    println("Sound: silence is the default and wins every tie")
    run {
        val on = SoundDesign.allCues
        val cue = SoundDesign.Cue.CHECK_IN
        check("off means off",
            SoundDesign.gainFor(cue, false, on, 1f, quiet = false) == null)
        check("an unchecked cue is silent",
            SoundDesign.gainFor(cue, true, emptySet(), 1f, quiet = false) == null)
        check("quiet hours override everything",
            SoundDesign.gainFor(cue, true, on, 1f, quiet = true) == null)
        check("a muted device is respected",
            SoundDesign.gainFor(cue, true, on, 1f, quiet = false, muted = true) == null)
        check("zero volume is silence",
            SoundDesign.gainFor(cue, true, on, 0f, quiet = false) == null)
        check("inaudible is not worth waking the audio path for",
            SoundDesign.gainFor(cue, true, on, 0.01f, quiet = false) == null)
        val g = SoundDesign.gainFor(cue, true, on, 1f, quiet = false)
        check("it does play when asked", g != null)
        check("never the loudest thing on the device", g!! <= SoundDesign.MAX_GAIN)
        // A whoosh fires dozens of times a day; a bell fires once.
        check("the swipe whoosh is quieter than the day bell",
            SoundDesign.relativeGain(SoundDesign.Cue.SWIPE_DISMISS) <
                SoundDesign.relativeGain(SoundDesign.Cue.DAY_COMPLETE))
        check("volume scales the result",
            SoundDesign.gainFor(cue, true, on, 0.5f, false)!! <
                SoundDesign.gainFor(cue, true, on, 1f, false)!!)
        check("four cues", SoundDesign.cues.size == 4)
        check("every cue pairs with a haptic",
            SoundDesign.cues.all { it.haptic.isNotBlank() })
        check("preview runs quietest first",
            SoundDesign.previewOrder().first() == SoundDesign.Cue.SWIPE_DISMISS)
    }
    println()

    println("Sound: quiet hours wrap midnight")
    run {
        val from = SoundDesign.parseTime("22:00")!!
        val to = SoundDesign.parseTime("07:00")!!
        check("late evening is quiet", SoundDesign.inQuietHours(23 * 60, from, to))
        check("the small hours are quiet", SoundDesign.inQuietHours(2 * 60, from, to))
        check("just before the window is not", !SoundDesign.inQuietHours(21 * 60 + 59, from, to))
        check("the start is inclusive", SoundDesign.inQuietHours(22 * 60, from, to))
        check("the end is exclusive", !SoundDesign.inQuietHours(7 * 60, from, to))
        check("the afternoon is loud", !SoundDesign.inQuietHours(14 * 60, from, to))
        // A window that does not wrap.
        val day = SoundDesign.parseTime("09:00")!! to SoundDesign.parseTime("17:00")!!
        check("a daytime window works too", SoundDesign.inQuietHours(12 * 60, day.first, day.second))
        check("and excludes the evening", !SoundDesign.inQuietHours(20 * 60, day.first, day.second))
        // Identical ends mean no quiet hours, not permanent silence.
        check("an empty window silences nothing", !SoundDesign.inQuietHours(3 * 60, 0, 0))
        eq("bad time parses to null", SoundDesign.parseTime("25:00"), null)
        eq("nonsense parses to null", SoundDesign.parseTime("morning"), null)
        eq("null parses to null", SoundDesign.parseTime(null), null)
        eq("midnight parses", SoundDesign.parseTime("00:00"), 0)
    }
    println()

    // =============================================== Phase 4: accessibility
    println("Accessibility: text scaling")
    run {
        check("the designed range is honoured",
            Accessibility.supported(1f) && Accessibility.supported(2f) &&
                !Accessibility.supported(2.5f))
        check("no reflow at normal size", !Accessibility.reflow(1f))
        check("reflow at large text", Accessibility.reflow(1.5f))
        check("more lines at bigger text",
            Accessibility.titleMaxLines(2f) > Accessibility.titleMaxLines(1f))
        check("lines never decrease with scale",
            listOf(0.85f, 1f, 1.2f, 1.6f, 2f).map { Accessibility.titleMaxLines(it) }
                .zipWithNext().all { it.first <= it.second })
        // Rows grow, but padding does not multiply.
        val base = 56
        val big = Accessibility.rowHeight(base, 2f)
        check("a row grows with text", big > base)
        check("but not by the full multiple", big < base * 2)
        check("a row never drops below a touch target",
            Accessibility.rowHeight(20, 0.85f) >= Accessibility.MIN_TARGET_DP)
    }
    println()

    println("Accessibility: touch targets")
    run {
        eq("the floor is 48", Accessibility.MIN_TARGET_DP, 48)
        check("a 48dp target passes", Accessibility.targetOk(48, 48))
        check("a 24dp icon fails on its own", !Accessibility.targetOk(24, 24))
        eq("and needs 12dp of padding each side", Accessibility.expansionFor(24), 12)
        eq("an odd gap rounds up", Accessibility.expansionFor(23), 13)
        eq("a big enough target needs nothing", Accessibility.expansionFor(64), 0)
        check("expansion always suffices",
            (1..48).all { 2 * Accessibility.expansionFor(it) + it >= Accessibility.MIN_TARGET_DP })
        // Density's smallest row must still be legal.
        check("compact density stays legal",
            Density.metrics(Density.COMPACT).listItemHeight >= Accessibility.MIN_TARGET_DP)
    }
    println()

    println("Accessibility: colour is never the only signal")
    run {
        eq("four modes", Accessibility.colorVisionOptions.size, 4)
        eq("unknown id is standard", Accessibility.colorVision(99), Accessibility.ColorVision.STANDARD)
        eq("standard is green and red",
            Accessibility.stateHue("done", Accessibility.ColorVision.STANDARD) to
                Accessibility.stateHue("missed", Accessibility.ColorVision.STANDARD),
            "green" to "red")
        // The failure this exists for: one man in twelve.
        check("green-blind mode drops green",
            Accessibility.stateHue("done", Accessibility.ColorVision.DEUTERANOPIA) != "green")
        check("and drops red",
            Accessibility.stateHue("missed", Accessibility.ColorVision.DEUTERANOPIA) != "red")
        check("done and missed differ in every mode",
            Accessibility.ColorVision.entries.all {
                Accessibility.stateHue("done", it) != Accessibility.stateHue("missed", it)
            })
        // The real fix: a mark, so a monochrome screenshot still reads.
        check("every state carries a glyph",
            listOf("done", "missed", "skipped", "pending")
                .all { Accessibility.glyphFor(it) != "none" })
        check("glyphs are distinct",
            listOf("done", "missed", "skipped", "pending")
                .map { Accessibility.glyphFor(it) }.toSet().size == 4)
    }
    println()

    println("Accessibility: announcements carry the consequence")
    run {
        eq("a mid-day check-in reports progress",
            Accessibility.announceCheckIn("Walk", 2, 5), "Walk checked in. 2 of 5 done.")
        // The one that matters: a blind user should not have to go counting.
        eq("the last check-in says the day is done",
            Accessibility.announceCheckIn("Walk", 5, 5), "Walk checked in. Day complete.")
        eq("an unscheduled check-in stays short",
            Accessibility.announceCheckIn("Walk", 0, 0), "Walk checked in.")
        check("skipping explains itself",
            Accessibility.announceSkip("Walk").contains("not count against"))
        check("undo is short", Accessibility.announceUndo("Walk") == "Walk unchecked.")
        check("an empty screen says it is empty",
            Accessibility.announceLoaded("Today", 0).contains("Nothing here"))
        check("one item is singular", Accessibility.announceLoaded("Today", 1).contains("One item"))
        check("many are counted", Accessibility.announceLoaded("Today", 7).contains("7 items"))
        check("section headers are headings", Accessibility.isHeading("sectionHeader"))
        check("cards are not", !Accessibility.isHeading("card"))
    }
    println()

    println("Accessibility: motor and motion")
    run {
        check("every swipe has a button",
            Accessibility.gestureAlternatives.values.all { it.isNotBlank() })
        check("undo lasts longer than Material's default",
            Accessibility.UNDO_MS > 3000L)
        check("a screen reader gets longer still",
            Accessibility.undoTimeout(true) > Accessibility.undoTimeout(false))
        // The OS setting wins over ours. Asking twice and honouring the
        // wrong answer is the failure the setting exists to prevent.
        check("the system setting wins",
            !Accessibility.animates(systemRemovesAnimations = true,
                appMotionEnabled = true, essential = false))
        check("essential motion survives it",
            Accessibility.animates(true, true, essential = true))
        check("the app setting still applies",
            !Accessibility.animates(false, appMotionEnabled = false, essential = false))
        check("otherwise things move", Accessibility.animates(false, true, false))
        eq("bold text bumps the weight", Accessibility.weightFor(400, true), 500)
        eq("and leaves it alone otherwise", Accessibility.weightFor(400, false), 400)
        check("weight never exceeds the scale", Accessibility.weightFor(900, true) <= 900)
    }

    // =============================================== Phase 4: tone synthesis
    println("Synth: every cue renders to a clean, bounded buffer")
    run {
        val T = com.superflow.design.ToneSynth
        // A fixed pseudo-random source: noise must not make the test flaky,
        // but it must still exercise the noise path.
        var seed = 12345
        fun rnd(): Float {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return (seed % 10_000) / 10_000f
        }

        SoundDesign.cues.forEach { cue ->
            val buf = T.render(cue, gain = 1f, random = ::rnd)
            val expected = (cue.durationMs / 1000f * T.SAMPLE_RATE).toInt()
            check("${cue.key} is exactly as long as the design says",
                kotlin.math.abs(buf.size - expected) <= 1)
            check("${cue.key} never clips", T.peak(buf) <= 1f)
            check("${cue.key} is audible", T.peak(buf) > 0.05f)
            // A cue that ends mid-cycle clicks. The taper must have run.
            check("${cue.key} ends in silence",
                kotlin.math.abs(buf.last()) < 0.02f)
            check("${cue.key} starts in silence",
                kotlin.math.abs(buf.first()) < 0.02f)
        }

        // The attack exists, and it rises.
        val d = 0.5f
        val decay = 0.2f
        check("silence before the start", T.envelope(-0.01f, d, decay) == 0f)
        check("silence after the end", T.envelope(d + 0.01f, d, decay) == 0f)
        check("the attack rises",
            T.envelope(0.0005f, d, decay) < T.envelope(T.ATTACK_MS / 1000f, d, decay))
        check("and then decays",
            T.envelope(T.ATTACK_MS / 1000f, d, decay) > T.envelope(0.2f, d, decay))
        check("the tail reaches zero", T.envelope(d, d, decay) < 0.0001f)

        // Gain scales linearly: the pure gain decision lives in SoundDesign
        // and this must not second-guess it.
        val loud = T.peak(T.render(SoundDesign.Cue.CHECK_IN, 1f, random = { 0.5f }))
        val soft = T.peak(T.render(SoundDesign.Cue.CHECK_IN, 0.25f, random = { 0.5f }))
        check("gain scales the buffer", soft < loud)
        check("and scales it about right",
            kotlin.math.abs(soft * 4f - loud) < 0.05f)
        check("zero gain is silence",
            T.peak(T.render(SoundDesign.Cue.CHECK_IN, 0f, random = { 0.5f })) == 0f)

        // PCM conversion must not wrap at full scale — that pop would land
        // on the loudest sample of the loudest cue.
        val pcm = T.toPcm16(floatArrayOf(1f, -1f, 0f))
        eq("three samples become six bytes", pcm.size, 6)
        val first = ((pcm[1].toInt() and 0xFF) shl 8) or (pcm[0].toInt() and 0xFF)
        eq("full positive scale stays positive", first, 32767)
        check("out-of-range input is clamped, not wrapped",
            T.toPcm16(floatArrayOf(4f)).let {
                (((it[1].toInt() and 0xFF) shl 8) or (it[0].toInt() and 0xFF)) == 32767
            })

        // Pitch set: no cue may contain a minor second against another, or
        // two cues overlapping would beat.
        val pitches = SoundDesign.cues
            .flatMap { T.voiceFor(it).partials }
            .map { it.hz }
            .distinct()
            .sorted()
        check("no two partials are a beating interval apart",
            pitches.zipWithNext().all { (a, b) -> b / a > 1.05f || b == a })

        check("the bell rings longest",
            T.voiceFor(SoundDesign.Cue.DAY_COMPLETE).decay >
                T.voiceFor(SoundDesign.Cue.CHECK_IN).decay)
        check("the whoosh is mostly noise",
            T.voiceFor(SoundDesign.Cue.SWIPE_DISMISS).noise > 0.5f)
        check("the chime is not",
            T.voiceFor(SoundDesign.Cue.CHECK_IN).noise == 0f)
    }
    println()

    println("Journey tree: the parent chain cannot cycle")
    run {
        // Rank strictly decreases going up, so no set of links can make the
        // walk revisit a node. The cases that would hang a naive walker:
        fun N(id: String, kind: JourneyTree.Kind, parentId: String?, title: String) =
            JourneyTree.Node(id, kind, parentId, title)
        val kinds = listOf(
            JourneyTree.Kind.IDENTITY, JourneyTree.Kind.GOAL,
            JourneyTree.Kind.SYSTEM, JourneyTree.Kind.HABIT,
        )

        // Self-link at every level.
        kinds.forEach { kind ->
            val self = listOf(N("x", kind, "x", kind.label))
            val tree = JourneyTree.build(self, setOf(kind.key + ":x"))
            eq("a self-link at ${kind.key} still yields one row", tree.rows.size, 1)
            check("and it is not treated as its own parent",
                tree.rows.first().node.parentId == "x" &&
                    tree.rows.first().depth == 0)
        }

        // Two nodes of the same kind pointing at each other. Neither link
        // resolves, because a parent must be one rank above.
        val mutual = listOf(
            N("a", JourneyTree.Kind.GOAL, "b", "A"),
            N("b", JourneyTree.Kind.GOAL, "a", "B"),
        )
        val mutualTree = JourneyTree.build(mutual)
        eq("mutual same-rank links produce two unlinked roots",
            mutualTree.unlinked.size, 2)
        eq("and no linked rows", mutualTree.linked.size, 0)
        eq("summary counts both as unlinked", mutualTree.summary.unlinked, 2)

        // A habit pointing up two levels: a real import case. The link is
        // dropped rather than drawn as a jump with a hole in it.
        val skipped = listOf(
            N("i", JourneyTree.Kind.IDENTITY, null, "I am someone who moves"),
            N("h", JourneyTree.Kind.HABIT, "i", "Walk"),
        )
        val skippedTree = JourneyTree.build(skipped, setOf("identity:i"))
        eq("a habit linked straight to an identity is unlinked",
            skippedTree.unlinked.size, 1)
        eq("and the identity has no children",
            skippedTree.linked.first().childCount, 0)
        eq("deepest chain counts only what really connects",
            skippedTree.summary.deepestChain, 1)

        // A long legitimate chain still resolves and terminates.
        val full = listOf(
            N("i", JourneyTree.Kind.IDENTITY, null, "I"),
            N("g", JourneyTree.Kind.GOAL, "i", "G"),
            N("s", JourneyTree.Kind.SYSTEM, "g", "S"),
            N("h", JourneyTree.Kind.HABIT, "s", "H"),
        )
        val open = setOf("identity:i", "goal:g", "system:s")
        val fullTree = JourneyTree.build(full, open)
        eq("a complete chain is four rows deep", fullTree.linked.size, 4)
        eq("and the habit sits at depth three", fullTree.linked.last().depth, 3)
        eq("deepest chain is four", fullTree.summary.deepestChain, 4)
        eq("revealing the habit opens all three ancestors",
            JourneyTree.revealPath(full, JourneyTree.Kind.HABIT, "h").size, 3)
    }
    println()

    println("Rendering: which toolkit draws each screen")
    run {
        // Studio has no View version, so it must never be VIEWS.
        eq("studio is compose-only", Rendering.studio, Rendering.Renderer.COMPOSE)
        eq("onboarding too", Rendering.onboarding, Rendering.Renderer.COMPOSE)

        // Every tab resolves; a new tab added without a renderer would not
        // compile, but a tab pointed at the wrong field would.
        Navigation.tabs.forEach { tab ->
            check("${tab.key} has a renderer", Rendering.rendererFor(tab) != null)
        }
        eq("studio routes to the studio field",
            Rendering.rendererFor(Navigation.Tab.STUDIO), Rendering.studio)
        eq("today routes to the today field",
            Rendering.rendererFor(Navigation.Tab.TODAY), Rendering.today)
        eq("journey routes to the journey field",
            Rendering.rendererFor(Navigation.Tab.JOURNEY), Rendering.journey)
        eq("insights routes to the insights field",
            Rendering.rendererFor(Navigation.Tab.INSIGHTS), Rendering.insights)

        // The dual list must describe reality, or it is worse than nothing.
        check("studio is not listed as dual-implemented",
            Navigation.Tab.STUDIO !in Rendering.dualImplemented)
        // Alpha3 M2: every primary screen is on Compose, so the list is
        // empty and the migration is complete. If this ever regresses - a
        // tab flipped back to Views - both assertions fail loudly here.
        eq("no screens remain dual-implemented",
            Rendering.dualImplemented.size, 0)
        eq("no duplicates in the dual list",
            Rendering.dualImplemented.distinct().size, Rendering.dualImplemented.size)

        eq("the migration is finished", Rendering.migrationComplete, true)
        check("and it finishes exactly when nothing is dual-implemented",
            Rendering.migrationComplete == Rendering.dualImplemented.isEmpty())
    }
    println()

    // ================================================ Phase 5: icon variants
    println("App icon: exactly one launcher alias at a time")
    run {
        eq("three variants", IconVariants.all.size, 3)
        eq("ordinals match Prefs", IconVariants.Variant.MONO.id, Prefs.ICON_MONO)
        eq("unknown ids fall back", IconVariants.variantFor(99),
            IconVariants.Variant.DEFAULT)
        eq("negatives too", IconVariants.variantFor(-1), IconVariants.Variant.DEFAULT)

        eq("the manifest ships one alias enabled",
            IconVariants.all.count { it.enabledByDefault }, 1)
        check("and it is the default one",
            IconVariants.Variant.DEFAULT.enabledByDefault)

        IconVariants.all.forEach { target ->
            val plan = IconVariants.transition(target)
            eq("every alias is addressed for ${target.key}", plan.size, 3)
            eq("exactly one is enabled for ${target.key}",
                plan.count { it.second }, 1)
            eq("and it is the target for ${target.key}",
                plan.first { it.second }.first, target.alias)
            // Ordering is load-bearing: disabling the last enabled launcher
            // alias first can drop the app from the launcher permanently.
            check("the enable comes first for ${target.key}", plan.first().second)
        }

        eq("aliases are distinct", IconVariants.all.map { it.alias }.distinct().size, 3)
        check("aliases are manifest-relative",
            IconVariants.all.all { it.alias.startsWith(".") })
        eq("keys are distinct", IconVariants.all.map { it.key }.distinct().size, 3)

        check("no change needed for the same variant",
            !IconVariants.needsChange(
                IconVariants.Variant.MONO, IconVariants.Variant.MONO))
        check("change needed otherwise",
            IconVariants.needsChange(
                IconVariants.Variant.DEFAULT, IconVariants.Variant.MONO))

        // The warning is the only place the user learns their home-screen
        // placement is about to be lost, so it has to actually say so.
        check("the warning mentions the home screen",
            IconVariants.PLACEMENT_WARNING.contains("home screen"))

        eq("the picker offers every variant",
            Catalog.appIcons.size, IconVariants.all.size)
        eq("and agrees on labels",
            Catalog.appIcons.map { it.label }, IconVariants.all.map { it.label })
        eq("and on ids", Catalog.appIcons.map { it.id }, IconVariants.all.map { it.id })
    }
    println()
    println()
    println()
    println("passed=$pass failed=$fail")
    if (fail > 0) kotlin.system.exitProcess(1)
}
