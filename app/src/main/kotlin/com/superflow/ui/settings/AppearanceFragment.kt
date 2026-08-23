package com.superflow.ui.settings

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.superflow.R
import com.superflow.SuperFlowApp
import com.superflow.data.Prefs
import com.superflow.design.Catalog
import com.superflow.design.Choice
import com.superflow.design.Contrast
import com.superflow.design.Haptics
import com.superflow.design.IconVariants
import com.superflow.design.Navigation
import com.superflow.design.ThemeSelection
import com.superflow.ui.common.SfHaptics
import com.superflow.ui.common.SfTheme
import com.superflow.ui.common.dp
import com.superflow.ui.common.snack
import com.superflow.ui.common.themeColor
import com.superflow.ui.common.visible
import com.google.android.material.R as MR

/**
 * Appearance & Experience settings.
 *
 * Every control here changes something the user can see immediately, so the
 * screen re-creates itself on change rather than showing an "apply" button:
 * theme attributes resolve at inflation, so the only honest way to preview a
 * palette is to actually be wearing it.
 *
 * The option lists all come from [Catalog] so this screen and any other
 * surface that offers the same choices cannot disagree.
 */
class AppearanceFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout

    /**
     * The appearance revision this view tree was built against. If a change
     * lands, the Activity is recreated so the new theme actually applies.
     */
    private var builtAtRevision = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs.get(requireContext())
        builtAtRevision = prefs.appearanceRevision

        view.findViewById<TextView>(R.id.screen_title).text =
            getString(R.string.appearance_experience)
        view.findViewById<TextView>(R.id.screen_subtitle).text =
            "Changes apply as you make them."

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.header)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = bars.top + v.context.resources.getDimensionPixelSize(R.dimen.space_m)
            )
            insets
        }

        val list = view.findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(requireContext())
        container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }
        list.adapter = SingleViewAdapter(container)
        render()
    }

    override fun onResume() {
        super.onResume()
        // Another surface may have changed the theme while this screen was
        // backgrounded.
        if (::prefs.isInitialized && prefs.appearanceRevision != builtAtRevision) {
            requireActivity().recreate()
        }
    }

    /**
     * Applies an appearance change and restarts the Activity so it takes
     * effect. Recreate is the supported way to change a theme; retinting a
     * live hierarchy misses anything already measured.
     */
    private fun applyAndRestart(change: () -> Unit) {
        change()
        SfHaptics.perform(requireView(), Haptics.SELECT, prefs)
        requireActivity().recreate()
    }

    private fun render() {
        container.removeAllViews()
        container.addView(previewCard())

        /* ------------------------------------------------------- theme mode */

        container.addView(section(getString(R.string.appearance)))

        val themeCard = layoutInflater.inflate(R.layout.item_theme_picker, container, false)
        val themeGroup = themeCard.findViewById<MaterialButtonToggleGroup>(R.id.theme_group)
        themeGroup.check(
            when (prefs.themeMode) {
                Prefs.THEME_LIGHT -> R.id.theme_light
                Prefs.THEME_DARK -> R.id.theme_dark
                else -> R.id.theme_system
            }
        )
        themeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.theme_light -> Prefs.THEME_LIGHT
                R.id.theme_dark -> Prefs.THEME_DARK
                else -> Prefs.THEME_SYSTEM
            }
            if (mode == prefs.themeMode) return@addOnButtonCheckedListener
            prefs.themeMode = mode
            SfHaptics.perform(requireView(), Haptics.SELECT, prefs)
            // setDefaultNightMode recreates the Activity itself.
            SuperFlowApp.applyTheme(mode)
        }
        container.addView(themeCard)

        /* ---------------------------------------------------------- palette */

        val dynamicAvailable = SfTheme.dynamicColorSupported()
        val dynamicOn = dynamicAvailable && prefs.dynamicColor

        if (dynamicAvailable) {
            container.addView(
                group(
                    listOf(
                        toggle(
                            getString(R.string.dynamic_color),
                            getString(R.string.dynamic_color_sub),
                            prefs.dynamicColor,
                        ) { on ->
                            applyAndRestart { prefs.setAppearance(dynamicColor = on) }
                        }
                    )
                )
            )
        }

        container.addView(paletteCard(dimmed = dynamicOn))

        // Dark style only matters if the user will ever see dark.
        if (prefs.themeMode != Prefs.THEME_LIGHT) {
            container.addView(
                groupOf(
                    segmented(
                        getString(R.string.dark_style),
                        Catalog.darkVariants,
                        prefs.darkVariant,
                    ) { id -> applyAndRestart { prefs.setAppearance(darkVariant = id) } }
                )
            )
        }

        /* --------------------------------------------------------- layout */

        container.addView(section("Layout & type"))
        container.addView(
            groupOf(
                segmented(
                    getString(R.string.density), Catalog.densities, prefs.density
                ) { id -> applyAndRestart { prefs.setAppearance(density = id) } }
            )
        )
        container.addView(
            group(
                listOf(
                    toggle(
                        getString(R.string.serif_accents),
                        getString(R.string.serif_accents_sub),
                        prefs.serifAccents,
                    ) { on -> applyAndRestart { prefs.setAppearance(serifAccents = on) } },
                    toggle(
                        getString(R.string.mono_figures),
                        getString(R.string.mono_figures_sub),
                        prefs.monoFigures,
                    ) { on -> applyAndRestart { prefs.setAppearance(monoFigures = on) } },
                    toggle(
                        getString(R.string.high_contrast),
                        getString(R.string.high_contrast_sub),
                        prefs.highContrast,
                    ) { on -> applyAndRestart { prefs.setAppearance(highContrast = on) } },
                )
            )
        )

        /* --------------------------------------------------------- app icon */

        container.addView(section(getString(R.string.app_icon)))
        container.addView(
            groupOf(
                segmented(
                    getString(R.string.app_icon), Catalog.appIcons, prefs.appIcon
                ) { id ->
                    if (id == prefs.appIcon) return@segmented
                    prefs.appIcon = id
                    SfHaptics.perform(requireView(), Haptics.SELECT, prefs)
                    AppIcons.apply(requireContext(), id)
                    // No recreate: nothing on screen changes. The launcher
                    // picks the new alias up on its own schedule, which is
                    // what the note below is warning about.
                    requireView().snack(IconVariants.PLACEMENT_WARNING)
                }
            )
        )

        /* --------------------------------------------------- motion & feel */

        container.addView(section("Motion & feedback"))
        container.addView(
            groupOf(
                segmented(
                    getString(R.string.motion), Catalog.motionLevels, prefs.motionLevel
                ) { id ->
                    prefs.motionLevel = id
                    SfHaptics.perform(requireView(), Haptics.SELECT, prefs)
                    // Motion is read at animation time, not at inflation, so
                    // this one needs no recreate.
                }
            )
        )
        if (SfTheme.systemAnimationsDisabled(requireContext())) {
            container.addView(
                note(
                    "Animations are switched off in your system settings, " +
                        "so this has no effect until you turn them back on."
                )
            )
        }

        container.addView(
            groupOf(
                segmented(
                    getString(R.string.haptics), Catalog.hapticLevels, prefs.hapticIntensity
                ) { id ->
                    prefs.hapticIntensity = id
                    // Fire the new intensity immediately: the only way to
                    // choose a vibration strength is to feel it.
                    SfHaptics.perform(requireView(), Haptics.COMPLETE, prefs)
                }
            )
        )

        container.addView(
            group(
                listOf(
                    toggle(
                        getString(R.string.sound_enabled),
                        getString(R.string.sound_enabled_sub),
                        prefs.soundEnabled,
                    ) { on -> prefs.soundEnabled = on },
                )
            )
        )

        /* --------------------------------------------------------- gestures */

        container.addView(section(getString(R.string.gestures)))
        container.addView(
            group(
                Navigation.Gesture.entries.map { gesture ->
                    // A destructive gesture is suppressed while confirmations
                    // are on, so its switch is shown but disabled rather than
                    // hidden: a control that vanishes when an unrelated
                    // setting changes is how people conclude the app is
                    // broken.
                    val suppressed = gesture.destructive && prefs.confirmCompletion
                    toggle(
                        gesture.label,
                        if (suppressed) getString(R.string.gesture_needs_confirm_off)
                        else getString(R.string.gesture_has_equivalent),
                        prefs.gestureEnabled(gesture),
                        enabled = !suppressed,
                    ) { on -> prefs.setGestureEnabled(gesture.key, on) }
                }
            )
        )

        /* ------------------------------------------------------- behaviour */

        container.addView(section("Behaviour"))
        container.addView(
            groupOf(
                segmented(
                    getString(R.string.start_screen),
                    Catalog.startDestinations,
                    prefs.startDestination,
                ) { id -> prefs.startDestination = id }
            )
        )
        container.addView(
            group(
                listOf(
                    toggle(
                        getString(R.string.show_history_strip),
                        getString(R.string.show_history_strip_sub),
                        prefs.showHistoryStrip,
                    ) { on -> prefs.showHistoryStrip = on },
                    toggle(
                        getString(R.string.swipe_actions),
                        getString(R.string.swipe_actions_sub),
                        prefs.swipeActionsEnabled,
                    ) { on -> prefs.swipeActionsEnabled = on },
                    toggle(
                        getString(R.string.confirm_completion),
                        getString(R.string.confirm_completion_sub),
                        prefs.confirmCompletion,
                    ) { on -> prefs.confirmCompletion = on },
                )
            )
        )

        container.addView(
            note(
                "Motion, haptics and behaviour settings apply immediately. " +
                    "Colour and layout changes restart the screen."
            )
        )
    }

    /* --------------------------------------------------------- palette card */

    private fun paletteCard(dimmed: Boolean): View {
        val card = layoutInflater.inflate(R.layout.item_palette_picker, container, false)
        val row = card.findViewById<LinearLayout>(R.id.palette_swatches)
        val current = card.findViewById<TextView>(R.id.palette_current)

        current.text = if (dimmed) {
            getString(R.string.dynamic_color_overridden)
        } else {
            Catalog.choiceOf(Catalog.palettes, prefs.palette).detail
        }

        // Wallpaper colours win, so the palettes below are inert. Say so and
        // look inert, rather than letting a tap do nothing.
        row.alpha = if (dimmed) 0.4f else 1f
        row.isEnabled = !dimmed

        for (choice in Catalog.palettes) {
            row.addView(
                swatch(row, choice, selected = choice.id == prefs.palette, enabled = !dimmed)
            )
        }
        return card
    }

    private fun swatch(
        parent: ViewGroup, choice: Choice, selected: Boolean, enabled: Boolean
    ): View {
        // Inflated against the real parent so the root's layout attributes are
        // honoured; a null parent would silently discard them.
        val v = layoutInflater.inflate(R.layout.part_swatch, parent, false)
        val primary = v.findViewById<View>(R.id.swatch_primary)
        val secondary = v.findViewById<View>(R.id.swatch_secondary)
        val check = v.findViewById<ImageView>(R.id.swatch_check)
        val label = v.findViewById<TextView>(R.id.swatch_label)

        val colors = PaletteSwatches.colorsFor(requireContext(), choice.id)
        primary.background = disc(colors.first, ring = selected)
        secondary.background = disc(colors.second, ring = false)

        check.visible(selected)
        if (selected) {
            // Tint against the disc it sits on, not the page.
            check.setColorFilter(PaletteSwatches.onColor(colors.first))
        }
        label.text = choice.label

        // Selection is announced, not just drawn, and the description carries
        // the palette's detail line so a screen-reader user gets the same
        // information a sighted user gets from the swatch itself.
        v.contentDescription = buildString {
            append(choice.label)
            append(". ")
            append(choice.detail)
            if (selected) append(" Selected.")
        }
        ViewCompat.setStateDescription(v, if (selected) "Selected" else null)

        v.isEnabled = enabled
        v.isClickable = enabled
        if (enabled && !selected) {
            v.setOnClickListener {
                applyAndRestart { prefs.setAppearance(palette = choice.id) }
            }
        }
        return v
    }

    private fun previewCard(): View {
        val card = layoutInflater.inflate(R.layout.item_text_card, container, false)
        card.findViewById<TextView>(R.id.text_title).text = "Preview — ${Catalog.choiceOf(Catalog.palettes, prefs.palette).label} · ${Catalog.choiceOf(Catalog.densities, prefs.density).label}"
        card.findViewById<TextView>(R.id.text_body).text = "Habit card · 7d streak · Tap palette or density to see this card re-tint after restart. Calm is paper, Forest is growth, Ocean is depth, Dusk is evening, Mono is quiet."
        card.alpha = 0.95f
        return card
    }

    /** A filled circle, optionally with a selection ring around it. */
    private fun disc(color: Int, ring: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (ring) {
                setStroke(requireContext().dp(3), requireView().themeColor(MR.attr.colorOnSurface))
            } else {
                // A hairline in the outline colour, so a pale swatch still has
                // an edge against a pale card.
                setStroke(requireContext().dp(1), requireView().themeColor(MR.attr.colorOutline))
            }
        }

    /* ------------------------------------------------------------- helpers */

    /**
     * A segmented control bound to a [Catalog] list. The detail line updates
     * to describe whichever option is selected.
     */
    private fun segmented(
        title: String,
        choices: List<Choice>,
        selected: Int,
        onPick: (Int) -> Unit,
    ): View {
        val v = layoutInflater.inflate(R.layout.item_setting_segmented, container, false)
        v.findViewById<TextView>(R.id.segmented_title).text = title
        val detail = v.findViewById<TextView>(R.id.segmented_detail)
        val group = v.findViewById<MaterialButtonToggleGroup>(R.id.segmented_group)

        detail.text = Catalog.choiceOf(choices, selected).detail

        for (choice in choices) {
            val button = MaterialButton(
                requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                // The id must be stable and unique within the group; the
                // catalogue id is both, offset to stay clear of R ids.
                id = BUTTON_ID_BASE + choice.id
                text = choice.label
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
                // Long labels must not silently disappear at large font
                // scales; one line with an ellipsis is better than clipping.
                maxLines = 1
                isAllCaps = false
            }
            group.addView(button)
        }
        group.check(BUTTON_ID_BASE + Catalog.choiceOf(choices, selected).id)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val id = checkedId - BUTTON_ID_BASE
            if (id == selected) return@addOnButtonCheckedListener
            detail.text = Catalog.choiceOf(choices, id).detail
            onPick(id)
        }
        return v
    }

    private fun section(title: String): View =
        layoutInflater.inflate(R.layout.item_section, container, false).also {
            (it as TextView).text = title
        }

    private fun note(text: String): View =
        TextView(requireContext()).apply {
            this.text = text
            setTextAppearance(R.style.Text_SuperFlow_BodyMedium)
            setTextColor(themeColor(MR.attr.colorOnSurfaceVariant))
            val h = context.dp(4)
            setPadding(h, context.dp(4), h, context.dp(16))
        }

    /** Wraps a single view in a card, matching [group]'s look. */
    private fun groupOf(child: View): View = group(listOf(child))

    private fun group(children: List<View>): View {
        val card = layoutInflater.inflate(R.layout.item_setting_group, container, false)
        val holder = card.findViewById<LinearLayout>(R.id.group_container)
        children.forEachIndexed { index, child ->
            holder.addView(child)
            if (index != children.lastIndex) {
                holder.addView(
                    com.google.android.material.divider.MaterialDivider(requireContext())
                )
            }
        }
        return card
    }

    /**
     * @param enabled when false the row is shown greyed and inert. Used for
     *   a setting whose value still matters but which another setting is
     *   currently overriding - showing it disabled explains the override,
     *   where hiding it would just look like the app losing a feature.
     */
    private fun toggle(
        title: String,
        subtitle: String?,
        value: Boolean,
        enabled: Boolean = true,
        onChange: (Boolean) -> Unit,
    ): View {
        val v = layoutInflater.inflate(R.layout.item_setting_toggle, container, false)
        v.findViewById<TextView>(R.id.toggle_title).text = title
        v.findViewById<TextView>(R.id.toggle_sub).apply {
            visible(subtitle != null)
            text = subtitle
        }
        val sw = v.findViewById<MaterialSwitch>(R.id.toggle_switch)
        sw.isChecked = value
        sw.isEnabled = enabled
        v.isEnabled = enabled
        v.alpha = if (enabled) 1f else DISABLED_ALPHA
        if (!enabled) return v

        // The row is the target, not just the switch: a 48dp switch is a small
        // thing to hit, and the label is the obvious thing to tap.
        v.setOnClickListener {
            sw.isChecked = !sw.isChecked
            onChange(sw.isChecked)
        }
        // Avoid a double fire when the switch itself is used.
        sw.setOnClickListener {
            onChange(sw.isChecked)
        }
        return v
    }

    private companion object {
        /**
         * Offset for generated button ids. Well above any aapt-assigned id
         * (which start at 0x7f...) would collide, so a small positive base is
         * safe and keeps `checkedId - BUTTON_ID_BASE` readable.
         */
        const val BUTTON_ID_BASE = 0x00A0_0000

        /** A disabled row stays readable; it just stops inviting a tap. */
        const val DISABLED_ALPHA = 0.5f
    }
}

