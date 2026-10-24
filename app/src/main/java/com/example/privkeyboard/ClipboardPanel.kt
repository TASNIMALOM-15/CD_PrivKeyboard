package com.example.privkeyboard

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Clipboard history shown in place of the keyboard. Tap a clip to insert it. */
class ClipboardPanel(context: Context, private val callbacks: Callbacks) : LinearLayout(context) {

    interface Callbacks {
        fun onPick(text: String)
        fun onClose()
        fun onManage()
    }

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val density = resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()

    private val store = ClipStore.get(context)
    private var pinnedOnly = false

    private val list = LinearLayout(context)
    private val scroller = ScrollView(context)
    private lateinit var allTab: TextView
    private lateinit var pinnedTab: TextView

    init {
        orientation = VERTICAL
        setPadding(dp(6), dp(2), dp(6), dp(6))

        val bar = LinearLayout(context)
        bar.orientation = HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        bar.addView(barButton("ABC") { callbacks.onClose() }, barLp(64))
        allTab = barButton("All") { setFilter(false) }
        pinnedTab = barButton("\u2605 Pinned") { setFilter(true) }
        bar.addView(allTab, barLp(72))
        bar.addView(pinnedTab, barLp(96))
        bar.addView(View(context), LinearLayout.LayoutParams(0, match, 1f))
        bar.addView(barButton("Manage") { callbacks.onManage() }, barLp(84))
        addView(bar, LinearLayout.LayoutParams(match, dp(42)))

        list.orientation = VERTICAL
        scroller.addView(list, ViewGroup.LayoutParams(match, wrap))
        addView(scroller, LinearLayout.LayoutParams(match, 0, 1f))

        updateTabs(false)
    }

    private fun barLp(widthDp: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(dp(widthDp), match)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        return lp
    }

    /** Call whenever the panel is shown. */
    fun refresh() {
        val onlyPinned = pinnedOnly
        store.async({ store.query(pinnedOnly = onlyPinned, limit = 100) }) { render(it) }
    }

    private fun setFilter(onlyPinned: Boolean) {
        pinnedOnly = onlyPinned
        updateTabs(true)
        refresh()
    }

    private fun updateTabs(animate: Boolean) {
        (allTab.background as GlassDrawable).setSelectedState(!pinnedOnly, animate)
        (pinnedTab.background as GlassDrawable).setSelectedState(pinnedOnly, animate)
    }

    private fun barButton(label: String, onClick: () -> Unit): TextView {
        val tv = Glass.text(context, label, 14f, ThemeRole.PRIMARY, true)
        tv.gravity = Gravity.CENTER
        tv.isClickable = true
        tv.background = GlassDrawable(tv)
        tv.setOnClickListener { onClick() }
        return tv
    }

    private fun render(clips: List<Clip>) {
        list.removeAllViews()
        if (clips.isEmpty()) {
            val empty = Glass.text(
                context,
                if (pinnedOnly) "No pinned clips yet." else "No clips yet. Copy some text and it will appear here.",
                14f, ThemeRole.MUTED
            )
            empty.gravity = Gravity.CENTER
            empty.setPadding(dp(16), dp(32), dp(16), dp(16))
            list.addView(empty, LinearLayout.LayoutParams(match, wrap))
            Glass.applyRoles(list)
            return
        }
        for (clip in clips) {
            val row = LinearLayout(context)
            row.orientation = HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(16), dp(4), dp(6), dp(4))
            row.isClickable = true
            row.background = GlassDrawable(row, 18f, 1f)
            row.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                store.background { store.markUsed(clip.id) }
                callbacks.onPick(clip.text)
            }

            val text = Glass.text(context, clip.text.take(200), 14f)
            text.maxLines = 2
            text.ellipsize = TextUtils.TruncateAt.END
            row.addView(text, LinearLayout.LayoutParams(0, wrap, 1f))

            val star = Glass.text(context, if (clip.pinned) "\u2605" else "\u2606", 20f, ThemeRole.MUTED)
            star.gravity = Gravity.CENTER
            if (clip.pinned) {
                star.tag = null
                star.setTextColor(Color.parseColor("#FFC83D"))
            }
            star.setOnClickListener {
                store.background { store.setPinned(clip.id, !clip.pinned) }
                refresh()
            }
            row.addView(star, LinearLayout.LayoutParams(dp(40), dp(40)))

            val del = Glass.text(context, "\u00D7", 22f, ThemeRole.MUTED)
            del.gravity = Gravity.CENTER
            del.setOnClickListener {
                store.background { store.delete(clip.id) }
                refresh()
            }
            row.addView(del, LinearLayout.LayoutParams(dp(40), dp(40)))

            val lp = LinearLayout.LayoutParams(match, wrap)
            lp.setMargins(dp(2), dp(3), dp(2), dp(3))
            list.addView(row, lp)
        }
        Glass.applyRoles(list)
        scroller.scrollTo(0, 0)
    }
}
