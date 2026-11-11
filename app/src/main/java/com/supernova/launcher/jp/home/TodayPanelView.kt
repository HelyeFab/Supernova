package com.supernova.launcher.jp.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// JP fork: TodayPanel, rebuilt. Month and 令和 year, the day number large in
// Abril Fatface with its 曜日, and beside it the weather and the next 祝日.
@SuppressLint("ViewConstructor")
class TodayPanelView(context: Context) : HomeWidget(context, wantsWeather = true) {

    private val month = label(12f, SUB, bold = true)
    private val era = label(11f, SUB)
    private val day = TextView(context).apply {
        typeface = serif()
        setTextColor(FG)
        includeFontPadding = false
        setAutoSizeTextTypeUniformWithConfiguration(24, 120, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
        gravity = Gravity.CENTER
        maxLines = 1
    }
    private val weekday = label(13f, SUB, bold = true)
    private val weatherIcon = icon(22)
    private val weatherLine = label(18f, FG, bold = true)
    private val place = label(11f, SUB)
    private val holiday = label(12f, FG).apply { maxLines = 2 }
    private val clockLine = label(12f, SUB)

    init {
        card(this)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val left = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(month)
            addView(era)
            // Autosize needs a definite box: the column's full width.
            addView(day, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(weekday)
        }
        val right = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(weatherIcon, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(6) })
                addView(weatherLine)
            })
            addView(place)
            addView(holiday, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(clockLine, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        row.addView(left, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        row.addView(right, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.4f).apply { marginStart = dp(12) })
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnClickListener { launch(calendarIntent()) }
        right.setOnClickListener { weatherIntent()?.let { launch(it) } }
        right.setOnLongClickListener { this@TodayPanelView.performLongClick() }
    }

    override fun refresh() {
        val today = LocalDate.now()
        month.text = today.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)).uppercase()
        era.text = JpCalendar.era(today)
        day.text = today.dayOfMonth.toString()
        weekday.text = "${JpCalendar.weekday(today)}曜日 · ${today.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).uppercase()}"
        val w = weather
        glyph(weatherIcon, w?.code, w?.isDay ?: true)
        weatherLine.text = w?.let { "${it.tempC}°  ${Weather.japanese(it.code)}" } ?: "…"
        place.text = Weather.PLACE
        holiday.text = JpCalendar.holiday(today)?.let { "今日は$it 🎌" } ?: JpCalendar.nextHoliday(today.plusDays(1))?.let { (d, name) ->
            val days = ChronoUnit.DAYS.between(today, d)
            "次の祝日 ${d.monthValue}/${d.dayOfMonth} $name（あと${days}日）"
        }.orEmpty()
        clockLine.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH.mm"))
    }

    companion object {
        const val KIND = "today-panel"
    }
}
