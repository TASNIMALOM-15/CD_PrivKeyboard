package com.example.privkeyboard

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.BitmapShader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

private fun withAlpha(color: Int, a: Float): Int =
    (color and 0x00FFFFFF) or ((a * 255f).toInt().coerceIn(0, 255) shl 24)

/** Paints one liquid-glass surface: tint, soft glow, top reflection and highlight border. */
class GlassPaint(private val density: Float) {
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentBorder = Paint(Paint.ANTI_ALIAS_FLAG)
    private val local = RectF()
    private var cachedH = -1f

    init {
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = density * 1.2f
        accentBorder.style = Paint.Style.STROKE
        accentBorder.strokeWidth = density * 1.5f
    }

    private fun ensure(h: Float) {
        if (h == cachedH) return
        cachedH = h
        sheenPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.55f,
            Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
        borderPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.argb(190, 255, 255, 255), Color.argb(40, 255, 255, 255), Shader.TileMode.CLAMP
        )
    }

    /**
     * @param opacity glass tint strength 0..1
     * @param glow    0..1 "illuminated" amount (pressed / selected)
     * @param scale   multiplies every alpha (used to fade a pill in and out)
     */
    fun draw(
        canvas: Canvas, rect: RectF, radius: Float, colors: ThemeColors,
        opacity: Float, glow: Float, accentTint: Boolean = false, scale: Float = 1f
    ) {
        val w = rect.width()
        val h = rect.height()
        if (w <= 0f || h <= 0f || scale <= 0.01f) return
        ensure(h)

        var a = opacity + glow * 0.20f
        if (colors.lightBackdrop) a = a * 1.5f + 0.06f
        tint.color = if (accentTint) {
            withAlpha(colors.accent, min(0.85f, 0.55f + glow * 0.2f) * scale)
        } else {
            withAlpha(Color.WHITE, a.coerceIn(0f, 0.9f) * scale)
        }
        canvas.drawRoundRect(rect, radius, radius, tint)

        if (glow > 0.01f) {
            glowPaint.shader = RadialGradient(
                rect.centerX(), rect.bottom, max(w, h) * 0.75f,
                withAlpha(colors.accent, 0.55f * glow * scale), withAlpha(colors.accent, 0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, glowPaint)
        }

        sheenPaint.alpha = (255 * scale).toInt()
        borderPaint.alpha = (255 * scale).toInt()
        canvas.save()
        canvas.translate(rect.left, rect.top)
        local.set(0f, 0f, w, h)
        canvas.drawRoundRect(local, radius, radius, sheenPaint)
        canvas.drawRoundRect(local, radius, radius, borderPaint)
        canvas.restore()

        if (glow > 0.01f) {
            accentBorder.color = withAlpha(colors.accent, 0.7f * glow * scale)
            canvas.drawRoundRect(rect, radius, radius, accentBorder)
        }
    }
}

/**
 * Background drawable with the Liquid Glass look: blurred backdrop + translucent tint +
 * reflection + highlight border. Selected / pressed state animates into an illuminated pill.
 */
