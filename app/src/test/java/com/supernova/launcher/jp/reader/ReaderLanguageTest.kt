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
}
