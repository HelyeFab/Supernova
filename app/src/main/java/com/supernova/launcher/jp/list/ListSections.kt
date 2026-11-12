package com.supernova.launcher.jp.list

import java.text.Normalizer

// JP fork, list mode: which scrubber section an app name belongs to, and the
// order of the sections. Latin names go under A-Z (accents folded: "Crédit"
// -> C), digits and symbols under "#", kana names under the gojūon row of
// their first sound (あかさたなはまやらわ, katakana and small kana folded,
// voiced kana with their row: が -> か), and names starting with kanji under 漢.
object ListSections {

    const val OTHER = "#"
    const val KANJI = "漢"
    val KANA_ROWS = listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ")

    // Every section in scrubber order; only the ones with apps are shown.
    val ORDER: List<String> = ('A'..'Z').map { it.toString() } + OTHER + KANA_ROWS + KANJI

    private val ROW_OF: Map<Char, String> = buildMap {
        val rows = mapOf(
            "あ" to "ぁあぃいぅうゔぇえぉお",
            "か" to "かがきぎくぐけげこご",
            "さ" to "さざしじすずせぜそぞ",
            "た" to "ただちぢっつづてでとど",
            "な" to "なにぬねの",
            "は" to "はばぱひびぴふぶぷへべぺほぼぽ",
            "ま" to "まみむめも",
            "や" to "ゃやゅゆょよ",
            "ら" to "らりるれろ",
            "わ" to "ゎわゐゑをん"
        )
        rows.forEach { (row, chars) -> chars.forEach { put(it, row) } }
    }

    fun sectionOf(label: String): String {
        val c = label.trimStart().firstOrNull() ?: return OTHER
        if (c in 'ァ'..'ヶ') return ROW_OF[c - 0x60] ?: OTHER
        if (c == 'ヴ') return "あ"
        ROW_OF[c]?.let { return it }
        if (c in '㐀'..'鿿' || c == '々') return KANJI
        val folded = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first().uppercaseChar()
        return if (folded in 'A'..'Z') folded.toString() else OTHER
    }

    fun rank(section: String): Int = ORDER.indexOf(section).let { if (it < 0) ORDER.size else it }
}
