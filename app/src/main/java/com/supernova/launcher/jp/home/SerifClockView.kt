package com.supernova.launcher.jp.home

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// JP fork: SerifClock, rebuilt. The date in Japanese, the time in Abril
// Fatface, then a row of badges: battery ring, tonight's moon, the ZeroClaw
// paw and a weather pill. Taps: date -> calendar, time -> clock, battery ->
// battery usage, moon/weather -> weather app, paw -> the ZeroClaw chat.
@SuppressLint("ViewConstructor")
class SerifClockView(context: Context) : HomeWidget(context, wantsWeather = true) {

    private val date = label(15f, FG).apply { shadow(this); gravity = Gravity.CENTER }
    // Sized from the digits' real ink (Abril overhangs its advance widths, so
    // autosize clipped the last digit): the time's ink spans INK_WIDTH of the
    // widget's width, capped by the height it has.
    private val time = object : TextView(context) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            fitTime(this, w, h)
        }
    }.apply {
        typeface = serif()
        setTextColor(0xFFFFFFFF.toInt())
        includeFontPadding = false
        gravity = Gravity.CENTER
        maxLines = 1
        shadow(this)
    }

    private fun fitTime(view: TextView, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val probe = android.graphics.Paint(view.paint).apply { textSize = PROBE_PX }
        val ink = android.graphics.Rect()
        probe.getTextBounds(WIDEST, 0, WIDEST.length, ink)
        val byWidth = PROBE_PX * w * INK_WIDTH / ink.width()
        val byHeight = PROBE_PX * h * INK_HEIGHT / ink.height()
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, minOf(byWidth, byHeight))
    }

    private val battery = BatteryRingView(context)
    private val moon = MoonView(context)
    private val weatherIcon = icon(26)
    private val weatherTemp = label(15f, FG, bold = true)
    private val weatherPlace = label(11f, SUB)

    init {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        column.addView(date, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(time, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val badges = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val size = dp(BADGE_DP)
        fun gap() = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(8) }
        badges.addView(battery, gap())
        badges.addView(moon, gap())
        if (appIntent(TELEGRAM) != null) {
            badges.addView(ImageView(context).apply {
                setImageResource(com.supernova.launcher.R.drawable.jp_ic_paw)
                scaleType = ImageView.ScaleType.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(PILL)
                }
                setOnClickListener { openZeroClaw() }
            }, gap())
        }
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(14), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = size / 2f
                setColor(PILL)
            }
            addView(weatherIcon, LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(weatherTemp)
                addView(weatherPlace)
            }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
            setOnClickListener { weatherIntent()?.let { launch(it) } }
        }
        badges.addView(pill, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, size))
        column.addView(badges, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        date.setOnClickListener { launch(calendarIntent()) }
        time.setOnClickListener {
            launch(*listOfNotNull(appIntent("com.hihonor.deskclock"), Intent(AlarmClock.ACTION_SHOW_ALARMS)).toTypedArray())
        }
        battery.setOnClickListener { launch(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_SETTINGS)) }
        moon.setOnClickListener { weatherIntent()?.let { launch(it) } }
        listOf(date, time, battery, moon).forEach { v ->
            v.setOnLongClickListener { this@SerifClockView.performLongClick() }
        }
    }

    override fun refresh() {
        val today = LocalDate.now()
        date.text = "${today.monthValue}月${today.dayOfMonth}日(${JpCalendar.weekday(today)})  " +
            today.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
        time.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH.mm"))
        battery.percent = batteryPct
        battery.charging = charging
        moon.phase = Moon.phase(System.currentTimeMillis())
        val w = weather
        glyph(weatherIcon, w?.code, w?.isDay ?: true)
        weatherTemp.text = w?.let { "${it.tempC}°" } ?: "–°"
        weatherPlace.text = Weather.PLACE
    }

    private fun openZeroClaw() {
        val washi = Intent().setComponent(ComponentName("dev.fabiani.washi", "dev.fabiani.washi.OpenChatActivity"))
        launch(washi, Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=emma_myzeroclaw_bot")))
    }

    companion object {
        const val KIND = "serif-clock"
        private const val BADGE_DP = 44
        private const val PROBE_PX = 200f
        private const val WIDEST = "00.00"
        // "Slightly smaller" than edge to edge (asked 2026-11-11).
        private const val INK_WIDTH = 0.90f
        private const val INK_HEIGHT = 1.0f
        private const val TELEGRAM = "org.telegram.messenger"
    }
}
