package com.supernova.launcher.jp.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class JpCalendarTest {

    // Cabinet Office list for 2026, incl. 振替休日 (5/6) and the Silver Week 国民の休日 (9/22).
    @Test
    fun holidays2026() {
        val expected = mapOf(
            "2026-01-01" to "元日", "2026-01-12" to "成人の日", "2026-02-11" to "建国記念の日",
            "2026-02-23" to "天皇誕生日", "2026-03-20" to "春分の日", "2026-04-29" to "昭和の日",
            "2026-05-03" to "憲法記念日", "2026-05-04" to "みどりの日", "2026-05-05" to "こどもの日",
            "2026-05-06" to "振替休日", "2026-07-20" to "海の日", "2026-08-11" to "山の日",
            "2026-09-21" to "敬老の日", "2026-09-22" to "国民の休日", "2026-09-23" to "秋分の日",
            "2026-10-12" to "スポーツの日", "2026-11-03" to "文化の日", "2026-11-23" to "勤労感謝の日"
        ).mapKeys { LocalDate.parse(it.key) }
        assertEquals(expected, JpCalendar.holidays(2026))
    }

    @Test
    fun substituteHolidaysAndEquinoxes2027() {
        val h = JpCalendar.holidays(2027)
        assertEquals("振替休日", h[LocalDate.of(2027, 3, 22)]) // 春分の日 3/21 is a Sunday
        assertEquals("秋分の日", h[LocalDate.of(2027, 9, 23)])
    }

    @Test
    fun eraAndWeekday() {
        assertEquals("令和8年", JpCalendar.era(LocalDate.of(2026, 11, 11)))
        assertEquals("令和元年", JpCalendar.era(LocalDate.of(2019, 6, 1)))
        assertEquals("水", JpCalendar.weekday(LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun nextHolidayCrossesTheYear() {
        assertEquals(LocalDate.of(2027, 1, 1), JpCalendar.nextHoliday(LocalDate.of(2026, 12, 1))?.first)
    }

    @Test
    fun moonPhaseNearKnownDates() {
        fun at(d: String) = LocalDate.parse(d).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        // Full moon 2026-11-24, new moon 2026-11-09 (UTC).
        assertTrue(Moon.illumination(Moon.phase(at("2026-11-24"))) > 0.95)
        assertTrue(Moon.illumination(Moon.phase(at("2026-11-09"))) < 0.05)
    }
}
