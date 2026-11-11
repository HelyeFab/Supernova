package com.supernova.launcher.jp.dict

// JP fork: romaji (Hepburn and wapuro spellings) to hiragana, for dictionary
// search from a Latin keyboard: "taberu" -> たべる, "kyou" -> きょう,
// "gakkou" -> がっこう, "konnichiha"/"kon'nichiwa" -> こんにちは/こんにちわ.
// Returns null unless the whole query converts, so ordinary app queries
// ("chrome", "maps") never reach the dictionary.
object Romaji {

    private val TABLE: Map<String, String> = buildMap {
        val rows = """
            a あ i い u う e え o お
            ka か ki き ku く ke け ko こ ga が gi ぎ gu ぐ ge げ go ご
            sa さ si し shi し su す se せ so そ za ざ zi じ ji じ zu ず ze ぜ zo ぞ
            ta た ti ち chi ち tu つ tsu つ te て to と da だ di ぢ du づ de で do ど
            na な ni に nu ぬ ne ね no の
            ha は hi ひ hu ふ fu ふ he へ ho ほ ba ば bi び bu ぶ be べ bo ぼ pa ぱ pi ぴ pu ぷ pe ぺ po ぽ
            ma ま mi み mu む me め mo も ya や yu ゆ yo よ
            ra ら ri り ru る re れ ro ろ la ら li り lu る le れ lo ろ wa わ wo を we うぇ wi うぃ
            kya きゃ kyu きゅ kyo きょ gya ぎゃ gyu ぎゅ gyo ぎょ
            sha しゃ shu しゅ sho しょ she しぇ sya しゃ syu しゅ syo しょ
            ja じゃ ju じゅ jo じょ je じぇ jya じゃ jyu じゅ jyo じょ zya じゃ zyu じゅ zyo じょ
            cha ちゃ chu ちゅ cho ちょ che ちぇ tya ちゃ tyu ちゅ tyo ちょ
            nya にゃ nyu にゅ nyo にょ hya ひゃ hyu ひゅ hyo ひょ bya びゃ byu びゅ byo びょ
            pya ぴゃ pyu ぴゅ pyo ぴょ mya みゃ myu みゅ myo みょ rya りゃ ryu りゅ ryo りょ
            fa ふぁ fi ふぃ fe ふぇ fo ふぉ ti てぃ di でぃ va ゔぁ vi ゔぃ vu ゔ ve ゔぇ vo ゔぉ
            xa ぁ xi ぃ xu ぅ xe ぇ xo ぉ xtu っ xya ゃ xyu ゅ xyo ょ
            nn ん n' ん - ー
        """.trim().split(Regex("\\s+"))
        for (i in rows.indices step 2) putIfAbsent(rows[i], rows[i + 1])
        // "ti"/"di" as ち/ぢ win over the later てぃ/でぃ (putIfAbsent keeps the first).
    }

    private const val VOWELS = "aeiou"

    fun toHiragana(input: String): String? {
        val s = input.lowercase().trim()
        if (s.isEmpty() || s.any { !(it in 'a'..'z' || it == '\'' || it == '-') }) return null
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            // Doubled consonant -> small tsu (gakkou, matte); not for n.
            if (i + 1 < s.length && c == s[i + 1] && c !in VOWELS && c != 'n' && c.isLetter()) {
                out.append('っ'); i++; continue
            }
            // "nn" before a vowel is ん + a na-row kana (konnichiha), else one ん.
            if (s.startsWith("nn", i)) {
                out.append('ん')
                val next = s.getOrNull(i + 2)
                i += if (next != null && (next in VOWELS || next == 'y')) 1 else 2
                continue
            }
            // tch -> っち (matcha)
            if (s.startsWith("tch", i)) { out.append('っ'); i++; continue }
            var matched = false
            for (len in 3 downTo 1) {
                if (i + len > s.length) continue
                val kana = TABLE[s.substring(i, i + len)] ?: continue
                // A lone n before a consonant (or at the end) is ん.
                if (len == 1 && c == 'n') break
                out.append(kana); i += len; matched = true; break
            }
            if (matched) continue
            if (c == 'n' && (i + 1 == s.length || s[i + 1] !in VOWELS && s[i + 1] != 'y')) {
                out.append('ん'); i++; continue
            }
            return null
        }
        return out.toString()
    }
}
