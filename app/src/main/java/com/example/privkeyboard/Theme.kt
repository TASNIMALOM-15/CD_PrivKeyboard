package com.example.privkeyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Which colour a themed text/icon uses; applied automatically by [Glass.applyRoles]. */
enum class ThemeRole { PRIMARY, MUTED, ACCENT }

data class ThemeSettings(
    val mode: Int = MODE_SYSTEM,
    val useImage: Boolean = false,
    val gradientId: Int = 0,
    val fit: Int = FIT_COVER,
    val bgBlur: Int = 0,
    val brightness: Int = 0,
    val glassOpacity: Int = 25,
    val glassBlur: Int = 60,
    val accent: Int = DEFAULT_ACCENT,
    val keyHeight: Int = 100,
    val effect: Int = EFFECT_RIPPLE,
    val trail: Boolean = false,
    val soundPack: Int = 0,
    val soundVolume: Int = 60,
    val haptic: Int = 0,
    val hapticStrength: Int = 60,
    val imageVersion: Int = 0
) {
    companion object {
        const val MODE_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
        const val FIT_COVER = 0
        const val FIT_CONTAIN = 1
        const val FIT_CENTER = 2
        const val EFFECT_NONE = 0
        const val EFFECT_RIPPLE = 1
        const val EFFECT_BURST = 2
        const val EFFECT_BOUNCE = 3
        const val EFFECT_POP = 4
        val DEFAULT_ACCENT: Int = 0xFF4FC3F7.toInt()
    }
}

/** Text colours chosen from the actual brightness of the backdrop (adaptive contrast). */
class ThemeColors(val text: Int, val muted: Int, val accent: Int, val lightBackdrop: Boolean) {
    companion object {
        val DEFAULT = ThemeColors(0xFFF3F6F9.toInt(), 0xA6F3F6F9.toInt(), ThemeSettings.DEFAULT_ACCENT, false)

        fun compute(luminance: Float, accent: Int): ThemeColors {
            val light = luminance > 0.62f
            val text = if (light) 0xFF11161B.toInt() else 0xFFF3F6F9.toInt()
            val muted = (text and 0x00FFFFFF) or (0xA6 shl 24)
            return ThemeColors(text, muted, accent, light)
        }
    }
}

object Gradients {
    class Preset(val name: String, val dark: IntArray, val light: IntArray = dark)

    private fun ints(vararg v: Long) = IntArray(v.size) { v[it].toInt() }

    val presets: List<Preset> = listOf(
        Preset("Auto", ints(0xFF02131B, 0xFF0A4A5E, 0xFF3E93AE), ints(0xFFEAF4FA, 0xFFCFE5F3, 0xFFA9CFE6)),
        Preset("Ocean", ints(0xFF03161F, 0xFF0B5A73, 0xFF52A9C6)),
        Preset("Aurora", ints(0xFF03110E, 0xFF0E6B58, 0xFF7B5CF0)),
        Preset("Sunset", ints(0xFF1B0B22, 0xFF8A2F5B, 0xFFF0875A)),
        Preset("Midnight", ints(0xFF02040A, 0xFF0B1530, 0xFF26327A)),
        Preset("Forest", ints(0xFF04120A, 0xFF14532D, 0xFF6BAF6A)),
        Preset("Rose", ints(0xFF1F0A14, 0xFF8E2C57, 0xFFF2A1C0)),
        Preset("Graphite", ints(0xFF0A0B0D, 0xFF24272D, 0xFF565B66)),
        Preset("Violet", ints(0xFF0A0618, 0xFF3B1D85, 0xFFA27BFF)),
        Preset("Peach", ints(0xFFFFF1E6, 0xFFFFD3B6, 0xFFFFA8A8)),
        Preset("Mint", ints(0xFFE8FFF4, 0xFFB8F0D6, 0xFF7FD8C3))
    )

    val accents: IntArray = ints(
        0xFF4FC3F7, 0xFF4DD6B0, 0xFFA27BFF, 0xFFFF7BAC,
        0xFFFFC857, 0xFFFF8A65, 0xFFA6E34F, 0xFFFFFFFF
    )

    fun colors(id: Int, dark: Boolean): IntArray {
        val p = presets.getOrElse(id) { presets[0] }
        return if (dark) p.dark else p.light
    }
}

/** Persistent theme settings + the user's chosen background image. */
object ThemeStore {
    private const val FILE_NAME = "theme_bg.jpg"

    private lateinit var app: Context
    private val listeners = ArrayList<() -> Unit>()
    private var source: Bitmap? = null
    private var sourceVersion = -1