class GlassDrawable(
    private val host: View,
    private val radiusDp: Float = -1f,
    private val strength: Float = 1f,
    private val explicitBackdrop: Backdrop? = null,
    private val accentTint: Boolean = false,
    private val activeOnly: Boolean = false
) : Drawable() {

    private val density = host.resources.displayMetrics.density
    private val glass = GlassPaint(density)
    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val matrix = Matrix()
    private val loc = IntArray(2)
    private val locRoot = IntArray(2)
    private var shader: BitmapShader? = null
    private var shaderVersion = -1
    private var glow = 0f
    private var selectedFlag = false
    private var pressedFlag = false
    private var animator: ValueAnimator? = null

    fun setSelectedState(selected: Boolean, animate: Boolean = true) {
        if (selectedFlag == selected) return
        selectedFlag = selected
        if (animate) {
            animateTo()
        } else {
            animator?.cancel()
            glow = if (selectedFlag || pressedFlag) 1f else 0f
            invalidateSelf()
        }
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val p = state.contains(android.R.attr.state_pressed)
        if (p != pressedFlag) {
            pressedFlag = p
            animateTo()
            return true
        }
        return false
    }

    private fun animateTo() {
        val target = if (selectedFlag || pressedFlag) 1f else 0f
        animator?.cancel()
        val a = ValueAnimator.ofFloat(glow, target)
        a.duration = 190
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            glow = it.animatedValue as Float
            invalidateSelf()
        }
        animator = a
        a.start()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val scale = if (activeOnly) glow else 1f
        if (scale <= 0.01f) return

        val bd = explicitBackdrop ?: Glass.backdropOf(host)
        val colors = bd?.colors ?: ThemeColors.DEFAULT
        rect.set(b.left + 0.5f, b.top + 0.5f, b.right - 0.5f, b.bottom - 0.5f)
        val r = if (radiusDp < 0f) min(rect.width(), rect.height()) / 2f else radiusDp * density

        if (bd != null) {
            if (shader == null || shaderVersion != bd.version) {
                shader = bd.newGlassShader()
                shaderVersion = bd.version
            }
            val sh = shader
            if (sh != null) {
                host.getLocationOnScreen(loc)
                bd.root.getLocationOnScreen(locRoot)
                bd.glassMatrix(loc[0] - locRoot[0] + b.left, loc[1] - locRoot[1] + b.top, matrix)
                sh.setLocalMatrix(matrix)
                backdropPaint.shader = sh
                backdropPaint.alpha = (255 * scale).toInt()
                canvas.drawRoundRect(rect, r, r, backdropPaint)
            }
        }
        val opacity = ThemeStore.settings.glassOpacity / 100f * strength
        glass.draw(canvas, rect, r, colors, opacity, glow, accentTint, scale)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Round colour swatch used for gradient presets and accent colours. */
class Swatch(context: Context, private val colors: IntArray?, private val solid: Int = 0) : View(context) {
    var picked = false
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = resources.displayMetrics.density * 3f
        ring.color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - ring.strokeWidth
        if (colors != null) {
            paint.shader = LinearGradient(
                cx - r, cy + r, cx + r, cy - r, colors, null, Shader.TileMode.CLAMP
            )
        } else {
            paint.shader = null
            paint.color = solid
        }
        canvas.drawCircle(cx, cy, r, paint)
        if (picked) canvas.drawCircle(cx, cy, r + ring.strokeWidth * 0.8f, ring)
    }
}

/** Floating glass navigation pill; the selected item is an illuminated glass pill. */
class GlassNav(
    context: Context,
    items: List<Pair<String, Int>>,
    private val onSelect: (Int) -> Unit
) : LinearLayout(context) {

    private val cells = ArrayList<LinearLayout>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = Glass.dp(context, 6)
        setPadding(pad, pad, pad, pad)
        background = GlassDrawable(this, -1f, 1.5f)
        items.forEachIndexed { i, item ->
            val cell = LinearLayout(context)
            cell.orientation = VERTICAL
            cell.gravity = Gravity.CENTER
            val iv = ImageView(context)
            iv.setImageResource(item.second)
            iv.tag = ThemeRole.PRIMARY
            cell.addView(iv, LayoutParams(Glass.dp(context, 24), Glass.dp(context, 24)))
            cell.addView(Glass.text(context, item.first, 11f))
            cell.background = GlassDrawable(cell, -1f, 1.2f, null, false, true)
            cell.isClickable = true
            cell.setOnClickListener {
                select(i)
                onSelect(i)
            }
            val lp = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            lp.setMargins(Glass.dp(context, 2), 0, Glass.dp(context, 2), 0)
            addView(cell, lp)
            cells.add(cell)
        }
    }

    fun select(index: Int) {
        for ((j, c) in cells.withIndex()) {
            (c.background as GlassDrawable).setSelectedState(j == index)
        }
    }
}

