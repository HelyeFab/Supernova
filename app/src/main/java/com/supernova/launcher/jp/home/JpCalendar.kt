package com.supernova.launcher.jp.home

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

// JP fork: Japanese dates for the home widgets - 曜日, 令和 years and the
// national holidays (祝日) under the current law (2020 onwards), with
// substitute holidays (振替休日) and sandwiched citizens' holidays (国民の休日).
object JpCalendar {

    val WEEKDAYS = arrayOf("月", "火", "水", "木", "金", "土", "日")

    fun weekday(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    // 令和 began on 2019-05-01; 2019 is 令和元年.
    fun era(date: LocalDate): String {
        val y = date.year - 2018
        return if (y == 1) "令和元年" else "令和${y}年"
    }

    private val cache = HashMap<Int, Map<LocalDate, String>>()

    @Synchronized
    fun holidays(year: Int): Map<LocalDate, String> = cache.getOrPut(year) { compute(year) }

    fun holiday(date: LocalDate): String? = holidays(date.year)[date]

    // The next holiday on or after [from], looking into next year if needed.
    fun nextHoliday(from: LocalDate): Pair<LocalDate, String>? =
        (holidays(from.year) + holidays(from.year + 1)).entries
            .filter { !it.key.isBefore(from) }
            .minByOrNull { it.key }
            ?.toPair()

    private fun compute(y: Int): Map<LocalDate, String> {
        fun d(m: Int, day: Int) = LocalDate.of(y, m, day)
        fun nthMonday(m: Int, n: Int) =
            d(m, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY))
        val base = sortedMapOf(
            d(1, 1) to "元日",
            nthMonday(1, 2) to "成人の日",
            d(2, 11) to "建国記念の日",
            d(2, 23) to "天皇誕生日",
            d(3, vernalEquinox(y)) to "春分の日",
            d(4, 29) to "昭和の日",
            d(5, 3) to "憲法記念日",
            d(5, 4) to "みどりの日",
            d(5, 5) to "こどもの日",
            nthMonday(7, 3) to "海の日",
            d(8, 11) to "山の日",
            nthMonday(9, 3) to "敬老の日",
            d(9, autumnalEquinox(y)) to "秋分の日",
            nthMonday(10, 2) to "スポーツの日",
            d(11, 3) to "文化の日",
            d(11, 23) to "勤労感謝の日"
        )
        val all = HashMap<LocalDate, String>(base)
        // 国民の休日: an ordinary day between two holidays.
        for (h in base.keys) {
            val gap = h.plusDays(1)
            if (gap !in base && h.plusDays(2) in base && gap.dayOfWeek != DayOfWeek.SUNDAY) all[gap] = "国民の休日"
        }
        // 振替休日: a holiday on a Sunday moves to the next day that isn't one.
        for (h in base.keys.filter { it.dayOfWeek == DayOfWeek.SUNDAY }) {
            var sub = h.plusDays(1)
            while (sub in all) sub = sub.plusDays(1)
            all[sub] = "振替休日"
        }
        return all
    }

    // The usual approximation, good for 1980-2099.
    private fun vernalEquinox(y: Int) = floor(20.8431 + 0.242194 * (y - 1980) - floor((y - 1980) / 4.0)).toInt()
    private fun autumnalEquinox(y: Int) = floor(23.2488 + 0.242194 * (y - 1980) - floor((y - 1980) / 4.0)).toInt()
}
