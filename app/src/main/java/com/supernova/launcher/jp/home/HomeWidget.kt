package com.supernova.launcher.jp.home

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.supernova.launcher.R
import java.util.concurrent.Executors

// JP fork: base of the KWGT replacements. Redraws on every minute tick, date
// or zone change and battery change while on screen, and fetches weather off
// the main thread when the home screen is shown. Being views in the launcher
// window they can't go stale the way hosted KWGT widgets did under Octopi.
@SuppressLint("ViewConstructor")
abstract class HomeWidget(context: Context, private val wantsWeather: Boolean) : FrameLayout(context) {

    protected var weather: Weather.Report? = null
    protected var batteryPct: Int = -1
    protected var charging = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_BATTERY_CHANGED) readBattery(i)
            refresh()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        context.registerReceiver(receiver, filter)?.let(::readBattery)
        refresh()
        loadWeather()
    }

    override fun onDetachedFromWindow() {
        runCatching { context.unregisterReceiver(receiver) }
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            refresh()
            loadWeather()
        }
    }

    private fun readBattery(i: Intent) {
        val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) batteryPct = level * 100 / scale
        val status = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    private fun loadWeather() {
        if (!wantsWeather) return
        val ctx = context.applicationContext
        IO.execute {
            val w = Weather.get(ctx)
            post { weather = w; refresh() }
        }
    }

    abstract fun refresh()

    // ---- shared look: his KWGT palette

    protected fun card(view: View) {
        view.background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(CARD)
            setStroke(dp(1), EDGE)
        }
    }

    protected fun label(sp: Float, color: Int = FG, bold: Boolean = false) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        includeFontPadding = false
        if (bold) typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
    }

    // A weather icon for [code], [sizeDp] square, in the foreground colour.
    protected fun glyph(view: android.widget.ImageView, code: Int?, isDay: Boolean = true) {
        view.setImageDrawable(code?.let { WeatherGlyph(WeatherGlyph.forCode(it, isDay)) })
    }

    protected fun icon(sizeDp: Int) = android.widget.ImageView(context).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    protected fun serif(): Typeface =
        ResourcesCompat.getFont(context, R.font.jp_abril_fatface) ?: Typeface.SERIF

    protected fun shadow(t: TextView) = t.setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), SHADOW)

    protected fun launch(vararg intents: Intent) {
        for (i in intents) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    protected fun appIntent(pkg: String): Intent? = context.packageManager.getLaunchIntentForPackage(pkg)

    protected fun calendarIntent(): Intent =
        appIntent("com.google.android.calendar") ?: appIntent("com.hihonor.calendar")
            ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)

    protected fun weatherIntent(): Intent? = appIntent("com.hihonor.android.totemweather")

    protected fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        val FG = 0xFFDCDCD8.toInt()
        val SUB = 0xFFBEBEBA.toInt()
        val ACCENT = 0xFFE05A47.toInt()
        val SATURDAY = 0xFF8FB4E0.toInt()
        const val CARD = 0x73303030
        const val EDGE = 0x33FFFFFF
        const val PILL = 0x59303030
        val SHADOW = Color.argb(140, 0, 0, 0)
        val IO = Executors.newSingleThreadExecutor()
    }
}