object Glass {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun lp(
        ctx: Context, w: Int = MATCH, h: Int = WRAP,
        l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0
    ): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.setMargins(dp(ctx, l), dp(ctx, t), dp(ctx, r), dp(ctx, b))
        return p
    }

    // ---------------------------------------------------------------- backdrop / colours

    fun backdropOf(v: View?): Backdrop? {
        var p: View? = v
        while (p != null) {
            if (p is BackdropLayout) return p.backdrop
            p = p.parent as? View
        }
        return null
    }

    fun colorsOf(v: View?): ThemeColors = backdropOf(v)?.colors ?: ThemeColors.DEFAULT

    private fun colorFor(role: ThemeRole, c: ThemeColors): Int = when (role) {
        ThemeRole.PRIMARY -> c.text
        ThemeRole.MUTED -> c.muted
        ThemeRole.ACCENT -> c.accent
    }

    /** Re-colours every view tagged with a [ThemeRole] below [root]. */
    fun applyRoles(root: View, anchor: View? = root) {
        applyInternal(root, colorsOf(anchor))
    }

    private fun applyInternal(v: View, c: ThemeColors) {
        val role = v.tag as? ThemeRole
        if (role != null) {
            val color = colorFor(role, c)
            if (v is TextView) {
                v.setTextColor(color)
                if (v is EditText) v.setHintTextColor(c.muted)
            } else if (v is ImageView) {
                v.setColorFilter(color)
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) applyInternal(v.getChildAt(i), c)
        }
    }

    // ---------------------------------------------------------------- factories

    fun text(
        ctx: Context, s: String, sp: Float,
        role: ThemeRole = ThemeRole.PRIMARY, bold: Boolean = false
    ): TextView {
        val tv = TextView(ctx)
        tv.text = s
        tv.textSize = sp
        tv.tag = role
        tv.setTextColor(colorFor(role, ThemeColors.DEFAULT))
        if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
        return tv
    }

    fun section(ctx: Context, title: String): TextView {
        val tv = text(ctx, title, 13f, ThemeRole.MUTED, true)
        tv.setPadding(dp(ctx, 8), dp(ctx, 12), 0, dp(ctx, 6))
        return tv
    }

    fun button(ctx: Context, label: String, onClick: (View) -> Unit): TextView {
        val tv = text(ctx, label, 14f, ThemeRole.PRIMARY, true)
        tv.gravity = Gravity.CENTER
        tv.minHeight = dp(ctx, 44)
        tv.setPadding(dp(ctx, 18), dp(ctx, 8), dp(ctx, 18), dp(ctx, 8))
        tv.isClickable = true
        tv.background = GlassDrawable(tv)
        tv.setOnClickListener { onClick(it) }
        return tv
    }

    fun chip(ctx: Context, label: String, selected: Boolean, onClick: (View) -> Unit): TextView {
        val tv = text(ctx, label, 13f, ThemeRole.PRIMARY, true)
        tv.gravity = Gravity.CENTER
        tv.minHeight = dp(ctx, 38)
        tv.setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6))
        tv.isClickable = true
        val gd = GlassDrawable(tv)
        gd.setSelectedState(selected, false)
        tv.background = gd
        tv.setOnClickListener { onClick(it) }
        return tv
    }

    /** Segmented row of chips; exactly one is illuminated. */
    fun segmented(ctx: Context, labels: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val chips = ArrayList<TextView>()
        labels.forEachIndexed { i, label ->
            val c = chip(ctx, label, i == selected) {
                for ((j, other) in chips.withIndex()) {
                    (other.background as GlassDrawable).setSelectedState(j == i)
                }
                onSelect(i)
            }
            chips.add(c)
            row.addView(c, lp(ctx, 0, WRAP, 0, 0, if (i < labels.size - 1) 8 else 0, 0).also { it.weight = 1f })
        }
        return row
    }

    /** Horizontally scrolling single-choice chips. */
    fun chipScroll(ctx: Context, labels: List<String>, selected: Int, onSelect: (Int) -> Unit): HorizontalScrollView {
        val scroll = HorizontalScrollView(ctx)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val chips = ArrayList<TextView>()
        labels.forEachIndexed { i, label ->
            val c = chip(ctx, label, i == selected) {
                for ((j, other) in chips.withIndex()) {
                    (other.background as GlassDrawable).setSelectedState(j == i)
                }
                onSelect(i)
            }
            chips.add(c)
            row.addView(c, lp(ctx, WRAP, WRAP, 0, 2, 8, 2))
        }
        scroll.addView(row)
        return scroll
    }

    fun card(ctx: Context): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        l.background = GlassDrawable(l, 26f, 1.3f)
        return l
    }

    fun field(ctx: Context, hint: String): EditText {
        val e = EditText(ctx)
        e.hint = hint
        e.tag = ThemeRole.PRIMARY
        e.inputType = InputType.TYPE_CLASS_TEXT
        e.maxLines = 1
        e.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
        e.minHeight = dp(ctx, 46)
        e.setBackgroundColor(Color.TRANSPARENT)
        e.background = GlassDrawable(e, 23f, 0.9f)
        return e
    }

    fun fab(ctx: Context, iconRes: Int, onClick: (View) -> Unit): ImageView {
        val iv = ImageView(ctx)
        iv.setImageResource(iconRes)
        iv.tag = ThemeRole.PRIMARY
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val p = dp(ctx, 16)
        iv.setPadding(p, p, p, p)
        iv.isClickable = true
        iv.background = GlassDrawable(iv, -1f, 1.7f)
        iv.setOnClickListener { onClick(it) }
        return iv
    }

    fun switchRow(ctx: Context, title: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch {
        val sw = Switch(ctx)
        sw.text = title
        sw.textSize = 15f
        sw.tag = ThemeRole.PRIMARY
        sw.isChecked = checked
        val accent = ThemeStore.settings.accent
        sw.thumbTintList = ColorStateList.valueOf(accent)
        sw.trackTintList = ColorStateList.valueOf(withAlpha(accent, 0.45f))
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        return sw
    }

    fun slider(
        ctx: Context, title: String, min: Int, max: Int, value: Int,
        fmt: (Int) -> String, onChange: (Int) -> Unit
    ): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        val label = text(ctx, title, 14f)
        val shown = text(ctx, fmt(value), 13f, ThemeRole.MUTED)
        head.addView(label, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(shown)
        val sb = SeekBar(ctx)
        sb.max = max - min
        sb.progress = value - min
        val accent = ColorStateList.valueOf(ThemeStore.settings.accent)
        sb.progressTintList = accent
        sb.thumbTintList = accent
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val x = progress + min
                shown.text = fmt(x)
                if (fromUser) onChange(x)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        box.addView(head)
        box.addView(sb, lp(ctx, MATCH, WRAP, 0, 4, 0, 8))
        return box
    }

    fun swatchRow(ctx: Context, swatches: List<Swatch>, onPick: (Int) -> Unit): HorizontalScrollView {
        val scroll = HorizontalScrollView(ctx)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        swatches.forEachIndexed { i, s ->
            s.setOnClickListener {
                for ((j, o) in swatches.withIndex()) o.picked = j == i
                onPick(i)
            }
            row.addView(s, lp(ctx, dp(ctx, 46), dp(ctx, 46), 0, 4, 8, 4))
        }
        scroll.addView(row)
        return scroll
    }
}

