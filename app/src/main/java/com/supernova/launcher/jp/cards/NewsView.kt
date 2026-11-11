package com.supernova.launcher.jp.cards

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Xml
import android.widget.LinearLayout
import com.supernova.launcher.R
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

// JP fork: NHK's latest headlines (NHK ONE main news RSS), refreshed at most
// every 20 minutes. Tap a headline to open the article; ↻ refetches now.
// (NHK's easy-Japanese site sits behind a consent gate, so it isn't used.)
@SuppressLint("ViewConstructor")
class NewsView(context: Context) : CardBase(context, "ニュース · NHK") {

    private data class Item(val title: String, val link: String, val at: Long)

    init {
        button(R.drawable.jp_ic_refresh) { force = true; load() }
    }

    private var force = false

    override fun load() {
        val ctx = context.applicationContext
        val forced = force.also { force = false }
        io {
            val items = fetch(ctx, forced)
            post { render(items) }
        }
    }

    private fun render(items: List<Item>) {
        body.removeAllViews()
        if (items.isEmpty()) {
            body.addView(text(13f, SUB).apply { text = "ニュースを読み込めません · couldn't load the news" })
            return
        }
        val now = System.currentTimeMillis()
        items.take(MAX_ITEMS).forEachIndexed { i, item ->
            body.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, if (i == 0) 0 else dp(7), 0, 0)
                addView(text(15f).apply {
                    text = item.title
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(text(10f, SUB).apply {
                    text = DateUtils.getRelativeTimeSpanString(item.at, now, DateUtils.MINUTE_IN_MILLIS)
                    setPadding(0, dp(2), 0, 0)
                })
                setOnClickListener { open(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
            })
        }
    }

    companion object {
        const val KIND = "jp-news"
        private const val FEED = "https://news.web.nhk/n-data/conf/na/rss/cat0.xml"
        private const val MAX_ITEMS = 5
        private const val MAX_AGE_MS = 20 * 60_000L
        private const val PREFS = "jp_news"

        private fun fetch(ctx: Context, forced: Boolean): List<Item> {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cached = prefs.getString("xml", null)
            val fresh = !forced && cached != null &&
                System.currentTimeMillis() - prefs.getLong("at", 0L) < MAX_AGE_MS
            val xml = if (fresh) cached else runCatching {
                val conn = URL(FEED).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "SupernovaJP/1.0")
                check(conn.responseCode == 200)
                conn.inputStream.use { it.readBytes().decodeToString() }
            }.getOrNull()?.also {
                prefs.edit().putString("xml", it).putLong("at", System.currentTimeMillis()).apply()
            } ?: cached
            return xml?.let(::parse).orEmpty()
        }

        private fun parse(xml: String): List<Item> = runCatching {
            val p = Xml.newPullParser()
            p.setInput(xml.reader())
            val out = ArrayList<Item>()
            var title = ""; var link = ""; var date = ""; var inItem = false
            val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "item" -> { inItem = true; title = ""; link = ""; date = "" }
                        "title" -> if (inItem) title = p.nextText().trim()
                        "link" -> if (inItem) link = p.nextText().trim()
                        "pubDate" -> if (inItem) date = p.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (p.name == "item") {
                        inItem = false
                        val at = runCatching { fmt.parse(date)?.time }.getOrNull() ?: 0L
                        if (title.isNotEmpty()) out += Item(title, link, at)
                    }
                }
            }
            out.sortedByDescending { it.at }
        }.getOrDefault(emptyList())
    }
}
