package com.example.privkeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Key sounds and haptics. Sound packs are synthesised on first use (no audio assets),
 * stored in the cache and played through a low-latency SoundPool.
 */
object Feedback {

    private const val SAMPLE_RATE = 22050

    private var app: Context? = null
    private var audio: AudioManager? = null
    private var vibrator: Vibrator? = null
    private var pool: SoundPool? = null
    private val soundIds = HashMap<Int, Int>()
    private val loaded: MutableSet<Int> = Collections.synchronizedSet(HashSet<Int>())

    @Suppress("DEPRECATION")
    fun init(context: Context) {
        if (app != null) return
        val c = context.applicationContext
        app = c
        audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        sp.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        pool = sp

        Thread {
            for (pack in 2 until Effects.soundNames.size) {
                try {
                    val f = File(c.cacheDir, "kb_sound_$pack.wav")
                    if (!f.exists()) f.writeBytes(wav(synth(pack)))
                    soundIds[pack] = sp.load(f.path, 1)
                } catch (e: Exception) {
                    // a missing sound pack must never break typing
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- sound

    fun playKey(type: KeyType) {
        playPack(ThemeStore.settings.soundPack, type)
    }

    fun playPack(pack: Int, type: KeyType) {
        if (pack == 0) return
        val vol = ThemeStore.settings.soundVolume / 100f
        if (pack == 1) {
            val fx = when (type) {
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio?.playSoundEffect(fx, vol)
            return
        }
        val id = soundIds[pack] ?: return
        if (!loaded.contains(id)) return
        val rate = when (type) {
            KeyType.SPACE -> 0.8f
            KeyType.DELETE -> 1.2f
            KeyType.ENTER -> 0.9f
            else -> 1f
        }
        pool?.play(id, vol, vol, 1, 0, rate)
    }

    // ---------------------------------------------------------------- haptics

    fun tap(view: View, type: KeyType) {
        val s = ThemeStore.settings
        tapWith(view, s.haptic, s.hapticStrength)
    }

    fun longPress(view: View) {
        if (ThemeStore.settings.haptic == 1) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun tapWith(view: View, pattern: Int, strength: Int) {
        when (pattern) {
            0 -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            1 -> Unit
            else -> vibrate(pattern, strength)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: Int, strength: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val scale = strength.coerceIn(10, 100) / 100f
        fun amp(base: Int): Int = (base * scale).toInt().coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= 26) {
            val effect = when (pattern) {
                2 -> VibrationEffect.createOneShot(10, amp(90))
                3 -> VibrationEffect.createOneShot(14, amp(170))
                4 -> VibrationEffect.createOneShot(24, amp(255))
                5 -> VibrationEffect.createWaveform(
                    longArrayOf(0, 12, 45, 12), intArrayOf(0, amp(170), 0, amp(170)), -1
                )
                else -> null
            }
            if (effect != null) v.vibrate(effect)
        } else {
            when (pattern) {
                2 -> v.vibrate(8L)
                3 -> v.vibrate(12L)
                4 -> v.vibrate(22L)
                5 -> v.vibrate(longArrayOf(0, 10, 40, 10), -1)
                else -> Unit
            }
        }
    }

    // ---------------------------------------------------------------- synthesis

    private fun synth(pack: Int): ShortArray {
        val ms = when (pack) {
            2 -> 45
            3 -> 80
            4 -> 100
            5 -> 100
            6 -> 180
            else -> 70
        }
        val n = SAMPLE_RATE * ms / 1000
        val rnd = Random(pack.toLong())
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val noise = rnd.nextDouble() * 2.0 - 1.0
            val v: Double = when (pack) {
                2 -> sin(2 * PI * 1500 * t) * exp(-t / 0.008) * 0.65
                3 -> sin(2 * PI * 180 * t) * exp(-t / 0.02) * 0.8 +
                    noise * exp(-t / 0.004) * 0.5 +
                    sin(2 * PI * 3500 * t) * exp(-t / 0.003) * 0.2
                4 -> noise * exp(-t / 0.006) * 0.7 +
                    sin(2 * PI * 2200 * t) * exp(-t / 0.03) * 0.35 +
                    sin(2 * PI * 900 * t) * exp(-t / 0.015) * 0.3
                5 -> {
                    val f = 500.0 + 1800.0 * (t / 0.1)
                    phase += 2 * PI * f / SAMPLE_RATE
                    sin(phase) * sin(PI * t / 0.1).coerceAtLeast(0.0) * 0.6
                }
                6 -> sin(2 * PI * 3100 * t) * exp(-t / 0.05) * 0.5 +
                    sin(2 * PI * 4650 * t) * exp(-t / 0.03) * 0.25
                else -> sin(2 * PI * 620 * t) * exp(-t / 0.012) * 0.7 +
                    noise * exp(-t / 0.002) * 0.3
            }
            out[i] = (v.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return out
    }

    private fun wav(pcm: ShortArray): ByteArray {
        val dataLen = pcm.size * 2
        val b = ByteArrayOutputStream(44 + dataLen)
        fun int32(v: Int) {
            b.write(v and 0xFF); b.write((v shr 8) and 0xFF)
            b.write((v shr 16) and 0xFF); b.write((v shr 24) and 0xFF)
        }
        fun int16(v: Int) {
            b.write(v and 0xFF); b.write((v shr 8) and 0xFF)
        }
        b.write("RIFF".toByteArray())
        int32(36 + dataLen)
        b.write("WAVE".toByteArray())
        b.write("fmt ".toByteArray())
        int32(16)
        int16(1)
        int16(1)
        int32(SAMPLE_RATE)
        int32(SAMPLE_RATE * 2)
        int16(2)
        int16(16)
        b.write("data".toByteArray())
        int32(dataLen)
        for (s in pcm) int16(s.toInt())
        return b.toByteArray()
    }
}
