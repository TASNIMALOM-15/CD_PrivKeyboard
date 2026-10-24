package com.example.privkeyboard

/**
 * Simple Avro-style phonetic transliteration (Roman -> Bangla).
 * Case matters: T/D/N/S/R/O/I/U etc. map to different letters, like Avro.
 *
 *   ami -> আমি      bangla -> বাংলা     sundor -> সুন্দর
 *   bhalObasi -> ভালোবাসি     kothay -> কথায়
 */
object BanglaPhonetic {

    // kind: 0 = consonant, 1 = vowel (a = independent, b = kar), 2 = mark
    private class Tok(val kind: Int, val a: String, val b: String = "")

    private val table = HashMap<String, Tok>().apply {
        fun c(k: String, v: String) { put(k, Tok(0, v)) }
        fun v(k: String, ind: String, kar: String) { put(k, Tok(1, ind, kar)) }
        fun m(k: String, v: String) { put(k, Tok(2, v)) }

        // consonants
        c("k", "ক"); c("kh", "খ"); c("g", "গ"); c("gh", "ঘ"); c("Ng", "ঙ")
        c("c", "চ"); c("ch", "ছ"); c("j", "জ"); c("J", "জ"); c("jh", "ঝ"); c("NG", "ঞ")
        c("T", "ট"); c("Th", "ঠ"); c("D", "ড"); c("Dh", "ঢ"); c("N", "ণ")
        c("t", "ত"); c("th", "থ"); c("d", "দ"); c("dh", "ধ"); c("n", "ন")
        c("p", "প"); c("ph", "ফ"); c("f", "ফ"); c("b", "ব"); c("bh", "ভ"); c("v", "ভ")
        c("m", "ম"); c("z", "য"); c("r", "র"); c("l", "ল")
        c("sh", "শ"); c("S", "শ"); c("Sh", "ষ"); c("s", "স"); c("h", "হ")
        c("R", "ড়"); c("Rh", "ঢ়"); c("y", "য়"); c("q", "ক"); c("x", "ক্স")

        // vowels: independent form, kar (dependent sign)
        v("a", "আ", "া"); v("aa", "আ", "া")
        v("i", "ই", "ি"); v("ii", "ঈ", "ী"); v("I", "ঈ", "ী"); v("ee", "ঈ", "ী")
        v("u", "উ", "ু"); v("uu", "ঊ", "ূ"); v("U", "ঊ", "ূ"); v("oo", "উ", "ু")
        v("rri", "ঋ", "ৃ")
        v("e", "এ", "ে"); v("oi", "ঐ", "ৈ")
        v("o", "অ", ""); v("O", "ও", "ো")
        v("ou", "ঔ", "ৌ"); v("OU", "ঔ", "ৌ")

        // marks
        m("ng", "ং"); m(":", "ঃ"); m("^", "ঁ")
    }

    fun convert(input: String): String {
        val out = StringBuilder()
        var afterConsonant = false
        var i = 0
        while (i < input.length) {
            var tok: Tok? = null
            var len = minOf(3, input.length - i)
            while (len >= 1) {
                tok = table[input.substring(i, i + len)]
                if (tok != null) break
                len--
            }
            if (tok == null) {
                val ch = input[i]
                val lower = ch.lowercaseChar()
                if (lower != ch) tok = table[lower.toString()]
                len = 1
            }
            if (tok == null) {
                out.append(input[i])
                afterConsonant = false
                i++
                continue
            }
            when (tok.kind) {
                0 -> {
                    if (tok.a == "য়" && afterConsonant) {
                        out.append("্য")
                    } else {
                        if (afterConsonant) out.append('্')
                        out.append(tok.a)
                    }
                    afterConsonant = true
                }
                1 -> {
                    out.append(if (afterConsonant) tok.b else tok.a)
                    afterConsonant = false
                }
                else -> {
                    out.append(tok.a)
                    afterConsonant = false
                }
            }
            i += len
        }
        return out.toString()
    }
}
