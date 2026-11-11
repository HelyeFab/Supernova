package com.supernova.launcher.jp.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

// JP fork: MonthGrid, rebuilt as a Japanese calendar. Monday-first with
// 月火水木金土日, Saturdays blue, Sundays and 祝日 red, today ringed in the
// accent colour, and the month's holidays named along the bottom.
@SuppressLint("ViewConstructor")
class MonthGridView(context: Context) : HomeWidget(context, wantsWeather = false) {

    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUB; typeface = Typeface.DEFAULT_BOLD }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUB }
    private var today = LocalDate.now()

    init {
        card(this)
        setWillNotDraw(false)
        setOnClickListener { launch(calendarIntent()) }
    }

    override fun refresh() {
        today = LocalDate.now()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = dp(14).toFloat()
        val w = width - 2 * pad
        val first = today.withDayOfMonth(1)
        val lead = first.dayOfWeek.value - 1
        val days = today.lengthOfMonth()
        val rows = (lead + days + 6) / 7
        val holidays = JpCalendar.holidays(today.year).filterKeys { it.month == today.month }.toSortedMap()

        val titleSize = min(dp(14).toFloat(), height / 11f)
        title.textSize = titleSize
        var y = pad + titleSize
        canvas.drawText(
            "${today.monthValue}月  ${today.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)).uppercase()} · ${JpCalendar.era(today)}",
            pad, y, title
        )
        val footSize = titleSize * 0.85f
        val footLines = if (holidays.isEmpty()) 0 else 1
        val gridTop = y + titleSize * 0.8f
        val gridBottom = height - pad - footLines * footSize * 1.4f
        val cellW = w / 7
        val cellH = (gridBottom - gridTop) / (rows + 1)
        val text = min(cellH * 0.5f, cellW * 0.42f)

        head.textSize = text * 0.85f
        for (c in 0 until 7) {
            head.color = colorFor(c, holiday = false, dim = true)
            val cx = pad + cellW * (c + 0.5f)
            canvas.drawText(JpCalendar.WEEKDAYS[c], cx, gridTop + cellH * 0.65f, head)
        }
        num.textSize = text
        for (d in 1..days) {
            val idx = lead + d - 1
            val r = idx / 7
            val c = idx % 7
            val date = first.withDayOfMonth(d)
            val cx = pad + cellW * (c + 0.5f)
            val cy = gridTop + cellH * (r + 1.5f)
            val isToday = date == today
            if (isToday) canvas.drawCircle(cx, cy, min(cellW, cellH) * 0.42f, ring)
            num.color = if (isToday) 0xFFFFFFFF.toInt() else colorFor(c, date in holidays, dim = date.isBefore(today))
            num.typeface = if (isToday || date in holidays) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(d.toString(), cx, cy - (num.ascent() + num.descent()) / 2, num)
        }
        if (holidays.isNotEmpty()) {
            foot.textSize = footSize
            val line = holidays.entries.joinToString("  ") { "${it.key.dayOfMonth}日 ${it.value}" }
            canvas.drawText(line, pad, height - pad, foot)
        }
    }

    private fun colorFor(col: Int, holiday: Boolean, dim: Boolean): Int {
        val base = when {
            holiday || col == DayOfWeek.SUNDAY.value - 1 -> ACCENT
            col == DayOfWeek.SATURDAY.value - 1 -> SATURDAY
            else -> FG
        }
        return if (dim) (base and 0x00FFFFFF) or 0x8C000000.toInt() else base
    }

    companion object {
        const val KIND = "month-grid"
    }
}
