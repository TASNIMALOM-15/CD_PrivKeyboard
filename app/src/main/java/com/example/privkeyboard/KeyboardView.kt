package com.example.privkeyboard

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Canvas-drawn Liquid Glass keyboard: multi-touch, backspace repeat, shift / caps-lock,
 * three layers, long-press popups, language switching, animated key illumination.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onNextLanguage()
        fun onEmojiKey()
        fun onShowImePicker()
    }

    var listener: Listener? = null

    /** Text for the Enter key (Go/Search/...). Null = show return-arrow icon. */
    var enterLabel: String? = null
        set(value) {
            field = value
            invalidate()
        }

    /** 0 = off, 1 = shift once, 2 = caps lock */
    var shiftState = 0
        private set

    var layer = Layer.LETTERS
        private set

    var language = Language.ENGLISH
        private set

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private fun rowHeightNow(): Float = dp(52f) * ThemeStore.settings.keyHeight / 100f

    private var rowHeight = rowHeightNow()
    private val padTop = dp(8f)
    private val padBottom = dp(8f)
    private val padH = dp(3f)
    private val gapX = dp(2.5f)
    private val gapY = dp(4f)

    val preferredHeight: Int
        get() = (padTop + padBottom + rowHeight * 4).toInt()

    private val cache = HashMap<Language, Map<Layer, List<List<Key>>>>()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val touches = SparseArray<Key>()
    private var lastShiftTap = 0L
    private var lastFrame = 0L

    // long-press / popup state
    private var lpKey: Key? = null
    private var lpPointer = -1
    private var popupActive = false
    private var popupPointer = -1
    private var popupItems: List<String> = emptyList()
    private var popupSel = 0
    private var popupLeft = 0f
    private var popupTop = 0f
    private var popupItemW = 0f

    // glass rendering
    private val glassPaint = GlassPaint(density)
    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: BitmapShader? = null
    private var shaderVersion = -1
    private val shaderMatrix = Matrix()
    private val loc = IntArray(2)
    private val locRoot = IntArray(2)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val iconStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val tmp = RectF()
    private val tmp2 = RectF()
    private val oval = RectF()

    // press effects (ripple / glow burst) and swipe trail
    private class Fx(val x: Float, val y: Float, val key: Key, val t0: Long, val kind: Int)
    private class TrailPt(val x: Float, val y: Float, val t: Long)

    private val fx = ArrayList<Fx>()
    private val trails = HashMap<Int, ArrayList<TrailPt>>()
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val clipPath = Path()

    private fun withAlpha(color: Int, a: Float): Int =
        (color and 0x00FFFFFF) or ((a * 255f).toInt().coerceIn(0, 255) shl 24)

    private val themeListener: () -> Unit = { onThemeChanged() }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            listener?.onDelete()
            uiHandler.postDelayed(this, 45)
        }
    }

    private val longPressRunnable = Runnable {
        val k = lpKey ?: return@Runnable
        val id = lpPointer
        lpKey = null
        lpPointer = -1
        if (touches.get(id) !== k) return@Runnable
        Feedback.longPress(this)
        if (k.type == KeyType.SPACE || k.type == KeyType.GLOBE) {
            touches.remove(id)
            listener?.onShowImePicker()
        } else if (k.extras.isNotEmpty()) {
            startPopup(id, k)
        }
        invalidate()
    }

    // ---------------------------------------------------------------- public API

    fun switchLayer(l: Layer) {
        layer = l
        invalidate()
    }

    fun applyShift(state: Int) {
        if (shiftState != state) {
            shiftState = state
            invalidate()
        }
    }

    fun applyLanguage(l: Language) {
        language = l
        layer = Layer.LETTERS
        reset()
    }

    fun reset() {
        stopRepeat()
        cancelLongPressState()
        popupActive = false
        popupPointer = -1
        touches.clear()
        fx.clear()
        trails.clear()
        invalidate()
    }

    // ---------------------------------------------------------------- theme

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeStore.addListener(themeListener)
        onThemeChanged()
    }

    override fun onDetachedFromWindow() {
        ThemeStore.removeListener(themeListener)
        reset()
        super.onDetachedFromWindow()
    }

    private fun onThemeChanged() {
        val h = rowHeightNow()
        if (h != rowHeight) {
            rowHeight = h
            for (m in cache.values) computeRects(m)
            requestLayout()
        }
        invalidate()
    }

    // ---------------------------------------------------------------- layout

    private fun layoutFor(l: Language): Map<Layer, List<List<Key>>> =
        cache.getOrPut(l) {
            Layouts.build(l).also { if (width > 0) computeRects(it) }
        }

    private fun currentRows(): List<List<Key>> = layoutFor(language).getValue(layer)

    private fun computeRects(map: Map<Layer, List<List<Key>>>) {
        val w = width.toFloat()
        for (rows in map.values) {
            for ((r, row) in rows.withIndex()) {
                val total = row.sumOf { it.weight.toDouble() }.toFloat()
                val unit = (w - 2 * padH) / total
                var x = padH
                val top = padTop + r * rowHeight
                for (k in row) {
                    val kw = k.weight * unit
                    k.rect.set(x, top, x + kw, top + rowHeight)
                    x += kw
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), preferredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutFor(language)
        for (m in cache.values) computeRects(m)
    }

    private fun keyAt(x: Float, y: Float): Key {
        val rows = currentRows()
        val r = ((y - padTop) / rowHeight).toInt().coerceIn(0, rows.size - 1)
        val row = rows[r]
        val cx = x.coerceIn(padH, width - padH - 1f)
        val idx = row.indexOfFirst { cx >= it.rect.left && cx < it.rect.right }
        if (idx < 0) return row.last()
        val k = row[idx]
        if (k.type == KeyType.SPACER) {
            return if (idx == 0) row[idx + 1] else row[idx - 1]
        }
        return k
    }

    private fun isDown(k: Key): Boolean {
        for (i in 0 until touches.size()) {
            if (touches.valueAt(i) === k) return true
        }
        return false
    }

    private fun shifted(text: String): String =
        if (layer == Layer.LETTERS && shiftState != 0) text.uppercase() else text

    // ---------------------------------------------------------------- drawing

    /** Points the blurred-backdrop shader at the part of the background behind this view. */
    private fun prepareBackdrop(bd: Backdrop?): Boolean {
        if (bd == null) return false
        if (shader == null || shaderVersion != bd.version) {
            shader = bd.newGlassShader()
            shaderVersion = bd.version
        }
        val sh = shader ?: return false
        getLocationOnScreen(loc)
        bd.root.getLocationOnScreen(locRoot)
        bd.glassMatrix(loc[0] - locRoot[0], loc[1] - locRoot[1], shaderMatrix)
        sh.setLocalMatrix(shaderMatrix)
        backdropPaint.shader = sh
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val bd = Glass.backdropOf(this)
        val colors = bd?.colors ?: ThemeColors.DEFAULT
        val settings = ThemeStore.settings
        val opacity = settings.glassOpacity / 100f
        val hasBackdrop = prepareBackdrop(bd)

        iconStroke.color = colors.text
        iconFill.color = colors.text
        hintPaint.color = colors.muted

        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(1L, 64L)
        lastFrame = now
        var animating = false

        for (row in currentRows()) {
            for (k in row) {
                if (k.type == KeyType.SPACER) continue
                tmp.set(
                    k.rect.left + gapX, k.rect.top + gapY,
                    k.rect.right - gapX, k.rect.bottom - gapY
                )
                val target = if (isDown(k) || (k.type == KeyType.SHIFT && shiftState != 0)) 1f else 0f
                if (k.glow != target) {
                    val step = dt / 130f
                    k.glow = if (target > k.glow) min(target, k.glow + step) else max(target, k.glow - step)
                    animating = true
                }
                val scale = when (settings.effect) {
                    ThemeSettings.EFFECT_BOUNCE -> 1f - 0.08f * k.glow
                    ThemeSettings.EFFECT_POP -> 1f + 0.10f * k.glow
                    else -> 1f
                }
                val scaled = scale != 1f
                if (scaled) {
                    canvas.save()
                    canvas.scale(scale, scale, tmp.centerX(), tmp.centerY())
                    if (settings.effect == ThemeSettings.EFFECT_POP) canvas.translate(0f, -dp(3f) * k.glow)
                }
                val radius = min(tmp.width(), tmp.height()) / 2f
                if (hasBackdrop) canvas.drawRoundRect(tmp, radius, radius, backdropPaint)
                val strength = if (k.type == KeyType.CHAR || k.type == KeyType.SPACE) 1f else 0.8f
                glassPaint.draw(
                    canvas, tmp, radius, colors, opacity * strength, k.glow,
                    accentTint = k.type == KeyType.ENTER
                )
                drawContent(canvas, k, tmp, colors)
                if (scaled) canvas.restore()
            }
        }
        if (fx.isNotEmpty()) {
            drawFx(canvas, now, colors)
            if (fx.isNotEmpty()) animating = true
        }
        if (popupActive) drawPopup(canvas, colors, opacity, hasBackdrop)
        if (settings.trail) {
            if (drawTrails(canvas, now, colors)) animating = true
        } else if (trails.isNotEmpty()) {
            trails.clear()
        }
        if (animating) postInvalidateOnAnimation() else lastFrame = 0L
    }

    private fun drawFx(canvas: Canvas, now: Long, colors: ThemeColors) {
        val iter = fx.iterator()
        while (iter.hasNext()) {
            val f = iter.next()
            val dur = if (f.kind == ThemeSettings.EFFECT_RIPPLE) 520f else 650f
            val p = (now - f.t0) / dur
            if (p >= 1f) {
                iter.remove()
                continue
            }
            val e = 1f - (1f - p) * (1f - p)
            val kr = f.key.rect
            tmp2.set(kr.left + gapX, kr.top + gapY, kr.right - gapX, kr.bottom - gapY)
            if (f.kind == ThemeSettings.EFFECT_RIPPLE) {
                val radius = min(tmp2.width(), tmp2.height()) / 2f
                clipPath.reset()
                clipPath.addRoundRect(tmp2, radius, radius, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clipPath)
                fxPaint.shader = null
                fxPaint.color = withAlpha(colors.accent, (1f - p) * 0.55f)
                val maxR = max(tmp2.width(), tmp2.height()) * 1.1f
                canvas.drawCircle(f.x, f.y, maxR * (0.15f + 0.85f * e), fxPaint)
                canvas.restore()
            } else {
                val r = min(tmp2.width(), tmp2.height()) * (0.7f + 1.6f * e)
                fxPaint.shader = RadialGradient(
                    tmp2.centerX(), tmp2.centerY(), r,
                    withAlpha(colors.accent, (1f - p) * 0.65f), withAlpha(colors.accent, 0f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(tmp2.centerX(), tmp2.centerY(), r, fxPaint)
                fxPaint.shader = null
            }
        }
    }

    /** Fading glowing finger trail. Returns true while something is still visible. */
    private fun drawTrails(canvas: Canvas, now: Long, colors: ThemeColors): Boolean {
        val life = 380f
        var any = false
        val iter = trails.entries.iterator()
        while (iter.hasNext()) {
            val pts = iter.next().value
            while (pts.isNotEmpty() && now - pts[0].t > life) pts.removeAt(0)
            if (pts.isEmpty()) {
                iter.remove()
                continue
            }
            for (i in 1 until pts.size) {
                val a = pts[i]
                val b = pts[i - 1]
                val fade = (1f - (now - a.t) / life).coerceIn(0f, 1f)
                trailPaint.color = withAlpha(colors.accent, 0.32f * fade)
                trailPaint.strokeWidth = dp(12f) * fade
                canvas.drawLine(b.x, b.y, a.x, a.y, trailPaint)
                trailPaint.color = withAlpha(Color.WHITE, 0.85f * fade)
                trailPaint.strokeWidth = dp(3.5f) * fade
                canvas.drawLine(b.x, b.y, a.x, a.y, trailPaint)
            }
            if (pts.size > 1) any = true
        }
        return any
    }

    private fun drawContent(canvas: Canvas, k: Key, r: RectF, colors: ThemeColors) {
        val cx = r.centerX()
        val cy = r.centerY()
        when (k.type) {
            KeyType.CHAR -> {
                drawLabel(canvas, shifted(k.label), cx, cy, 22f, colors.text)
                if (k.hint.isNotEmpty() && layer == Layer.LETTERS) {
                    hintPaint.textSize = dp(10f)
                    canvas.drawText(k.hint, r.right - dp(6f), r.top + dp(13f), hintPaint)
                }
            }
            KeyType.LAYER_LETTERS, KeyType.LAYER_SYM1, KeyType.LAYER_SYM2 ->
                drawLabel(canvas, k.label, cx, cy, 15f, colors.text)
            KeyType.SHIFT -> drawShift(canvas, cx, cy)
            KeyType.DELETE -> drawDelete(canvas, cx, cy)
            KeyType.GLOBE -> drawGlobe(canvas, cx, cy)
            KeyType.EMOJI -> drawSmiley(canvas, cx, cy)
            KeyType.ENTER -> {
                iconStroke.color = Color.WHITE
                val label = enterLabel
                if (label != null) drawLabel(canvas, label, cx, cy, 15f, Color.WHITE)
                else drawEnter(canvas, cx, cy)
                iconStroke.color = colors.text
            }
            KeyType.SPACE -> drawLabel(canvas, language.label, cx, cy, 12f, colors.muted)
            else -> Unit
        }
    }

    private fun drawLabel(canvas: Canvas, text: String, cx: Float, cy: Float, sizeDp: Float, color: Int) {
        textPaint.color = color
        textPaint.textSize = dp(sizeDp)
        val fm = textPaint.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    private fun drawPopup(canvas: Canvas, colors: ThemeColors, opacity: Float, hasBackdrop: Boolean) {
        val n = popupItems.size
        val top = popupTop + gapY - dp(2f)
        val bottom = popupTop + rowHeight - gapY + dp(2f)
        tmp.set(popupLeft, top, popupLeft + n * popupItemW, bottom)
        val radius = (bottom - top) / 2f
        if (hasBackdrop) canvas.drawRoundRect(tmp, radius, radius, backdropPaint)
        glassPaint.draw(canvas, tmp, radius, colors, max(0.45f, opacity * 2f), 0f)
        for (j in 0 until n) {
            val x0 = popupLeft + j * popupItemW
            if (j == popupSel) {
                tmp2.set(x0 + dp(2f), top + dp(2f), x0 + popupItemW - dp(2f), bottom - dp(2f))
                glassPaint.draw(canvas, tmp2, (tmp2.height()) / 2f, colors, 0.2f, 1f)
            }
            drawLabel(canvas, shifted(popupItems[j]), x0 + popupItemW / 2f, (top + bottom) / 2f, 22f, colors.text)
        }
    }

    private fun drawShift(canvas: Canvas, cx: Float, cy0: Float) {
        val s = dp(9f)
        val cy = cy0 - s * 0.15f
        path.reset()
        path.moveTo(cx, cy - s)
        path.lineTo(cx + s, cy)
        path.lineTo(cx + s * 0.45f, cy)
        path.lineTo(cx + s * 0.45f, cy + s * 0.9f)
        path.lineTo(cx - s * 0.45f, cy + s * 0.9f)
        path.lineTo(cx - s * 0.45f, cy)
        path.lineTo(cx - s, cy)
        path.close()
        canvas.drawPath(path, if (shiftState != 0) iconFill else iconStroke)
        if (shiftState == 2) {
            canvas.drawLine(cx - s * 0.45f, cy + s * 1.35f, cx + s * 0.45f, cy + s * 1.35f, iconStroke)
        }
    }

    private fun drawDelete(canvas: Canvas, cx: Float, cy: Float) {
        val s = dp(9f)
        path.reset()
        path.moveTo(cx - s, cy)
        path.lineTo(cx - s * 0.35f, cy - s * 0.75f)
        path.lineTo(cx + s, cy - s * 0.75f)
        path.lineTo(cx + s, cy + s * 0.75f)
        path.lineTo(cx - s * 0.35f, cy + s * 0.75f)
        path.close()
        canvas.drawPath(path, iconStroke)
        canvas.drawLine(cx + s * 0.025f, cy - s * 0.3f, cx + s * 0.625f, cy + s * 0.3f, iconStroke)
        canvas.drawLine(cx + s * 0.025f, cy + s * 0.3f, cx + s * 0.625f, cy - s * 0.3f, iconStroke)
    }

    private fun drawEnter(canvas: Canvas, cx: Float, cy: Float) {
        val s = dp(8f)
        path.reset()
        path.moveTo(cx + s, cy - s * 0.7f)
        path.lineTo(cx + s, cy + s * 0.3f)
        path.lineTo(cx - s, cy + s * 0.3f)
        canvas.drawPath(path, iconStroke)
        canvas.drawLine(cx - s, cy + s * 0.3f, cx - s * 0.35f, cy - s * 0.35f, iconStroke)
        canvas.drawLine(cx - s, cy + s * 0.3f, cx - s * 0.35f, cy + s * 0.95f, iconStroke)
    }

    private fun drawGlobe(canvas: Canvas, cx: Float, cy: Float) {
        val s = dp(8f)
        canvas.drawCircle(cx, cy, s, iconStroke)
        canvas.drawLine(cx - s, cy, cx + s, cy, iconStroke)
        canvas.drawLine(cx, cy - s, cx, cy + s, iconStroke)
        oval.set(cx - s * 0.45f, cy - s, cx + s * 0.45f, cy + s)
        canvas.drawOval(oval, iconStroke)
    }

    private fun drawSmiley(canvas: Canvas, cx: Float, cy: Float) {
        val s = dp(8f)
        canvas.drawCircle(cx, cy, s, iconStroke)
        canvas.drawCircle(cx - s * 0.35f, cy - s * 0.25f, dp(1.3f), iconFill)
        canvas.drawCircle(cx + s * 0.35f, cy - s * 0.25f, dp(1.3f), iconFill)
        oval.set(cx - s * 0.55f, cy - s * 0.45f, cx + s * 0.55f, cy + s * 0.65f)
        canvas.drawArc(oval, 25f, 130f, false, iconStroke)
    }

    // ---------------------------------------------------------------- touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val k = keyAt(e.getX(i), e.getY(i))
                if (!popupActive) cancelLongPressState()
                touches.put(id, k)
                addTrailPoint(id, e.getX(i), e.getY(i), true)
                pressKey(id, k, e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    if (popupActive && id == popupPointer) {
                        updatePopupSelection(e.getX(i))
                        continue
                    }
                    val old = touches.get(id) ?: continue
                    addTrailPoint(id, e.getX(i), e.getY(i), false)
                    val k = keyAt(e.getX(i), e.getY(i))
                    if (k !== old) {
                        if (old.type == KeyType.DELETE) stopRepeat()
                        if (id == lpPointer) cancelLongPressState()
                        touches.put(id, k)
                        pressKey(id, k, e.getX(i), e.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                if (id == lpPointer) cancelLongPressState()
                if (popupActive && id == popupPointer) {
                    finishPopup()
                } else {
                    val k = touches.get(id)
                    touches.remove(id)
                    if (k != null) releaseKey(k)
                }
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        invalidate()
        return true
    }

    private fun addTrailPoint(id: Int, x: Float, y: Float, fresh: Boolean) {
        if (!ThemeStore.settings.trail) return
        val list = if (fresh) ArrayList<TrailPt>().also { trails[id] = it } else trails[id] ?: return
        list.add(TrailPt(x, y, SystemClock.uptimeMillis()))
        if (list.size > 80) list.removeAt(0)
    }

    private fun pressKey(id: Int, k: Key, x: Float, y: Float) {
        Feedback.tap(this, k.type)
        Feedback.playKey(k.type)
        val effect = ThemeStore.settings.effect
        if (effect == ThemeSettings.EFFECT_RIPPLE || effect == ThemeSettings.EFFECT_BURST) {
            fx.add(Fx(x, y, k, SystemClock.uptimeMillis(), effect))
        }
        if (k.type == KeyType.DELETE) {
            listener?.onDelete()
            uiHandler.postDelayed(repeatRunnable, 400)
        } else if (k.extras.isNotEmpty() || k.type == KeyType.SPACE || k.type == KeyType.GLOBE) {
            lpKey = k
            lpPointer = id
            uiHandler.postDelayed(longPressRunnable, 380)
        }
    }

    private fun releaseKey(k: Key) {
        when (k.type) {
            KeyType.DELETE -> stopRepeat()
            KeyType.CHAR -> commitChar(k.label)
            KeyType.SPACE -> listener?.onText(" ")
            KeyType.ENTER -> listener?.onEnter()
            KeyType.GLOBE -> listener?.onNextLanguage()
            KeyType.EMOJI -> listener?.onEmojiKey()
            KeyType.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shiftState = when {
                    shiftState == 2 -> 0
                    shiftState == 1 && now - lastShiftTap < 350 -> 2
                    shiftState == 1 -> 0
                    else -> 1
                }
                lastShiftTap = now
            }
            KeyType.LAYER_LETTERS -> layer = Layer.LETTERS
            KeyType.LAYER_SYM1 -> layer = Layer.SYMBOLS1
            KeyType.LAYER_SYM2 -> layer = Layer.SYMBOLS2
            else -> Unit
        }
        invalidate()
    }

    private fun commitChar(text: String) {
        val out = shifted(text)
        if (layer == Layer.LETTERS && shiftState == 1) shiftState = 0
        listener?.onText(out)
    }

    // ---------------------------------------------------------------- popup

    private fun startPopup(id: Int, k: Key) {
        val items = k.extras.map { it.toString() }
        if (items.isEmpty()) return
        val n = items.size
        var itemW = max(dp(40f), k.rect.width())
        if (n * itemW > width) itemW = width.toFloat() / n
        val stripW = n * itemW
        val left = (k.rect.centerX() - itemW / 2f).coerceIn(0f, max(0f, width - stripW))
        val rowIdx = currentRows().indexOfFirst { row -> row.any { it === k } }.coerceAtLeast(0)
        popupItems = items
        popupItemW = itemW
        popupLeft = left
        popupTop = padTop + max(rowIdx - 1, 0) * rowHeight
        popupSel = ((k.rect.centerX() - left) / itemW).toInt().coerceIn(0, n - 1)
        popupPointer = id
        popupActive = true
    }

    private fun updatePopupSelection(x: Float) {
        val n = popupItems.size
        if (n == 0) return
        popupSel = ((x - popupLeft) / popupItemW).toInt().coerceIn(0, n - 1)
    }

    private fun finishPopup() {
        val text = popupItems.getOrNull(popupSel)
        touches.remove(popupPointer)
        popupActive = false
        popupPointer = -1
        if (text != null) commitChar(text)
    }

    private fun cancelLongPressState() {
        uiHandler.removeCallbacks(longPressRunnable)
        lpKey = null
        lpPointer = -1
    }

    private fun stopRepeat() {
        uiHandler.removeCallbacks(repeatRunnable)
    }
}
