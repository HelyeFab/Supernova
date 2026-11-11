package com.supernova.launcher.jp.cards

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.util.Xml
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.supernova.launcher.R
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

// JP fork: news in Japanese. First choice: NEWS WEB EASY (やさしいにほんご)
// from his own nhk-easy service (nhk.selfmind.dev, fed by the nhk-easy-task
// fetcher on leonard): headlines with furigana, a one-line outline and NHK's
// narration (▶). When it has nothing from the last two weeks, NHK ONE's
// regular headlines instead. Tap a headline to open the article; ↻ refetches.
@SuppressLint("ViewConstructor")
class NewsView(context: Context) : CardBase(context, "ニュース · NHK") {

    private data class Item(
        val title: CharSequence,
        val outline: String,
        val link: String,
        val audio: String?,
        val at: Long
    )

    private var force = false

    init {
        button(R.drawable.jp_ic_refresh) { force = true; load() }
    }

    override fun load() {
        val ctx = context.applicationContext
        val forced = force.also { force = false }
        io {
            val easy = easyNews(ctx, forced)
            val items = easy.ifEmpty { mainNews(ctx, forced) }
            post {
                header.text = if (easy.isNotEmpty()) "やさしいニュース · NHK EASY" else "ニュース · NHK"
                render(items)
            }
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
                setPadding(0, if (i == 0) 0 else dp(9), 0, 0)
                addView(text(15f).apply {
                    text = item.title
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                    // Room above each line for the furigana.
                    setLineSpacing(textSize * RubySpan.FURIGANA_SPACING, 1f)
                    setPadding(0, (textSize * RubySpan.FURIGANA_SPACING).toInt(), 0, 0)
                })
                if (item.outline.isNotEmpty()) addView(text(12f, SUB).apply {
                    text = item.outline
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(2), 0, 0)
                    addView(text(10f, SUB).apply {
                        text = DateUtils.getRelativeTimeSpanString(item.at, now, DateUtils.MINUTE_IN_MILLIS)
                    }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                    item.audio?.let { url -> addView(playButton(url)) }
                })
                setOnClickListener { open(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
            })
        }
    }

    // ▶ plays NHK's own reading of the article (HLS); tap again to stop.
    private fun playButton(url: String): TextView = text(12f, ACCENT).apply {
        text = if (Player.playing == url) "■ 止める" else "▶ 聞く"
        setPadding(dp(10), dp(4), dp(4), dp(4))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setOnClickListener { Player.toggle(url) { post { load() } } }
    }

    private object Player {
        var playing: String? = null
        private var player: MediaPlayer? = null

        @Synchronized
        fun toggle(url: String, changed: () -> Unit) {
            val was = playing
            stop()
            if (was == url) { changed(); return }
            playing = url
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stop(); changed() }
                setOnErrorListener { _, _, _ -> stop(); changed(); true }
                prepareAsync()
            }
            changed()
        }

        @Synchronized
        fun stop() {
            runCatching { player?.release() }
            player = null
            playing = null
        }
    }

    companion object {
        const val KIND = "jp-news"
        private const val EASY = "https://nhk.selfmind.dev/news"
        private const val FEED = "https://news.web.nhk/n-data/conf/na/rss/cat0.xml"
        private const val MAX_ITEMS = 5
        private const val MAX_AGE_MS = 20 * 60_000L
        private const val EASY_DAYS = 14L
        private const val PREFS = "jp_news"

        private fun cached(ctx: Context, key: String, url: String, forced: Boolean): String? {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = prefs.getString(key, null)
            if (!forced && old != null && System.currentTimeMillis() - prefs.getLong("${key}_at", 0L) < MAX_AGE_MS) return old
            return runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "SupernovaJP/1.0")
                check(conn.responseCode == 200)
                conn.inputStream.use { it.readBytes().decodeToString() }
            }.getOrNull()?.also {
                prefs.edit().putString(key, it).putLong("${key}_at", System.currentTimeMillis()).apply()
            } ?: old
        }

        private fun easyNews(ctx: Context, forced: Boolean): List<Item> {
            val now = Instant.now()
            val from = now.minus(EASY_DAYS, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
            val to = now.plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
            // The API wants millisecond ISO dates ("...T00:00:00.000Z").
            val url = "$EASY?startDate=${iso(from)}&endDate=${iso(to)}"
            val json = cached(ctx, "easy", url, forced) ?: return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                List(arr.length()) { arr.getJSONObject(it) }.map { o ->
                    Item(
                        title = RubySpan.fromHtml(o.optString("titleWithRuby").ifEmpty { o.optString("title") }),
                        outline = o.optString("outline"),
                        link = o.optString("url"),
                        audio = o.optString("m3u8Url").takeIf { it.startsWith("http") },
                        at = runCatching { Instant.parse(o.optString("publishedAtUtc")).toEpochMilli() }.getOrDefault(0L)
                    )
                }.sortedByDescending { it.at }
            }.getOrDefault(emptyList())
        }

        private fun iso(i: Instant) = i.toString().replace("Z", ".000Z").replace(".000.000Z", ".000Z")

        private fun mainNews(ctx: Context, forced: Boolean): List<Item> {
            val xml = cached(ctx, "xml", FEED, forced) ?: return emptyList()
            return runCatching {
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
                            if (title.isNotEmpty()) out += Item(title, "", link, null, at)
                        }
                    }
                }
                out.sortedByDescending { it.at }
            }.getOrDefault(emptyList())
        }
    }
}
