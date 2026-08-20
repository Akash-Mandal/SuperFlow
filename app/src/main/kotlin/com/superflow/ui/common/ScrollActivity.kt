package com.superflow.ui.common

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.superflow.R
import com.superflow.data.Prefs

/**
 * Base for the secondary screens.
 *
 * Provides an edge-to-edge collapsing toolbar and a single vertical content
 * column hosted in a RecyclerView, so subclasses only build their content.
 */
abstract class ScrollActivity : AppCompatActivity() {

    protected lateinit var content: LinearLayout

    /** Appearance revision this instance inflated against; see [onResume]. */
    private var builtAtRevision = 0

    protected lateinit var toolbar: MaterialToolbar
    protected lateinit var fab: ExtendedFloatingActionButton

    abstract fun titleText(): String

    /** Rebuild [content]. Called on create and whenever [rebuild] is invoked. */
    abstract fun buildContent()

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Theme overlays have to be merged before anything inflates.
        val prefs = Prefs.get(this)
        SfTheme.apply(this, prefs)
        builtAtRevision = prefs.appearanceRevision
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scroll)

        toolbar = findViewById(R.id.toolbar)
        fab = findViewById(R.id.fab)
        toolbar.title = titleText()
        toolbar.setNavigationOnClickListener { finish() }

        val list = findViewById<RecyclerView>(R.id.list)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = SingleContentAdapter(content)

        ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom + resources.getDimensionPixelSize(R.dimen.space_xl))
            insets
        }

        buildContent()
    }

    protected fun contentReady(): Boolean = ::content.isInitialized

    override fun onResume() {
        super.onResume()
        // Picks up a palette / density / contrast change made while this
        // screen was in the back stack.
        if (SfTheme.needsRecreate(Prefs.get(this), builtAtRevision)) recreate()
    }

    protected fun rebuild() {
        content.removeAllViews()
        buildContent()
    }

    /* ------------------------------------------------------- content helpers */

    protected fun section(title: String): View =
        layoutInflater.inflate(R.layout.item_section, content, false).also {
            (it as android.widget.TextView).text = title
        }

    protected fun textCard(title: String, body: String): View =
        layoutInflater.inflate(R.layout.item_text_card, content, false).also {
            it.findViewById<android.widget.TextView>(R.id.text_title).text = title
            it.findViewById<android.widget.TextView>(R.id.text_body).text = body
        }

    protected fun spacer(heightDp: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)
        )
    }
}

class SingleContentAdapter(private val view: View) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) =
        object : RecyclerView.ViewHolder(view) {}
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    override fun getItemCount() = 1
}
