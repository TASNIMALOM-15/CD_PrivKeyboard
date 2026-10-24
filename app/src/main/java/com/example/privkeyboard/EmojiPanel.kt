package com.example.privkeyboard

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Emoji picker shown in place of the keys (glass tabs and buttons). */
class EmojiPanel(context: Context, private val callbacks: Callbacks) : LinearLayout(context) {

    interface Callbacks {
        fun onEmoji(emoji: String)
        fun onBackspace()
        fun onClose()
    }

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val density = resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()

    private val columns = 8
    private val prefs = context.getSharedPreferences("emoji", Context.MODE_PRIVATE)
    private val uiHandler = Handler(Looper.getMainLooper())
    private val tabViews = ArrayList<TextView>()
    private val grid = GridLayout(context)
    private val scroller = ScrollView(context)

    private val repeatRunnable = object : Runnable {
        override fun run() {
            callbacks.onBackspace()
            uiHandler.postDelayed(this, 45)
        }
    }

    init {
        orientation = VERTICAL
        setPadding(dp(6), dp(2), dp(6), dp(6))

        // category tabs
        val tabs = LinearLayout(context)
        tabs.orientation = HORIZONTAL
        EmojiData.tabIcons.forEachIndexed { i, icon ->
            val tv = TextView(context)
            tv.text = icon
            tv.textSize = 19f
            tv.gravity = Gravity.CENTER
            tv.isClickable = true
            tv.background = GlassDrawable(tv, -1f, 1f, null, false, true)
            tv.setOnClickListener { select(i) }
            tabViews.add(tv)
            val lp = LinearLayout.LayoutParams(0, match, 1f)
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            tabs.addView(tv, lp)
        }
        addView(tabs, LinearLayout.LayoutParams(match, dp(40)))

        // emoji grid
        grid.columnCount = columns
        scroller.addView(grid, ViewGroup.LayoutParams(match, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroller, LinearLayout.LayoutParams(match, 0, 1f))

        // bottom bar: ABC + backspace
        val bar = LinearLayout(context)
        bar.orientation = HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        val abc = Glass.button(context, "ABC") { callbacks.onClose() }
        bar.addView(abc, LinearLayout.LayoutParams(dp(88), match))

        bar.addView(View(context), LinearLayout.LayoutParams(0, match, 1f))

        val back = ImageView(context)
        back.setImageResource(R.drawable.ic_backspace)
        back.tag = ThemeRole.PRIMARY
        back.setPadding(dp(26), dp(10), dp(26), dp(10))
        back.background = GlassDrawable(back)
        back.isClickable = true
        back.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    callbacks.onBackspace()
                    uiHandler.postDelayed(repeatRunnable, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    uiHandler.removeCallbacks(repeatRunnable)
                }
            }
            true
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(88), match))

        val barLp = LinearLayout.LayoutParams(match, dp(46))
        barLp.topMargin = dp(4)
        addView(bar, barLp)
    }

    /** Call when the panel is shown. */
    fun show() {
        select(if (recents().isEmpty()) 1 else 0)
    }

    private fun recents(): List<String> =
        (prefs.getString("recent", "") ?: "").split("|").filter { it.isNotEmpty() }

    private fun addRecent(emoji: String) {
        val list = recents().toMutableList()
        list.remove(emoji)
        list.add(0, emoji)
        while (list.size > 32) list.removeAt(list.size - 1)
        prefs.edit().putString("recent", list.joinToString("|")).apply()
    }

    private fun select(index: Int) {
        for ((j, tv) in tabViews.withIndex()) {
            (tv.background as GlassDrawable).setSelectedState(j == index)
        }
        val items = if (index == 0) recents() else EmojiData.categories[index - 1]
        val cellW = (resources.displayMetrics.widthPixels - dp(12)) / columns
        val cellH = dp(44)
        grid.removeAllViews()
        for (e in items) {
            val tv = TextView(context)
            tv.text = e
            tv.textSize = 24f
            tv.gravity = Gravity.CENTER
            tv.setTextColor(Color.WHITE)
            tv.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                callbacks.onEmoji(e)
                addRecent(e)
            }
            val lp = GridLayout.LayoutParams()
            lp.width = cellW
            lp.height = cellH
            grid.addView(tv, lp)
        }
        scroller.scrollTo(0, 0)
    }
}
