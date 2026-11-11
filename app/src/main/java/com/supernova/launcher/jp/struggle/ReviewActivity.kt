package com.supernova.launcher.jp.struggle

// JP fork: ported from ~/Dev/anki-struggle-widget (dev.fabiani.strugglewidget).

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * Full-screen reviewer for the "Struggling" filtered deck: shows the card at the front of
 * AnkiDroid's queue (rendered from AnkiDroid's own front/back HTML), grades it through
 * Queue (which verifies AnkiDroid really saved it), then moves to the next head.
 */
class ReviewActivity : Activity() {
    private lateinit var title: TextView
    private lateinit var web: WebView
    private lateinit var showBtn: Button
    private lateinit var grades: LinearLayout
    private val gradeButtons = ArrayList<Button>()

    private var card: StruggleCard? = null
    private var fields: Map<String, String> = emptyMap()
    private var questionSounds: List<String> = emptyList()
    private var shownAt = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        title = TextView(this).apply {
            textSize = 14f; setTextColor(Color.parseColor("#16705F"))
            setPadding(dp(20), dp(16), dp(8), dp(8))
        }
        val skip = TextView(this).apply {
            text = "Skip"; textSize = 15f; setTextColor(Color.parseColor("#41524E"))
            setPadding(dp(12), dp(12), dp(8), dp(8)); setOnClickListener { skip() }
        }
        val close = TextView(this).apply {
            text = "✕"; textSize = 20f; setTextColor(Color.parseColor("#41524E"))
            setPadding(dp(16), dp(12), dp(20), dp(8)); setOnClickListener { finish() }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); addView(skip); addView(close)
        }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
            addJavascriptInterface(Bridge(), "Android")
        }
        showBtn = Button(this).apply {
            text = "Show answer"; textSize = 17f; isAllCaps = false
            setTextColor(Color.WHITE); background = pill("#16705F", dp(14).toFloat())
            setOnClickListener { reveal() }
        }
        grades = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        val colors = listOf("#C8463D", "#D9822B", "#2E8B57", "#2F6FB5")
        CardRepo.EASE_LABELS.forEachIndexed { i, label ->
            val b = Button(this).apply {
                text = label; textSize = 15f; isAllCaps = false; setTextColor(Color.WHITE)
                background = pill(colors[i], dp(14).toFloat())
                setOnClickListener { grade(i + 1) }
            }
            gradeButtons += b
            grades.addView(b, LinearLayout.LayoutParams(0, dp(60), 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24))
            addView(showBtn, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))
            addView(grades, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F8F7"))
            addView(top)
            addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(bottom)
            // JP fork: targetSdk 36 is edge-to-edge; keep clear of the system bars.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        })
        // JP fork: dark status/nav icons on the light page.
        val light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(light, light)
        loadHead()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        loadHead()
    }

    override fun onStop() {
        super.onStop()
        Tts.stop()
    }

    /** Ask AnkiDroid for the current head of the Struggling queue and show it. */
    private fun loadHead() {
        thread {
            val q = Queue.state(this)
            runOnUiThread {
                when (q) {
                    is Queue.State.Ready -> show(q.head)
                    Queue.State.Empty -> done("All struggling cards done 🎉 Rebuild “${Queue.DECK_NAME}” in AnkiDroid for more.")
                    Queue.State.NoDeck -> done("There's no “${Queue.DECK_NAME}” filtered deck in AnkiDroid.")
                    is Queue.State.Error -> done("Can't read AnkiDroid: ${q.message}")
                }
            }
        }
    }

    private fun done(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun show(head: Queue.Head) {
        val c = head.card
        card = c
        title.text = "${c.deck} · ${c.cardName} · ${head.remaining} left"
        gradeButtons.forEachIndexed { k, b ->
            b.text = CardRepo.EASE_LABELS[k] + (head.intervals.getOrNull(k)?.let { "\n$it" } ?: "")
            b.isEnabled = true
        }
        showBtn.visibility = View.VISIBLE
        grades.visibility = View.GONE
        thread {
            fields = CardRepo.noteFields(this, c.noteId)
            val html = CardRepo.cardHtml(this, c.noteId, c.ord)
            runOnUiThread {
                if (html == null) { Toast.makeText(this, "AnkiDroid didn't return this card", Toast.LENGTH_LONG).show(); return@runOnUiThread }
                questionSounds = sounds(html.first)
                render(html.first)
                web.tag = html.second
                shownAt = SystemClock.elapsedRealtime()
                questionSounds.firstOrNull()?.let(::play)
            }
        }
    }

    private fun reveal() {
        val answer = web.tag as? String ?: return
        render(answer)
        showBtn.visibility = View.GONE
        grades.visibility = View.VISIBLE
        // Like Anki: play the sounds that are new on the answer side.
        sounds(answer).firstOrNull { it !in questionSounds }?.let(::play)
    }

    private fun grade(ease: Int) {
        val c = card ?: return
        val took = SystemClock.elapsedRealtime() - shownAt
        act(c, CardRepo.EASE_LABELS[ease - 1]) { Queue.grade(this, c, ease, took) }
    }

    private fun skip() {
        val c = card ?: return
        act(c, "skipped until tomorrow") { Queue.skip(this, c) }
    }

    private fun act(c: StruggleCard, what: String, op: () -> Queue.Result) {
        gradeButtons.forEach { it.isEnabled = false }
        Tts.stop()
        thread {
            val res = try { op() } catch (e: Exception) { Queue.Result.NOT_SAVED }
            StruggleBus.changed()
            runOnUiThread {
                Toast.makeText(this, when (res) {
                    Queue.Result.OK -> "${c.word} → $what"
                    Queue.Result.MOVED_ON -> "The queue moved on — here's the current card"
                    Queue.Result.NOT_SAVED -> "⚠ AnkiDroid didn't save that — try again or review in AnkiDroid"
                }, Toast.LENGTH_SHORT).show()
                if (res == Queue.Result.NOT_SAVED) gradeButtons.forEach { it.isEnabled = true } else loadHead()
            }
        }
    }

    private fun render(cardHtml: String) {
        val body = cardHtml
            .replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("\\[sound:([^\\]]+)]")) {
                val f = it.groupValues[1].replace("'", "\\'")
                "<button class='snd' onclick=\"Android.play('$f')\">▶</button>"
            }
        val page = """
            <html><head><meta name='viewport' content='width=device-width, initial-scale=1'>
            <style>
              body { font-family: 'Noto Sans JP', sans-serif; text-align: center; color: #14211F;
                     font-size: 22px; padding: 12px 18px; margin: 0; background: transparent; }
              img { display: none; }   /* AnkiDroid's media folder is private to AnkiDroid */
              hr { border: 0; border-top: 1px solid #C9D8D4; margin: 18px 0; }
              svg { max-width: 100%; }
              .snd { font-size: 20px; width: 52px; height: 52px; border-radius: 26px; border: 0;
                     background: #DDEBE8; color: #16705F; margin: 6px; }
            </style></head><body>$body</body></html>
        """.trimIndent()
        web.loadDataWithBaseURL("about:blank", page, "text/html", "utf-8", null)
    }

    private fun sounds(html: String) = Regex("\\[sound:([^\\]]+)]").findAll(html).map { it.groupValues[1] }.toList()

    /** Which words does this mp3 hold? Find the field it lives in and speak that field's text. */
    private fun textFor(file: String): String {
        val c = card ?: return ""
        val holder = fields.entries.firstOrNull { it.value.contains("[sound:$file]") }?.key
        return when (holder) {
            "Sentence Audio" -> CardRepo.plainJapanese(fields["Sentence"])
            "Audio" -> CardRepo.plainJapanese(fields["Expression"]) // Core: the word; Tango: the sentence
            else -> c.word
        }.ifBlank { c.word }
    }

    private fun play(file: String) = Tts.speak(this, textFor(file)) { err ->
        runOnUiThread { Toast.makeText(this, "Audio failed: $err", Toast.LENGTH_SHORT).show() }
    }

    inner class Bridge {
        @JavascriptInterface fun play(file: String) = runOnUiThread { this@ReviewActivity.play(file) }
    }

    private fun pill(color: String, r: Float) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = r }

    companion object {
        fun intent(ctx: Context) = Intent(ctx, ReviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