object GlassDialogs {

    private fun build(anchor: View, title: String): Pair<Dialog, LinearLayout> {
        val activity = anchor.context as Activity
        val bd = Glass.backdropOf(anchor)
        val d = Dialog(activity)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(activity)
        card.orientation = LinearLayout.VERTICAL
        val p = Glass.dp(activity, 20)
        card.setPadding(p, p, p, Glass.dp(activity, 12))
        card.background = GlassDrawable(card, 28f, 2.6f, bd)
        card.addView(Glass.text(activity, title, 19f, ThemeRole.PRIMARY, true))
        d.setContentView(card)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return Pair(d, card)
    }

    private fun buttons(
        ctx: Context, dialog: Dialog, positive: String, onPositive: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        row.addView(Glass.button(ctx, "Cancel") { dialog.dismiss() }, Glass.lp(ctx, Glass.WRAP, Glass.WRAP, 0, 0, 8, 0))
        row.addView(Glass.button(ctx, positive) {
            dialog.dismiss()
            onPositive()
        })
        return row
    }

    fun confirm(anchor: View, title: String, message: String, positive: String, onYes: () -> Unit) {
        val (dialog, card) = build(anchor, title)
        val ctx = card.context
        card.addView(Glass.text(ctx, message, 14f, ThemeRole.MUTED), Glass.lp(ctx, t = 8, b = 16))
        card.addView(buttons(ctx, dialog, positive, onYes))
        Glass.applyRoles(card, anchor)
        dialog.show()
    }

    fun input(anchor: View, title: String, hint: String, initial: String, onSave: (String) -> Unit) {
        val (dialog, card) = build(anchor, title)
        val ctx = card.context
        val field = Glass.field(ctx, hint)
        field.setText(initial)
        card.addView(field, Glass.lp(ctx, t = 10, b = 16))
        card.addView(buttons(ctx, dialog, "Save") { onSave(field.text.toString()) })
        Glass.applyRoles(card, anchor)
        dialog.show()
    }
}

object GlassMenu {
    fun show(anchor: View, items: List<Pair<String, () -> Unit>>) {
        val ctx = anchor.context
        val bd = Glass.backdropOf(anchor)
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        val p = Glass.dp(ctx, 8)
        card.setPadding(p, p, p, p)
        card.background = GlassDrawable(card, 22f, 2.6f, bd)
        val popup = PopupWindow(card, Glass.WRAP, Glass.WRAP, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        for (item in items) {
            val tv = Glass.text(ctx, item.first, 15f)
            tv.setPadding(Glass.dp(ctx, 16), Glass.dp(ctx, 12), Glass.dp(ctx, 24), Glass.dp(ctx, 12))
            tv.isClickable = true
            tv.setOnClickListener {
                popup.dismiss()
                item.second()
            }
            card.addView(tv)
        }
        Glass.applyRoles(card, anchor)
        popup.showAsDropDown(anchor, 0, 0, Gravity.END)
    }
}
