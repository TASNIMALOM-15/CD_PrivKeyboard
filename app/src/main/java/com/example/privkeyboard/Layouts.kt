package com.example.privkeyboard

import android.graphics.RectF

enum class KeyType {
    CHAR, SPACE, SHIFT, DELETE, ENTER, GLOBE, EMOJI,
    LAYER_LETTERS, LAYER_SYM1, LAYER_SYM2, SPACER
}

enum class Layer { LETTERS, SYMBOLS1, SYMBOLS2 }

enum class Language(val id: String, val label: String) {
    ENGLISH("en", "English"),
    BANGLA("bn", "বাংলা");

    companion object {
        fun fromId(id: String?): Language = values().firstOrNull { it.id == id } ?: ENGLISH
    }
}

/**
 * extras = characters offered on long-press (each UTF-16 char is one option).
 * hint   = small label drawn in the key's top-right corner.
 */
class Key(
    val label: String,
    val type: KeyType,
    val weight: Float = 1f,
    val extras: String = "",
    val hint: String = ""
) {
    val rect = RectF()

    /** 0..1 illumination (pressed / active), animated by the view. */
    var glow = 0f
}

object Layouts {

    private val accents = mapOf(
        'a' to "àáâäæãåā", 'c' to "çćč", 'd' to "ð", 'e' to "èéêëēėę",
        'i' to "ìíîïīį", 'l' to "ł", 'n' to "ñń", 'o' to "òóôöõøœō",
        's' to "ßśš", 'u' to "ùúûüū", 'y' to "ýÿ", 'z' to "žźż"
    )

    private val symbolExtras = mapOf(
        '1' to "¹½⅓¼⅛", '2' to "²⅔", '3' to "³¾⅜", '4' to "⁴", '5' to "⅝", '7' to "⅞",
        '0' to "ⁿ∅°", '-' to "–—·•", '$' to "€£¥¢₹৳", '(' to "[{<", ')' to "]}>",
        '"' to "“”„«»", '\'' to "‘’‚‹›", '!' to "¡", '?' to "¿", '/' to "\\|",
        '+' to "±", '=' to "≠≈", '*' to "†‡★", '%' to "‰", '#' to "№"
    )

    private fun banglaDigit(d: Char): Char = "০১২৩৪৫৬৭৮৯"[d - '0']

    private fun spacer() = Key("", KeyType.SPACER, 0.5f)

    private fun delete() = Key("", KeyType.DELETE, 1.5f)

    private fun letterKey(c: Char, digit: Char?, lang: Language): Key {
        val sb = StringBuilder()
        var hint = ""
        if (digit != null) {
            if (lang == Language.BANGLA) {
                val bd = banglaDigit(digit)
                sb.append(bd).append(digit)
                hint = bd.toString()
            } else {
                sb.append(digit)
                hint = digit.toString()
            }
        }
        if (lang == Language.ENGLISH) {
            accents[c]?.let { sb.append(it) }
        } else if (c == 't') {
            sb.append("ৎ")
        }
        return Key(c.toString(), KeyType.CHAR, 1f, sb.toString(), hint)
    }

    private fun sym(c: Char, lang: Language): Key {
        var ex = symbolExtras[c] ?: ""
        if (lang == Language.BANGLA && c in '0'..'9') ex = banglaDigit(c) + ex
        return Key(c.toString(), KeyType.CHAR, 1f, ex)
    }

    private fun syms(s: String, lang: Language): List<Key> = s.map { sym(it, lang) }

    private fun periodKey(lang: Language): Key =
        if (lang == Language.BANGLA) Key("।", KeyType.CHAR, 0.9f, ".?!:;-")
        else Key(".", KeyType.CHAR, 0.9f, "?!:;-_@/")

    private fun bottomRow(left: Key, period: Key): List<Key> = listOf(
        left,
        Key(",", KeyType.CHAR, 0.9f),
        Key("", KeyType.GLOBE, 0.9f),
        Key("", KeyType.EMOJI, 0.9f),
        Key(" ", KeyType.SPACE, 3.9f),
        period,
        Key("", KeyType.ENTER, 1.3f)
    )

    private fun letters(lang: Language): List<List<Key>> {
        val digits = "1234567890"
        return listOf(
            "qwertyuiop".mapIndexed { i, c -> letterKey(c, digits[i], lang) },
            listOf(spacer()) + "asdfghjkl".map { letterKey(it, null, lang) } + listOf(spacer()),
            listOf(Key("", KeyType.SHIFT, 1.5f)) + "zxcvbnm".map { letterKey(it, null, lang) } + listOf(delete()),
            bottomRow(Key("?123", KeyType.LAYER_SYM1, 1.2f), periodKey(lang))
        )
    }

    private fun symbols1(lang: Language): List<List<Key>> = listOf(
        syms("1234567890", lang),
        syms("@#\$_&-+()/", lang),
        listOf(Key("=\\<", KeyType.LAYER_SYM2, 1.5f)) + syms("*\"':;!?", lang) + listOf(delete()),
        bottomRow(Key("ABC", KeyType.LAYER_LETTERS, 1.2f), Key(".", KeyType.CHAR, 0.9f, "?!:;-_@/"))
    )

    private fun symbols2(lang: Language): List<List<Key>> = listOf(
        syms("~`|•√π÷×¶∆", lang),
        syms("£€¥^°={}[]", lang),
        listOf(Key("?123", KeyType.LAYER_SYM1, 1.5f)) + syms("%©®™<>\\", lang) + listOf(delete()),
        bottomRow(Key("ABC", KeyType.LAYER_LETTERS, 1.2f), Key(".", KeyType.CHAR, 0.9f, "?!:;-_@/"))
    )

    fun build(lang: Language): Map<Layer, List<List<Key>>> = mapOf(
        Layer.LETTERS to letters(lang),
        Layer.SYMBOLS1 to symbols1(lang),
        Layer.SYMBOLS2 to symbols2(lang)
    )
}
