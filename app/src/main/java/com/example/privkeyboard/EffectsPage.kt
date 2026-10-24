package com.example.privkeyboard

import android.widget.LinearLayout
import android.widget.ScrollView

/** Key-press effects, swipe trail, sound packs and haptic patterns (all live). */
class EffectsPage(private val main: MainActivity) : ScrollView(main) {

    private val col = LinearLayout(main)

    init {
        clipToPadding = false
        setPadding(Glass.dp(main, 16), 0, Glass.dp(main, 16), Glass.dp(main, 140))
        col.orientation = LinearLayout.VERTICAL
        addView(col)
        build()
    }

    /** Rebuilt every time the page is shown so presets applied elsewhere are reflected. */
    fun onShow() {
        col.removeAllViews()
        build()
        Glass.applyRoles(col)
    }

    private fun build() {
        val s = ThemeStore.settings

        col.addView(Glass.section(main, "TRY IT \u2014 PRESS AND SWIPE THE KEYS"))
        val preview = Glass.card(main)
        preview.setPadding(Glass.dp(main, 4), Glass.dp(main, 4), Glass.dp(main, 4), Glass.dp(main, 4))
        preview.addView(KeyboardView(main), Glass.lp(main))
        col.addView(preview)

        col.addView(Glass.section(main, "KEY ANIMATION"))
        val anim = Glass.card(main)
        anim.addView(
            Glass.chipScroll(main, Effects.effectNames, s.effect) { i ->
                ThemeStore.update { it.copy(effect = i) }
            }
        )
        anim.addView(
            Glass.switchRow(main, "Swipe trail", s.trail) { on ->
                ThemeStore.update { it.copy(trail = on) }
            },
            Glass.lp(main, t = 10)
        )
        col.addView(anim)

        col.addView(Glass.section(main, "SOUND"))
        val sound = Glass.card(main)
        sound.addView(
            Glass.chipScroll(main, Effects.soundNames, s.soundPack) { i ->
                ThemeStore.update { it.copy(soundPack = i) }
                Feedback.playPack(i, KeyType.CHAR)
            }
        )
        sound.addView(
            Glass.slider(main, "Volume", 0, 100, s.soundVolume, { "$it%" }) { v ->
                ThemeStore.update { it.copy(soundVolume = v) }
            },
            Glass.lp(main, t = 10)
        )
        col.addView(sound)

        col.addView(Glass.section(main, "HAPTICS"))
        val haptics = Glass.card(main)
        haptics.addView(
            Glass.chipScroll(main, Effects.hapticNames, s.haptic) { i ->
                ThemeStore.update { it.copy(haptic = i) }
                Feedback.tapWith(this, i, ThemeStore.settings.hapticStrength)
            }
        )
        haptics.addView(
            Glass.slider(main, "Strength", 10, 100, s.hapticStrength, { "$it%" }) { v ->
                ThemeStore.update { it.copy(hapticStrength = v) }
            },
            Glass.lp(main, t = 10)
        )
        col.addView(haptics)
    }
}
