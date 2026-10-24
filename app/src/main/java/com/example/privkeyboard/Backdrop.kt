package com.example.privkeyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Cheap, smooth blur: repeated half-size down-scaling then progressive up-scaling. */
object Blur {
    fun blur(src: Bitmap, factor: Float): Bitmap {
        if (factor <= 1.01f) return src
        val w = src.width
        val h = src.height
        val tw = max(1, (w / factor).toInt())
        val th = max(1, (h / factor).toInt())
        var cur = src
        while (cur.width / 2 >= tw && cur.height / 2 >= th) {
            cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
        }
        if (cur.width != tw || cur.height != th) {
            cur = Bitmap.createScaledBitmap(cur, tw, th, true)
        }
        while (cur.width * 2 < w && cur.height * 2 < h) {
            cur = Bitmap.createScaledBitmap(cur, cur.width * 2, cur.height * 2, true)
        }
        return Bitmap.createScaledBitmap(cur, w, h, true)
    }
}

/**
 * The themed background of one screen (activity or keyboard) plus a blurred copy
 * that glass surfaces sample ("backdrop blur").
 */
class Backdrop(val root: View) {

    private var bg: Bitmap? = null
    private var glass: Bitmap? = null
    private var glassSx = 4f
    private var glassSy = 4f

    var colors: ThemeColors = ThemeColors.DEFAULT
        private set

    /** Bumped on every rebuild so consumers know to refresh their shaders. */
    var version = 0
        private set

    fun rebuild(w: Int, h: Int, context: Context) {
        val s = ThemeStore.settings
        val dark = ThemeStore.isDark(context)

        val base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(base)
        drawGradient(c, w, h, Gradients.colors(s.gradientId, dark))
        val src = ThemeStore.sourceBitmap()
        if (src != null) drawFitted(c, src, w, h, s.fit)

        var result = base
        if (s.bgBlur > 0) result = Blur.blur(base, 1f + s.bgBlur * 0.5f)

        if (s.brightness != 0) {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val oc = Canvas(out)
            oc.drawBitmap(result, 0f, 0f, null)
            val a = (abs(s.brightness) / 100f * 0.8f * 255f).toInt()
            val v = if (s.brightness > 0) 255 else 0
            oc.drawColor(Color.argb(a, v, v, v))
            result = out
        }
        bg = result

        // average luminance of the final picture -> readable text colours
        val tiny = Bitmap.createScaledBitmap(result, 8, 8, true)
        var sum = 0f
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val p = tiny.getPixel(x, y)
                sum += (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)) / 255f
            }
        }
        colors = ThemeColors.compute(sum / 64f, s.accent)

        // low-resolution blurred copy used by glass surfaces
        val gw = max(1, w / 4)
        val gh = max(1, h / 4)
        val small = Bitmap.createScaledBitmap(result, gw, gh, true)
        glass = Blur.blur(small, 1f + s.glassBlur * 0.12f)
        glassSx = w.toFloat() / gw
        glassSy = h.toFloat() / gh

        version++
    }

    fun draw(canvas: Canvas) {
        val b = bg
        if (b != null) canvas.drawBitmap(b, 0f, 0f, null) else canvas.drawColor(0xFF06151C.toInt())
    }

    fun newGlassShader(): BitmapShader? {
        val g = glass ?: return null
        return BitmapShader(g, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    /** Matrix mapping the blurred copy into a view located at (ox, oy) inside [root]. */
    fun glassMatrix(ox: Int, oy: Int, out: Matrix) {
        out.setScale(glassSx, glassSy)
        out.postTranslate(-ox.toFloat(), -oy.toFloat())
    }

    private fun drawGradient(c: Canvas, w: Int, h: Int, cols: IntArray) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, h.toFloat(), w.toFloat(), 0f, cols, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val last = cols[cols.size - 1]
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = RadialGradient(
            w * 0.9f, h * 0.05f, max(w, h) * 0.7f,
            (last and 0x00FFFFFF) or (150 shl 24), last and 0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow)
    }

    private fun drawFitted(c: Canvas, src: Bitmap, w: Int, h: Int, fit: Int) {
        val sw = src.width.toFloat()
        val sh = src.height.toFloat()
        val scale = when (fit) {
            ThemeSettings.FIT_COVER -> max(w / sw, h / sh)
            ThemeSettings.FIT_CONTAIN -> min(w / sw, h / sh)
            else -> 1f
        }
        val dw = sw * scale
        val dh = sh * scale
        val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        c.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }
}

/** Root container that paints the themed backdrop and re-themes its children live. */
class BackdropLayout(context: Context) : FrameLayout(context) {

    val backdrop = Backdrop(this)

    /** Called after every rebuild (e.g. to switch status-bar icon colours). */
    var onRebuilt: ((ThemeColors) -> Unit)? = null

    private var pending = false
    private val listener: () -> Unit = { schedule() }

    init {
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeStore.addListener(listener)
        if (backdrop.version == 0) rebuild()
    }

    override fun onDetachedFromWindow() {
        ThemeStore.removeListener(listener)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        rebuild()
    }

    override fun onDraw(canvas: Canvas) {
        backdrop.draw(canvas)
    }

    private fun schedule() {
        if (pending) return
        pending = true
        post {
            pending = false
            rebuild()
        }
    }

    private fun rebuild() {
        if (width <= 0 || height <= 0) return
        backdrop.rebuild(width, height, context)
        Glass.applyRoles(this)
        invalidateTree(this)
        onRebuilt?.invoke(backdrop.colors)
    }

    private fun invalidateTree(v: View) {
        v.invalidate()
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) invalidateTree(v.getChildAt(i))
        }
    }
}
