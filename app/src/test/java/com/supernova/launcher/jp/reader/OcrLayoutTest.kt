package com.supernova.launcher.jp.reader

import com.supernova.launcher.jp.reader.OcrLayout.Line
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLayoutTest {
    @Test fun `small kana lines above the body are furigana and dropped`() {
        val page = listOf(listOf(
            Line("きょうとふ　びょうき", 22),
            Line("京都府は、この病気が", 44),
            Line("ばしょ　ひろ", 21),
            Line("ほかの場所に広がりました。", 45),
        ))
        assertEquals("京都府は、この病気がほかの場所に広がりました。", OcrLayout.text(page))
    }

    @Test fun `a body line written only in kana is kept`() {
        val page = listOf(listOf(Line("これはテストです。", 44), Line("ありがとう", 43)))
        assertEquals("これはテストです。ありがとう", OcrLayout.text(page))
    }

    @Test fun `small lines with kanji are kept`() {
        val page = listOf(listOf(Line("本文の行です。", 44)), listOf(Line("注：小さい字", 20)))
        assertEquals("本文の行です。\n注：小さい字", OcrLayout.text(page))
    }

    @Test fun `blocks stay on their own lines`() {
        val page = listOf(listOf(Line("見出し", 50)), listOf(Line("本文です。", 44)))
        assertEquals("見出し\n本文です。", OcrLayout.text(page))
    }

    @Test fun `furigana are dropped even when they outnumber the body lines`() {
        // NHK Easy: every reading comes back as its own short line or block.
        val page = listOf(
            listOf(Line("きょうとふかめおかし", 22)), listOf(Line("にち", 21)), listOf(Line("のうか", 22)),
            listOf(Line("そだ", 21)), listOf(Line("京都府亀岡市で11日、農家で育てている", 46)),
            listOf(Line("にわとり", 22)), listOf(Line("し", 20)), listOf(Line("み", 21)),
            listOf(Line("鶏が死んでいるのが見つかりました。", 45)),
        )
        assertEquals("京都府亀岡市で11日、農家で育てている鶏が死んでいるのが見つかりました。", OcrLayout.text(page))
    }
}
