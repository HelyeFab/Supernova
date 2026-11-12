package com.supernova.launcher.jp.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListSectionsTest {

    @Test
    fun latinNames() {
        assertEquals("A", ListSections.sectionOf("AnkiDroid"))
        assertEquals("C", ListSections.sectionOf("Crédit Agricole Italia"))
        assertEquals("C", ListSections.sectionOf("cuscon"))
        assertEquals("#", ListSections.sectionOf("2FA"))
    }

    @Test
    fun kanaNamesByGojuonRow() {
        assertEquals("か", ListSections.sectionOf("ことばランド"))
        assertEquals("あ", ListSections.sectionOf("アニメ・マンガの日本語"))
        assertEquals("か", ListSections.sectionOf("ガイド"))
        assertEquals("は", ListSections.sectionOf("ポケモン"))
        assertEquals("わ", ListSections.sectionOf("をかし"))
    }

    @Test
    fun kanjiNames() {
        assertEquals("漢", ListSections.sectionOf("日本語"))
    }

    @Test
    fun orderLatinThenOtherThenKanaThenKanji() {
        assertTrue(ListSections.rank("Z") < ListSections.rank("#"))
        assertTrue(ListSections.rank("#") < ListSections.rank("あ"))
        assertTrue(ListSections.rank("わ") < ListSections.rank("漢"))
    }
}
