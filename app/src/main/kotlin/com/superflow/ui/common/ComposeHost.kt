package com.superflow.ui.common

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.superflow.ui.theme.SfThemeFromPrefs

/**
 * Bridge between the View shell and the Compose screens.
 *
 * SuperFlow is a hybrid: the shell, the secondary activities and the
 * settings surfaces are still Views, while the five primary screens are
 * Compose. Every crossing goes through here so the two rules that are easy
 * to get wrong are only written once.
 *
 * The first is [ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed].
 * The default disposes when the view detaches from the window, which for a
 * fragment inside a ViewPager2 happens on every swipe — the composition is
 * torn down and rebuilt, losing scroll position and every `remember`.
 * Tying disposal to the view-tree lifecycle instead makes an offscreen page
 * behave like the RecyclerView-backed pages it sits beside.
 *
 * The second is the theme. Compose does not inherit the XML theme overlays
 * that `SfTheme.apply` merged into the Activity, so a Compose island in a
 * themed Activity would quietly ignore the user's palette. Wrapping every
 * entry point in [SfThemeFromPrefs] reads the same preferences the overlays
 * were built from, so the two agree.
 */
fun Fragment.sfComposeView(content: @Composable () -> Unit): ComposeView =
    requireContext().sfComposeView(content).apply {
        // A fragment view created in code has no layout params until it is
        // added, and ViewPager2's generated defaults are wrap-content - which
        // measures the composition with infinite height and crashes any
        // LazyColumn inside. Every tab fills its page; say so explicitly.
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

fun Context.sfComposeView(content: @Composable () -> Unit): ComposeView =
    ComposeView(this).sfContent(content)

/**
 * Fills a [ComposeView] that already exists in an inflated layout.
 *
 * Preferred over constructing one where the surrounding layout is XML: the
 * view keeps its id, its layout params and any insets listener attached to
 * it, none of which survive being swapped out for a fresh instance.
 */
fun ComposeView.sfContent(content: @Composable () -> Unit): ComposeView = apply {
    if (layoutParams == null) {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        SfThemeFromPrefs {
            BoxWithConstraints {
                val modifier = if (constraints.hasBoundedHeight) {
                    Modifier
                } else {
                    Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp)
                }
                Box(modifier = modifier) {
                    content()
                }
            }
        }
    }
}
