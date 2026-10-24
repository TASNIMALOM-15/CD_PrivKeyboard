package com.example.privkeyboard

/** Names for the effect / sound / haptic options (index = stored value). */
object Effects {
    val effectNames = listOf("None", "Ripple", "Glow burst", "Bounce", "Pop")
    val soundNames = listOf("Off", "System", "Soft click", "Mechanical", "Typewriter", "Bubble", "Glass", "Wood")
    val hapticNames = listOf("System", "Off", "Soft", "Crisp", "Heavy", "Double tick")
}

/** A ready-made look: background, glass strength, accent, key effect, sound and haptics. */
class ThemePreset(
    val name: String,
    val gradientId: Int,
    val opacity: Int,
    val blur: Int,
    val accent: Long,
    val effect: Int,
    val sound: Int,
    val haptic: Int,
    val bgBlur: Int = 0
) {
    fun applyTo(s: ThemeSettings): ThemeSettings = s.copy(
        useImage = false,
        gradientId = gradientId,
        bgBlur = bgBlur,
        brightness = 0,
        glassOpacity = opacity,
        glassBlur = blur,
        accent = accent.toInt(),
        effect = effect,
        soundPack = sound,
        haptic = haptic,
        imageVersion = s.imageVersion + 1
    )
}

object Presets {
    private const val NONE = ThemeSettings.EFFECT_NONE
    private const val RIPPLE = ThemeSettings.EFFECT_RIPPLE
    private const val BURST = ThemeSettings.EFFECT_BURST
    private const val BOUNCE = ThemeSettings.EFFECT_BOUNCE
    private const val POP = ThemeSettings.EFFECT_POP

    val all: List<ThemePreset> = listOf(
        ThemePreset("Aurora Glass", 2, 25, 60, 0xFF4DD6B0, RIPPLE, 6, 3),
        ThemePreset("Ocean Depth", 1, 25, 60, 0xFF4FC3F7, BURST, 5, 2),
        ThemePreset("Sunset Neon", 3, 22, 55, 0xFFFF8A65, BOUNCE, 2, 3),
        ThemePreset("Midnight Steel", 4, 20, 65, 0xFFA27BFF, RIPPLE, 3, 4),
        ThemePreset("Forest Mist", 5, 28, 70, 0xFFA6E34F, POP, 7, 2),
        ThemePreset("Rose Quartz", 6, 26, 60, 0xFFFF7BAC, BURST, 2, 2),
        ThemePreset("Graphite Pro", 7, 18, 50, 0xFFFFFFFF, NONE, 4, 3),
        ThemePreset("Violet Dream", 8, 24, 65, 0xFFA27BFF, BURST, 5, 5),
        ThemePreset("Peach Cream", 9, 30, 55, 0xFFFF8A65, BOUNCE, 2, 2),
        ThemePreset("Mint Fresh", 10, 30, 55, 0xFF4DD6B0, RIPPLE, 6, 2),
        ThemePreset("Frosted Clear", 0, 14, 90, 0xFF4FC3F7, RIPPLE, 1, 0),
        ThemePreset("Neon Pulse", 4, 32, 40, 0xFFA6E34F, BURST, 3, 5, 10)
    )
}
