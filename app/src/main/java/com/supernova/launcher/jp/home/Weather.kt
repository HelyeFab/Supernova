package com.supernova.launcher.jp.home

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// JP fork: current weather and a 4-day forecast from Open-Meteo (free, no
// key) for one fixed place, cached for 30 minutes in prefs. The last good
// reading stays on any failure. Callers run off the main thread.
object Weather {

    // Where the widgets report on: his KWGT weather pill read "Medicina".
    const val PLACE = "Medicina"
    private const val LAT = 44.4817
    private const val LON = 11.6372
    private const val MAX_AGE_MS = 30 * 60_000L
    private const val PREFS = "jp_weather"

    data class Day(val code: Int, val max: Int, val min: Int)
    data class Report(val tempC: Int, val code: Int, val isDay: Boolean, val days: List<Day>, val fetchedAt: Long)

    fun get(context: Context): Report? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString("json", null)?.let { parse(it, prefs.getLong("at", 0L)) }
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < MAX_AGE_MS) return cached
        val fresh = runCatching { fetch() }.getOrNull()
        if (fresh != null) {
            val now = System.currentTimeMillis()
            prefs.edit().putString("json", fresh).putLong("at", now).apply()
            return parse(fresh, now) ?: cached
        }
        return cached
    }

    private fun fetch(): String {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=4"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "SupernovaJP/1.0")
        check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
        return conn.inputStream.use { it.readBytes().decodeToString() }
    }

    private fun parse(json: String, at: Long): Report? = runCatching {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val codes = daily.getJSONArray("weather_code")
        val maxes = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        Report(
            tempC = Math.round(cur.getDouble("temperature_2m")).toInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            days = List(codes.length()) {
                Day(codes.getInt(it), Math.round(maxes.getDouble(it)).toInt(), Math.round(mins.getDouble(it)).toInt())
            },
            fetchedAt = at
        )
    }.getOrNull()

    // WMO weather codes -> Japanese and English labels (icons: WeatherGlyph).
    fun japanese(code: Int): String = when (code) {
        0 -> "快晴"; 1 -> "晴れ"; 2 -> "晴れ時々曇り"; 3 -> "曇り"
        45, 48 -> "霧"
        in 51..57 -> "霧雨"
        in 61..67 -> "雨"
        in 71..77 -> "雪"
        in 80..82 -> "にわか雨"
        85, 86 -> "にわか雪"
        in 95..99 -> "雷雨"
        else -> "—"
    }

    fun english(code: Int): String = when (code) {
        0 -> "Clear"; 1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> ""
    }
}
