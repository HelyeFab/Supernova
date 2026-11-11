package com.supernova.launcher.jp.cards

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

// JP fork: 季節 - the traditional calendar for today: the month's old name
// (和風月名), the current solar term (二十四節気, dates ±1 day) and the next.
@SuppressLint("ViewConstructor")
class SeasonView(context: Context) : CardBase(context, "季節 · the season") {

    private class Term(val start: MonthDay, val name: String, val kana: String, val en: String)

    private val month = text(12f, SUB)
    private val name = text(30f).apply { gravity = Gravity.CENTER }
    private val kana = text(13f, ACCENT).apply { gravity = Gravity.CENTER }
    private val en = text(13f, SUB).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }
    private val next = text(12f, SUB).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }

    init {
        body.addView(month)
        body.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            listOf(name, kana, en, next).forEach { addView(it) }
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun load() {
        val today = LocalDate.now()
        val (m, mk) = MONTHS[today.monthValue - 1]
        month.text = "${today.monthValue}月 · $m（$mk）"
        val md = MonthDay.from(today)
        val idx = TERMS.indexOfLast { !it.start.isAfter(md) }.let { if (it < 0) TERMS.size - 1 else it }
        val t = TERMS[idx]
        name.text = t.name
        kana.text = t.kana
        en.text = t.en
        val n = TERMS[(idx + 1) % TERMS.size]
        var nd = n.start.atYear(today.year)
        if (!nd.isAfter(today)) nd = nd.plusYears(1)
        next.text = "次は ${n.name}（${n.kana}）${nd.monthValue}/${nd.dayOfMonth}頃 · あと${ChronoUnit.DAYS.between(today, nd)}日"
    }

    companion object {
        const val KIND = "jp-season"

        private val MONTHS = listOf(
            "睦月" to "むつき", "如月" to "きさらぎ", "弥生" to "やよい", "卯月" to "うづき",
            "皐月" to "さつき", "水無月" to "みなづき", "文月" to "ふみづき", "葉月" to "はづき",
            "長月" to "ながつき", "神無月" to "かんなづき", "霜月" to "しもつき", "師走" to "しわす"
        )

        private fun t(m: Int, d: Int, name: String, kana: String, en: String) = Term(MonthDay.of(m, d), name, kana, en)

        private val TERMS = listOf(
            t(1, 5, "小寒", "しょうかん", "Lesser cold"),
            t(1, 20, "大寒", "だいかん", "Greater cold - the coldest time of year"),
            t(2, 4, "立春", "りっしゅん", "Beginning of spring"),
            t(2, 19, "雨水", "うすい", "Snow turns to rain"),
            t(3, 5, "啓蟄", "けいちつ", "Hibernating insects awaken"),
            t(3, 20, "春分", "しゅんぶん", "Spring equinox"),
            t(4, 5, "清明", "せいめい", "Clear and bright"),
            t(4, 20, "穀雨", "こくう", "Rain for the grain"),
            t(5, 5, "立夏", "りっか", "Beginning of summer"),
            t(5, 21, "小満", "しょうまん", "Life grows full"),
            t(6, 6, "芒種", "ぼうしゅ", "Sowing of grain"),
            t(6, 21, "夏至", "げし", "Summer solstice"),
            t(7, 7, "小暑", "しょうしょ", "Lesser heat"),
            t(7, 23, "大暑", "たいしょ", "Greater heat"),
            t(8, 7, "立秋", "りっしゅう", "Beginning of autumn"),
            t(8, 23, "処暑", "しょしょ", "The heat eases"),
            t(9, 8, "白露", "はくろ", "White dew"),
            t(9, 23, "秋分", "しゅうぶん", "Autumn equinox"),
            t(10, 8, "寒露", "かんろ", "Cold dew"),
            t(10, 23, "霜降", "そうこう", "First frost"),
            t(11, 7, "立冬", "りっとう", "Beginning of winter"),
            t(11, 22, "小雪", "しょうせつ", "Light snow"),
            t(12, 7, "大雪", "たいせつ", "Heavy snow"),
            t(12, 22, "冬至", "とうじ", "Winter solstice")
        )
    }
}
