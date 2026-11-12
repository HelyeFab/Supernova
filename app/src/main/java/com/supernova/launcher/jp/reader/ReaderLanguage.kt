package com.supernova.launcher.jp.reader

import com.atilika.kuromoji.ipadic.Tokenizer

/** Offline morphology adapted to the launcher's existing JMdict, without a second dictionary. */
object ReaderLanguage {
    private val tokenizer by lazy { Tokenizer() }
    data class Word(
        val start: Int,
        val end: Int,
        val surface: String,
        val base: String,
        val reading: String?,
        val pos: String
    )

    /** Positions refer to the unchanged source, including whitespace and line breaks. */
    fun words(text: String): List<Word> {
        val tokens = tokenizer.tokenize(text)
        val result = mutableListOf<Word>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.surface.isBlank() || token.partOfSpeechLevel1 == "記号") { i++; continue }
            var end = token.position + token.surface.length
            val reading = StringBuilder(token.reading.takeUnless { it.isNullOrBlank() || it == "*" }.orEmpty())
            var next = i + 1
            // Include conjugation auxiliaries in the clickable verb/adjective surface.
            if (token.partOfSpeechLevel1 in setOf("動詞", "形容詞")) {
                while (next < tokens.size && tokens[next].position == end &&
                    (tokens[next].partOfSpeechLevel1 == "助動詞" ||
                        (tokens[next].partOfSpeechLevel1 == "動詞" && tokens[next].partOfSpeechLevel2 == "接尾"))) {
                    end += tokens[next].surface.length
                    reading.append(tokens[next].reading.takeUnless { it.isNullOrBlank() || it == "*" }.orEmpty())
                    next++
                }
            }
            result.add(Word(token.position, end, text.substring(token.position, end),
                token.baseForm.takeUnless { it.isNullOrBlank() || it == "*" } ?: token.surface,
                reading.toString().takeIf { it.isNotBlank() }?.let(::hiragana),
                token.partOfSpeechLevel1))
            i = next
        }
        return result
    }

    /** Individual morpheme readings retain the actual conjugated pronunciation. */
    fun readings(text: String): List<Word> = tokenizer.tokenize(text).mapNotNull { token ->
        val reading = token.reading.takeUnless { it.isNullOrBlank() || it == "*" } ?: return@mapNotNull null
        if (token.surface.none(::isKanji)) return@mapNotNull null
        Word(token.position, token.position + token.surface.length, token.surface, token.surface,
            hiragana(reading), token.partOfSpeechLevel1)
    }

    fun hiragana(text: String): String = text.map { if (it in 'ァ'..'ヶ') it - 0x60 else it }.joinToString("")
    fun isKanji(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿'
}