/**
 * Resolves the two representative colours for each palette swatch.
 *
 * These deliberately read the palette's own colour resources rather than the
 * current theme, because the whole point is to show what a palette looks like
 * while a different one is active.
 */
object PaletteSwatches {

    fun colorsFor(context: android.content.Context, paletteId: Int): Pair<Int, Int> {
        val (primary, secondary) = when (paletteId) {
            ThemeSelection.PALETTE_FOREST_ID ->
                R.color.sf_forest_green_40 to R.color.sf_forest_olive_40
            ThemeSelection.PALETTE_OCEAN_ID ->
                R.color.sf_ocean_teal_40 to R.color.sf_ocean_coral_50
            ThemeSelection.PALETTE_DUSK_ID ->
                R.color.sf_dusk_violet_40 to R.color.sf_dusk_rose_50
            ThemeSelection.PALETTE_MONO_ID ->
                R.color.sf_mono_stone_40 to R.color.sf_mono_stone_60
            // Calm is the base theme and has no overlay, so its swatch comes
            // from the original ramps.
            else -> R.color.sf_green_50 to R.color.sf_amber_50
        }
        return context.getColor(primary) to context.getColor(secondary)
    }

    /**
     * Black or white, whichever is legible on [background].
     *
     * Delegates to [Contrast], which is unit-tested; the swatch check mark is
     * the one place in the app where text sits on an arbitrary palette colour
     * rather than on a themed surface, so getting it wrong is visible.
     */
    fun onColor(background: Int): Int = Contrast.onColorFor(background)
}
