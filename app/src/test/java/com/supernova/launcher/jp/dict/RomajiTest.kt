package com.supernova.launcher.jp.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomajiTest {

    @Test
    fun basicWords() {
        assertEquals("たべる", Romaji.toHiragana("taberu"))
        assertEquals("ねこ", Romaji.toHiragana("neko"))
        assertEquals("すし", Romaji.toHiragana("sushi"))
        assertEquals("ちず", Romaji.toHiragana("chizu"))
        assertEquals("つくえ", Romaji.toHiragana("tsukue"))
        assertEquals("ふじ", Romaji.toHiragana("fuji"))
    }

    @Test
    fun youonAndSmallTsu() {
        assertEquals("きょう", Romaji.toHiragana("kyou"))
        assertEquals("がっこう", Romaji.toHiragana("gakkou"))
        assertEquals("まっちゃ", Romaji.toHiragana("matcha"))
        assertEquals("しゃしん", Romaji.toHiragana("shashin"))
    }

    @Test
    fun syllabicN() {
        assertEquals("こんにちは", Romaji.toHiragana("konnichiha"))
        assertEquals("ほん", Romaji.toHiragana("hon"))
        assertEquals("せんせい", Romaji.toHiragana("sensei"))
        assertEquals("きんえん", Romaji.toHiragana("kin'en"))
        assertEquals("こんや", Romaji.toHiragana("kon'ya"))
        assertEquals("にゃ", Romaji.toHiragana("nya"))
    }

    @Test
    fun appQueriesDoNotConvert() {
        assertNull(Romaji.toHiragana("chrome"))
        assertNull(Romaji.toHiragana("maps"))
        assertNull(Romaji.toHiragana("photos"))
        assertNull(Romaji.toHiragana("x2"))
    }
}
