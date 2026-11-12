package com.supernova.launcher.jp.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderLanguageTest {
    @Test fun `conjugated verbs retain surface but look up dictionary form`() {
        val words = ReaderLanguage.words("昨日、食べさせられた。")
        val verb = words.first { it.base == "食べる" }
        assertEquals("食べさせられた", verb.surface)
        assertEquals("たべさせられた", verb.reading)
        assertTrue(words.zipWithNext().all { (a, b) -> a.end <= b.start })
    }

    @Test fun `tap offsets preserve whitespace newlines and emoji before Japanese`() {
        val source = "🍙  日本語を\n 勉強しました。"
        val words = ReaderLanguage.words(source)
        assertTrue(words.any { it.base == "日本語" })
        words.forEach { assertEquals(it.surface, source.substring(it.start, it.end)) }
        ReaderLanguage.readings(source).forEach { assertEquals(it.surface, source.substring(it.start, it.end)) }
        assertTrue(words.zipWithNext().all { (a, b) -> a.end <= b.start })
    }

    @Test fun `contextual reading is not dictionary pronunciation`() {
        val reading = ReaderLanguage.readings("明日、来た人に聞いて。")
        assertEquals("き", reading.first { it.surface == "来" }.reading)
    }

    @Test fun `sentence navigation keeps punctuation and closing quotes`() {
        assertEquals(listOf("「日本語を読む。」", "楽しい！", "次の行"), ReaderText.sentences("「日本語を読む。」楽しい！\n次の行"))
        assertEquals(emptyList<String>(), ReaderText.sentences(" \n "))
    }

    @Test fun `furigana sit over the kanji only`() {
        assertEquals(Triple(0, 1, "み"), ReaderLanguage.rubyCore("見る", "みる"))
        assertEquals(Triple(0, 1, "うつく"), ReaderLanguage.rubyCore("美しい", "うつくしい"))
        assertEquals(Triple(1, 2, "ちゃ"), ReaderLanguage.rubyCore("お茶", "おちゃ"))
        assertEquals(Triple(0, 2, "けしき"), ReaderLanguage.rubyCore("景色", "けしき"))
        assertEquals(Triple(0, 3, "きょうとふ"), ReaderLanguage.rubyCore("京都府", "きょうとふ"))
    }
}
