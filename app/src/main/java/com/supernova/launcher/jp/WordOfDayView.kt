package com.supernova.launcher.jp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.supernova.launcher.BuildConfig
import com.supernova.launcher.jp.dict.JpDict
import com.supernova.launcher.jp.dict.JpDictRowView
import com.supernova.launcher.jp.struggle.CardRepo
import com.supernova.launcher.jp.struggle.Tts
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlin.math.abs

// JP fork: one word a day from three places, swiped between inside the card:
//  Anki  - a card you're learning but haven't mastered (Core 2k/6k, JLPT Tango, kotoba)
//  辞書   - a common JMdict word with kanji
//  Chiba - the sentence Chiba last sent on Telegram
// Anki and dictionary picks hold for the whole day. Tap = hear it (AivisSpeech);
// ↗ = open the word in Akebi, or Chiba for its sentence.
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class WordOfDayView(context: Context) : LinearLayout(context) {

    private enum class Source(val tab: String) { ANKI("Anki"), DICT("辞書"), CHIBA("Chiba") }

    private class Shown(val label: String, val word: String, val reading: String, val meaning: String,
                        val extra: String, val speak: String, val speed: Double = 1.0, val lookup: String? = null)

    private val tabs = Source.entries.associateWith { tab(it.tab) }
    private val label = text(11f, MUTED)
    private val word = text(30f, FG).apply { gravity = Gravity.CENTER }
    private val reading = text(13f, ACCENT).apply { gravity = Gravity.CENTER }
    private val meaning = text(13f, FG).apply { gravity = Gravity.CENTER; maxLines = 2 }
    private val extra = text(12f, MUTED).apply { gravity = Gravity.CENTER; maxLines = 2 }
    private val body = LinearLayout(context)
    private var source = Source.ANKI
    private val shown = mutableMapOf<Source, Shown?>()

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(8), dp(8), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(CARD)
            setStroke(dp(1), EDGE)
        }
        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            label.maxLines = 1
            label.ellipsize = TextUtils.TruncateAt.END
            addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            tabs.forEach { (src, v) -> v.setOnClickListener { show(src) }; addView(v) }
            // ↻: another word on Anki / 辞書 (until tapped again; tomorrow starts fresh),
            // or fetch Chiba's latest sentence right now.
            addView(ImageView(context).apply {
                setImageResource(com.supernova.launcher.R.drawable.jp_ic_refresh)
                setColorFilter(MUTED)
                scaleType = ImageView.ScaleType.CENTER
                setOnClickListener { refreshCurrent() }
            }, LayoutParams(dp(32), dp(28)))
            addView(ImageView(context).apply {
                setImageResource(com.supernova.launcher.R.drawable.jp_ic_expand)
                setColorFilter(MUTED)
                scaleType = ImageView.ScaleType.CENTER
                setOnClickListener { openCurrent() }
            }, LayoutParams(dp(32), dp(28)))
        }
        addView(top)
        body.apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            listOf(word, reading, meaning, extra).forEach { addView(it) }
            word.maxLines = 2
            word.ellipsize = TextUtils.TruncateAt.END
        }
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { speak(); return true }
            override fun onLongPress(e: MotionEvent) { this@WordOfDayView.performLongClick() }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (abs(dx) < dp(40) || abs(vx) < abs(vy)) return false
                val all = Source.entries
                val next = all[Math.floorMod(all.indexOf(source) + if (dx < 0) 1 else -1, all.size)]
                body.animate().alpha(0f).translationX(if (dx < 0) -dp(24).toFloat() else dp(24).toFloat())
                    .setDuration(FADE_MS).withEndAction {
                        show(next)
                        body.translationX = 0f
                        body.animate().alpha(1f).setDuration(FADE_MS).start()
                    }.start()
                return true
            }
        })
        body.setOnTouchListener { v, ev ->
            // Keep sideways swipes inside the card instead of turning the page.
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) v.parent.requestDisallowInterceptTouchEvent(true)
            gestures.onTouchEvent(ev)
        }
        show(Source.ANKI)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) load()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        load()
    }

    private fun refreshCurrent() {
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (source) {
            Source.CHIBA -> prefs.edit().putLong("fetched", 0L).apply()
            else -> {
                val key = skipKey(source, LocalDate.now().toEpochDay())
                prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
            }
        }
        body.animate().alpha(0.3f).setDuration(FADE_MS).start()
        load { body.animate().alpha(1f).setDuration(FADE_MS).start() }
    }

    private fun load(then: () -> Unit = {}) {
        val ctx = context.applicationContext
        val day = LocalDate.now().toEpochDay()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ankiSkip = prefs.getInt(skipKey(Source.ANKI, day), 0)
        val dictSkip = prefs.getInt(skipKey(Source.DICT, day), 0)
        IO.execute {
            val anki = runCatching { CardRepo.dailyCard(ctx, ANKI_SEARCH, day, ankiSkip) }.getOrNull()?.let {
                Shown("Anki · ${it.deck}", it.word, it.reading, it.meaning, it.sentence,
                    it.reading.ifEmpty { it.word }, lookup = it.word)
            }
            val dict = JpDict.daily(ctx, day, dictSkip)?.let {
                Shown("辞書 · ${it.pos}", it.head, it.reading, it.gloss, "", it.reading.ifEmpty { it.head }, lookup = it.head)
            }
            val chiba = chibaPing(ctx)
            post {
                shown[Source.ANKI] = anki
                shown[Source.DICT] = dict
                shown[Source.CHIBA] = chiba
                show(source)
                then()
            }
        }
    }

    private fun show(src: Source) {
        source = src
        tabs.forEach { (s, v) -> v.alpha = if (s == src) 1f else 0.45f }
        val s = shown[src]
        if (s == null) {
            label.text = src.tab
            word.text = "…"
            word.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            reading.text = ""
            meaning.text = when (src) {
                Source.ANKI -> "Nothing in learning (or AnkiDroid not readable)"
                Source.DICT -> "Dictionary not installed"
                Source.CHIBA -> "No ping yet"
            }
            extra.text = ""
            return
        }
        label.text = s.label
        word.text = s.word
        // A Chiba sentence is long: smaller type than a single word.
        word.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (s.word.length > WORD_MAX) 18f else 30f)
        reading.text = s.reading
        reading.visibility = if (s.reading.isEmpty()) GONE else VISIBLE
        meaning.text = s.meaning
        meaning.visibility = if (s.meaning.isEmpty()) GONE else VISIBLE
        extra.text = s.extra
        extra.visibility = if (s.extra.isNotEmpty() && height > dp(EXTRA_MIN_DP)) VISIBLE else GONE
    }

    private fun speak() {
        val s = shown[source] ?: return
        Tts.speakAt(context.applicationContext, s.speak, s.speed) {}
    }

    private fun openCurrent() {
        val s = shown[source] ?: return
        if (s.lookup != null) {
            JpDictRowView.open(context, s.lookup)
            return
        }
        // Chiba: its web app (WebAPK), found by the URL it owns.
        val view = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(CHIBA_URL))
        runCatching { context.startActivity(view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun tab(name: String) = text(11f, FG).apply {
        text = name
        setPadding(dp(6), dp(4), dp(6), dp(4))
    }

    private fun text(sp: Float, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        includeFontPadding = false
        setPadding(0, dp(1), 0, dp(1))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val KIND = "word-of-day"
        private val FG = 0xFFDCDCD8.toInt()
        private val MUTED = 0xFFBEBEBA.toInt()
        private val ACCENT = 0xFFF4B3A6.toInt()
        private const val CARD = 0x73303030
        private const val EDGE = 0x33FFFFFF
        private const val FADE_MS = 120L
        private const val WORD_MAX = 8
        private const val EXTRA_MIN_DP = 170
        private const val CHIBA_URL = "https://chiba.appsparkle.org/"
        private const val PING_SPEED = 0.8
        private const val PING_REFRESH_MS = 5 * 60_000L
        private const val PREFS = "jp_word_of_day"

        // Learning but not yet mature (interval under 3 weeks), not new, not suspended.
        private const val ANKI_SEARCH = "(\"deck:Core 2k/6k Optimized JP Vocab (JouzuJuls)\" OR \"deck:JLPT Tango\" " +
            "OR deck:kotoba) -is:new -is:suspended prop:ivl<21"

        private val IO = Executors.newSingleThreadExecutor()

        // Refresh taps count per source per day, so a new day starts at its own pick.
        private fun skipKey(source: Source, day: Long) = "skip_${source.name}_$day"

        // The latest ping, fetched at most every 5 minutes; the last one stays on failure.
        private fun chibaPing(ctx: Context): Shown? {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - prefs.getLong("fetched", 0L) > PING_REFRESH_MS && BuildConfig.CHIBA_WIDGET_TOKEN.isNotEmpty()) {
                runCatching {
                    val conn = URL(BuildConfig.CHIBA_PING_URL).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.setRequestProperty("X-Chiba-Widget-Token", BuildConfig.CHIBA_WIDGET_TOKEN)
                    conn.setRequestProperty("User-Agent", "SupernovaJP/1.0")
                    if (conn.responseCode == 200) {
                        val ping = JSONObject(conn.inputStream.use { it.readBytes().decodeToString() }).optJSONObject("ping")
                        val jp = ping?.optString("japanese").orEmpty()
                        if (jp.isNotBlank()) {
                            prefs.edit().putString("japanese", jp)
                                .putString("sentAt", ping?.optString("sentAt").orEmpty()).apply()
                        }
                    }
                    prefs.edit().putLong("fetched", now).apply()
                }
            }
            val jp = prefs.getString("japanese", null) ?: return null
            val ago = prefs.getString("sentAt", null)?.let { iso ->
                runCatching {
                    DateUtils.getRelativeTimeSpanString(Instant.parse(iso).toEpochMilli(), now, DateUtils.MINUTE_IN_MILLIS)
                }.getOrNull()
            }
            return Shown("Chiba" + (ago?.let { " · $it" } ?: ""), jp, "", "", "", jp, PING_SPEED)
        }
    }
}
