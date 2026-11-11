package com.supernova.launcher.jp.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// JP fork: WeatherForecast, rebuilt. Now (symbol, temperature, 天気 in
// Japanese with English, the place) and four days with their 曜日.
@SuppressLint("ViewConstructor")
class WeatherForecastView(context: Context) : HomeWidget(context, wantsWeather = true) {

    private val symbol = icon(40)
    private val temp = label(34f, FG).apply { typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) }
    private val cond = label(13f, SUB)
    private val place = label(11f, SUB)
    private val dayRows = List(4) { Triple(label(13f, SUB), icon(18), label(13f, FG, bold = true)) }

    init {
        card(this)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val now = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(symbol, LinearLayout.LayoutParams(dp(40), dp(40)))
                addView(temp, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
            })
            addView(cond, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            addView(place)
        }
        val days = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            dayRows.forEachIndexed { i, (name, icon, range) ->
                if (i > 0) addView(android.view.View(context).apply { setBackgroundColor(0x26FFFFFF) },
                    LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1).apply { topMargin = dp(3); bottomMargin = dp(3) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(name, LinearLayout.LayoutParams(dp(30), LayoutParams.WRAP_CONTENT))
                    addView(icon, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) })
                    range.gravity = Gravity.END
                    addView(range, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                })
            }
        }
        row.addView(now, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        row.addView(days, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(12) })
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnClickListener { weatherIntent()?.let { launch(it) } }
    }

    override fun refresh() {
        val w = weather
        glyph(symbol, w?.code, w?.isDay ?: true)
        temp.text = w?.let { "${it.tempC}°" } ?: "–°"
        cond.text = w?.let { "${Weather.japanese(it.code)} · ${Weather.english(it.code)}" } ?: "loading…"
        place.text = "⌖ ${Weather.PLACE}"
        val today = LocalDate.now()
        dayRows.forEachIndexed { i, (name, icon, range) ->
            val d = w?.days?.getOrNull(i)
            val date = today.plusDays(i.toLong())
            name.text = if (i == 0) "今日" else JpCalendar.weekday(date)
            name.setTextColor(if (JpCalendar.holiday(date) != null || date.dayOfWeek.value == 7) ACCENT else SUB)
            glyph(icon, d?.code)
            range.text = d?.let { "${it.max}° ${it.min}°" } ?: ""
        }
    }

    companion object {
        const val KIND = "weather-forecast"
        @Suppress("unused") private val FMT = DateTimeFormatter.ISO_DATE
    }
}