    var settings = ThemeSettings()
        private set

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        settings = load()
    }

    fun isDark(context: Context): Boolean = when (settings.mode) {
        ThemeSettings.MODE_LIGHT -> false
        ThemeSettings.MODE_DARK -> true
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    // ---------------------------------------------------------------- listeners

    fun addListener(l: () -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun update(change: (ThemeSettings) -> ThemeSettings) {
        settings = change(settings)
        save(settings)
        for (l in ArrayList(listeners)) l()
    }

    // ---------------------------------------------------------------- image

    /** Decodes and stores the chosen photo privately (call off the main thread). */
    fun saveImage(uri: Uri): Boolean {
        return try {
            val cr = app.contentResolver
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            var bmp: Bitmap = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            val orientation = try {
                cr.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val m = Matrix()
                m.postRotate(degrees)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            FileOutputStream(File(app.filesDir, FILE_NAME)).use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Switch to the stored photo (main thread). */
    fun useSavedImage() {
        source = null
        update { it.copy(useImage = true, imageVersion = it.imageVersion + 1) }
    }

    fun removeImage() {
        File(app.filesDir, FILE_NAME).delete()
        source = null
        update { it.copy(useImage = false, imageVersion = it.imageVersion + 1) }
    }

    fun sourceBitmap(): Bitmap? {
        val s = settings
        if (!s.useImage) return null
        if (source != null && sourceVersion == s.imageVersion) return source
        val f = File(app.filesDir, FILE_NAME)
        if (!f.exists()) return null
        source = try {
            BitmapFactory.decodeFile(f.path)
        } catch (e: Exception) {
            null
        }
        sourceVersion = s.imageVersion
        return source
    }

    fun resetBackground() {
        File(app.filesDir, FILE_NAME).delete()
        source = null
        update {
            it.copy(
                useImage = false, gradientId = 0, fit = ThemeSettings.FIT_COVER,
                bgBlur = 0, brightness = 0, imageVersion = it.imageVersion + 1
            )
        }
    }

    fun resetAll() {
        File(app.filesDir, FILE_NAME).delete()
        source = null
        update { ThemeSettings(imageVersion = it.imageVersion + 1) }
    }

    // ---------------------------------------------------------------- import / export

    fun toJson(): String {
        val s = settings
        val o = JSONObject()
        o.put("mode", s.mode)
        o.put("gradient", s.gradientId)
        o.put("fit", s.fit)
        o.put("bgBlur", s.bgBlur)
        o.put("brightness", s.brightness)
        o.put("glassOpacity", s.glassOpacity)
        o.put("glassBlur", s.glassBlur)
        o.put("accent", s.accent)
        o.put("keyHeight", s.keyHeight)
        o.put("effect", s.effect)
        o.put("trail", s.trail)
        o.put("soundPack", s.soundPack)
        o.put("soundVolume", s.soundVolume)
        o.put("haptic", s.haptic)
        o.put("hapticStrength", s.hapticStrength)
        return o.toString(2)
    }

    fun applyJson(text: String): Boolean {
        return try {
            val o = JSONObject(text.trim())
            update {
                it.copy(
                    mode = o.optInt("mode", it.mode).coerceIn(0, 2),
                    gradientId = o.optInt("gradient", it.gradientId).coerceIn(0, Gradients.presets.size - 1),
                    fit = o.optInt("fit", it.fit).coerceIn(0, 2),
                    bgBlur = o.optInt("bgBlur", it.bgBlur).coerceIn(0, 100),
                    brightness = o.optInt("brightness", it.brightness).coerceIn(-80, 80),
                    glassOpacity = o.optInt("glassOpacity", it.glassOpacity).coerceIn(0, 60),
                    glassBlur = o.optInt("glassBlur", it.glassBlur).coerceIn(0, 100),
                    accent = o.optInt("accent", it.accent),
                    keyHeight = o.optInt("keyHeight", it.keyHeight).coerceIn(80, 130),
                    effect = o.optInt("effect", it.effect).coerceIn(0, Effects.effectNames.size - 1),
                    trail = o.optBoolean("trail", it.trail),
                    soundPack = o.optInt("soundPack", it.soundPack).coerceIn(0, Effects.soundNames.size - 1),
                    soundVolume = o.optInt("soundVolume", it.soundVolume).coerceIn(0, 100),
                    haptic = o.optInt("haptic", it.haptic).coerceIn(0, Effects.hapticNames.size - 1),
                    hapticStrength = o.optInt("hapticStrength", it.hapticStrength).coerceIn(10, 100)
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- storage

    private fun prefs() = app.getSharedPreferences("theme", Context.MODE_PRIVATE)

    private fun load(): ThemeSettings {
        val p = prefs()
        val d = ThemeSettings()
        return ThemeSettings(
            mode = p.getInt("mode", d.mode),
            useImage = p.getBoolean("useImage", d.useImage),
            gradientId = p.getInt("gradient", d.gradientId),
            fit = p.getInt("fit", d.fit),
            bgBlur = p.getInt("bgBlur", d.bgBlur),
            brightness = p.getInt("brightness", d.brightness),
            glassOpacity = p.getInt("glassOpacity", d.glassOpacity),
            glassBlur = p.getInt("glassBlur", d.glassBlur),
            accent = p.getInt("accent", d.accent),
            keyHeight = p.getInt("keyHeight", d.keyHeight),
            effect = p.getInt("effect", d.effect),
            trail = p.getBoolean("trail", d.trail),
            soundPack = p.getInt("soundPack", d.soundPack),
            soundVolume = p.getInt("soundVolume", d.soundVolume),
            haptic = p.getInt("haptic", d.haptic),
            hapticStrength = p.getInt("hapticStrength", d.hapticStrength),
            imageVersion = p.getInt("imageVersion", d.imageVersion)
        )
    }

    private fun save(s: ThemeSettings) {
        prefs().edit()
            .putInt("mode", s.mode)
            .putBoolean("useImage", s.useImage)
            .putInt("gradient", s.gradientId)
            .putInt("fit", s.fit)
            .putInt("bgBlur", s.bgBlur)
            .putInt("brightness", s.brightness)
            .putInt("glassOpacity", s.glassOpacity)
            .putInt("glassBlur", s.glassBlur)
            .putInt("accent", s.accent)
            .putInt("keyHeight", s.keyHeight)
            .putInt("effect", s.effect)
            .putBoolean("trail", s.trail)
            .putInt("soundPack", s.soundPack)
            .putInt("soundVolume", s.soundVolume)
            .putInt("haptic", s.haptic)
            .putInt("hapticStrength", s.hapticStrength)
            .putInt("imageVersion", s.imageVersion)
            .apply()
    }
}
