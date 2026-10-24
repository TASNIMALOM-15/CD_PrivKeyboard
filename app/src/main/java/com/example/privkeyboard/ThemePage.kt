package com.example.privkeyboard

import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView

/** Theme studio. Everything applies live: the whole app and the keyboard update as you drag. */
class ThemePage(private val main: MainActivity) : ScrollView(main) {

    private val col = LinearLayout(main)

    init {
        clipToPadding = false
        setPadding(Glass.dp(main, 16), Glass.dp(main, 0), Glass.dp(main, 16), Glass.dp(main, 140))
        col.orientation = LinearLayout.VERTICAL
        addView(col)
        build()
    }

    /** Rebuild all controls from the current settings (after a reset / import). */
    fun rebuild() {
        col.removeAllViews()
        build()
        Glass.applyRoles(col)
    }

    private fun build() {
        val s = ThemeStore.settings
        val dark = ThemeStore.isDark(main)

        // ---- live preview: a real keyboard, same renderer as the IME
        col.addView(Glass.section(main, "LIVE PREVIEW"))
        val preview = Glass.card(main)
        preview.setPadding(Glass.dp(main, 4), Glass.dp(main, 4), Glass.dp(main, 4), Glass.dp(main, 4))
        preview.addView(KeyboardView(main), Glass.lp(main))
        col.addView(preview)

        // ---- ready-made looks
        col.addView(Glass.section(main, "THEME COLLECTION"))
        val collection = HorizontalScrollView(main)
        collection.isHorizontalScrollBarEnabled = false
        val strip = LinearLayout(main)
        strip.orientation = LinearLayout.HORIZONTAL
        for (preset in Presets.all) {
            val cell = LinearLayout(main)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(Glass.dp(main, 8), Glass.dp(main, 10), Glass.dp(main, 8), Glass.dp(main, 8))
            cell.background = GlassDrawable(cell, 22f, 1.2f)
            cell.isClickable = true
            cell.addView(
                Swatch(main, Gradients.colors(preset.gradientId, true)),
                LinearLayout.LayoutParams(Glass.dp(main, 52), Glass.dp(main, 52))
            )
            val name = Glass.text(main, preset.name, 11f, ThemeRole.PRIMARY, true)
            name.gravity = Gravity.CENTER
            cell.addView(name, Glass.lp(main, Glass.WRAP, Glass.WRAP, 0, 4, 0, 0))
            cell.setOnClickListener {
                ThemeStore.update { st -> preset.applyTo(st) }
                rebuild()
            }
            strip.addView(cell, Glass.lp(main, Glass.dp(main, 92), Glass.WRAP, 0, 0, 8, 0))
        }
        collection.addView(strip)
        col.addView(collection)

        // ---- appearance
        col.addView(Glass.section(main, "APPEARANCE"))
        val appearance = Glass.card(main)
        appearance.addView(
            Glass.segmented(main, listOf("System", "Light", "Dark"), s.mode) { i ->
                ThemeStore.update { it.copy(mode = i) }
            }
        )
        col.addView(appearance)

        // ---- background
        col.addView(Glass.section(main, "BACKGROUND"))
        val bg = Glass.card(main)
        val swatches = ArrayList<Swatch>()
        for ((i, p) in Gradients.presets.withIndex()) {
            val sw = Swatch(main, if (dark) p.dark else p.light)
            sw.picked = !s.useImage && s.gradientId == i
            swatches.add(sw)
        }
        bg.addView(
            Glass.swatchRow(main, swatches) { i ->
                ThemeStore.update { it.copy(useImage = false, gradientId = i) }
            }
        )

        val buttons = LinearLayout(main)
        buttons.orientation = LinearLayout.HORIZONTAL
        val pickLp = Glass.lp(main, 0, Glass.WRAP, 0, 8, if (s.useImage) 8 else 0, 8)
        pickLp.weight = 1f
        buttons.addView(Glass.button(main, "Choose photo") { main.pickImage() }, pickLp)
        if (s.useImage) {
            val removeLp = Glass.lp(main, 0, Glass.WRAP, 0, 8, 0, 8)
            removeLp.weight = 1f
            buttons.addView(
                Glass.button(main, "Use gradient") {
                    ThemeStore.removeImage()
                    rebuild()
                },
                removeLp
            )
        }
        bg.addView(buttons)

        bg.addView(Glass.text(main, "Image fit", 14f))
        bg.addView(
            Glass.segmented(main, listOf("Cover", "Contain", "Center"), s.fit) { i ->
                ThemeStore.update { it.copy(fit = i) }
            },
            Glass.lp(main, t = 6, b = 8)
        )
        bg.addView(
            Glass.slider(main, "Background blur", 0, 100, s.bgBlur, { "$it%" }) { v ->
                ThemeStore.update { it.copy(bgBlur = v) }
            }
        )
        bg.addView(
            Glass.slider(
                main, "Brightness overlay", -80, 80, s.brightness,
                { if (it > 0) "+$it" else "$it" }
            ) { v ->
                ThemeStore.update { it.copy(brightness = v) }
            }
        )
        col.addView(bg)

        // ---- liquid glass
        col.addView(Glass.section(main, "LIQUID GLASS"))
        val glass = Glass.card(main)
        glass.addView(
            Glass.slider(main, "Glass opacity", 0, 60, s.glassOpacity, { "$it%" }) { v ->
                ThemeStore.update { it.copy(glassOpacity = v) }
            }
        )
        glass.addView(
            Glass.slider(main, "Glass blur", 0, 100, s.glassBlur, { "$it%" }) { v ->
                ThemeStore.update { it.copy(glassBlur = v) }
            }
        )
        glass.addView(Glass.text(main, "Accent glow", 14f))
        val accents = ArrayList<Swatch>()
        for (c in Gradients.accents) {
            val sw = Swatch(main, null, c)
            sw.picked = c == s.accent
            accents.add(sw)
        }
        glass.addView(
            Glass.swatchRow(main, accents) { i ->
                ThemeStore.update { it.copy(accent = Gradients.accents[i]) }
            }
        )
        col.addView(glass)

        // ---- keyboard
        col.addView(Glass.section(main, "KEYBOARD"))
        val kb = Glass.card(main)
        kb.addView(
            Glass.slider(main, "Key height", 80, 130, s.keyHeight, { "$it%" }) { v ->
                ThemeStore.update { it.copy(keyHeight = v) }
            }
        )
        col.addView(kb)

        // ---- reset
        val reset = Glass.button(main, "Reset everything to default") { v: View ->
            GlassDialogs.confirm(v, "Reset theme?", "All theme settings and the chosen photo will be removed.", "Reset") {
                ThemeStore.resetAll()
                rebuild()
            }
        }
        col.addView(reset, Glass.lp(main, t = 16))
    }
}
