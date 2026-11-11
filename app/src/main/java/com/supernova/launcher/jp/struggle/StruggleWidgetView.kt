package com.supernova.launcher.jp.struggle

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors
import kotlin.math.abs

// JP fork: the Struggle widget as part of the launcher. Reviews the
// "Struggling" filtered deck (owned by the desktop Shiba Dash add-on) through
// AnkiDroid's queue - see Queue for why only the head can be answered.
//
// Tap the card to reveal; Again/Hard/Good/Easy grade it; swipe it left (or ⤼)
// to skip, which buries it until tomorrow. Listening cards: the first tap plays
// the word, the next reveals. ⤢ opens the full review screen. Re-reads the
// queue whenever the home screen comes back into view.
@SuppressLint("ViewConstructor")
class StruggleWidgetView(context: Context) : FrameLayout(context) {

    // Ink follows the frost: dark ink on the milky day glass, light ink on the night glass.
    private val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    private val TEXT = if (night) 0xFFE8EFEC.toInt() else 0xFF14211F.toInt()
    private val MUTED = if (night) 0xFFAFC0BB.toInt() else 0xFF41524E.toInt()
    private val ACCENT = if (night) 0xFF7FD6C2.toInt() else 0xFF16705F.toInt()

    private val glass = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private val header = text(11f, MUTED)
    private val word = text(26f, TEXT).apply { gravity = Gravity.CENTER }
    private val reading = text(12f, ACCENT).apply { gravity = Gravity.CENTER }
    private val meaning = text(13f, MUTED).apply { gravity = Gravity.CENTER }
    private val sentence = text(12f, MUTED).apply { gravity = Gravity.CENTER }
    private val card = LinearLayout(context)
    private val grades = LinearLayout(context)
    private val gradeButtons = List(4) { text(12f, Color.WHITE) }
    private val skip = icon(com.supernova.launcher.R.drawable.jp_ic_skip)

    private var state: Queue.State? = null
    private var shownKey: String? = null
    private var revealed = false
    private var heard = false
    private var shownAt = 0L
    private var flash: String? = null
    private var busy = false
    private val onBus: () -> Unit = { reload() }

    init {
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, dp(RADIUS_DP).toFloat())
        }
        background = GradientDrawable().apply {
            cornerRadius = dp(RADIUS_DP).toFloat()
            colors = if (night) intArrayOf(0xCC1E2826.toInt(), 0x99141A19.toInt())
            else intArrayOf(0xC8FFFFFF.toInt(), 0x99EEF5F3.toInt())
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dp(1), 0x80FFFFFF.toInt())
        }
        addView(glass, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(10))
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            header.maxLines = 1
            header.ellipsize = TextUtils.TruncateAt.END
            addView(header, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(icon(com.supernova.launcher.R.drawable.jp_ic_refresh).apply {
                setOnClickListener { flash = null; reload() }
            }, LinearLayout.LayoutParams(dp(32), dp(28)))
            addView(icon(com.supernova.launcher.R.drawable.jp_ic_expand).apply {
                setOnClickListener { openFull() }
            }, LinearLayout.LayoutParams(dp(32), dp(28)))
        }
        column.addView(top)

        val middle = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        card.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(word); addView(reading); addView(meaning); addView(sentence)
            listOf(word, reading, meaning, sentence).forEach { it.maxLines = 2; it.ellipsize = TextUtils.TruncateAt.END }
        }
        middle.addView(card, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        skip.setOnClickListener { act(skip = true, ease = 0) }
        middle.addView(skip, LinearLayout.LayoutParams(dp(36), dp(36)))
        column.addView(middle, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        grades.orientation = LinearLayout.HORIZONTAL
        gradeButtons.forEachIndexed { i, b ->
            b.gravity = Gravity.CENTER
            b.maxLines = 1
            b.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(PILLS[i]) }
            b.setOnClickListener { act(skip = false, ease = i + 1) }
            grades.addView(b, LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                marginStart = if (i == 0) 0 else dp(4)
            })
        }
        column.addView(grades, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(6)
        })
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val swipe = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { tapCard(); return true }
            override fun onLongPress(e: MotionEvent) { this@StruggleWidgetView.performLongClick() }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (dx < -dp(48) && abs(vx) > abs(vy)) { swipeAway(); return true }
                return false
            }
        })
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        card.setOnTouchListener { v, ev ->
            // Claim horizontal drags so the workspace doesn't change page under the card.
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) v.parent.requestDisallowInterceptTouchEvent(true)
            if (ev.actionMasked == MotionEvent.ACTION_MOVE && ev.historySize > 0) {
                val dy = abs(ev.y - ev.getHistoricalY(0))
                if (dy > slop) v.parent.requestDisallowInterceptTouchEvent(false)
            }
            swipe.onTouchEvent(ev)
        }
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        StruggleBus.add(onBus)
        reload()
    }

    override fun onDetachedFromWindow() {
        StruggleBus.remove(onBus)
        super.onDetachedFromWindow()
    }

    // Home shown again (back from AnkiDroid, screen on): the queue may have moved.
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) reload()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) post { updateFrost() }
    }

    private fun updateFrost() {
        if (width == 0 || !isAttachedToWindow) return
        val at = IntArray(2).also(::getLocationOnScreen)
        val onScreen = Rect(at[0], at[1], at[0] + width, at[1] + height)
        val root = rootView
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val ctx = context.applicationContext
        val radius = dp(RADIUS_DP).toFloat()
        IO.execute {
            val bmp = Frost.render(ctx, onScreen, root.width, root.height, radius, night)
            post { glass.setImageDrawable(bmp?.let { BitmapDrawable(resources, it) }) }
        }
    }

    private fun reload() {
        val ctx = context.applicationContext
        IO.execute {
            val s = Queue.state(ctx)
            post { state = s; render() }
        }
    }

    private fun tapCard() {
        val ready = state as? Queue.State.Ready ?: run { openAnki(); return }
        val c = ready.head.card
        if (c.isListening && !heard && !revealed) {
            heard = true
            Tts.speak(context.applicationContext, c.reading.ifEmpty { c.word }) { msg -> post { flash = "⚠ audio: $msg"; render() } }
            render()
            return
        }
        revealed = !revealed
        if (revealed) Tts.speak(context.applicationContext, c.reading.ifEmpty { c.word })
        render()
    }

    private fun swipeAway() {
        if (state !is Queue.State.Ready || busy) return
        card.animate().translationX(-width.toFloat()).alpha(0f).setDuration(SWIPE_MS).withEndAction {
            card.translationX = 0f
            card.alpha = 1f
            act(skip = true, ease = 0)
        }.start()
    }

    private fun act(skip: Boolean, ease: Int) {
        val ready = state as? Queue.State.Ready ?: return
        if (busy) return
        busy = true
        val c = ready.head.card
        val took = System.currentTimeMillis() - shownAt
        val ctx = context.applicationContext
        IO.execute {
            val res = if (skip) Queue.skip(ctx, c) else Queue.grade(ctx, c, ease, took)
            val what = if (skip) "skipped" else CardRepo.EASE_LABELS[(ease - 1).coerceIn(0, 3)]
            val note = when (res) {
                Queue.Result.OK -> "✓ ${c.word} $what"
                Queue.Result.MOVED_ON -> "queue moved on"
                Queue.Result.NOT_SAVED -> "⚠ not saved"
            }
            val next = Queue.state(ctx)
            post {
                busy = false
                flash = note
                state = next
                render()
                StruggleBus.changed()
            }
        }
    }

    private fun render() {
        val s = state
        if (s is Queue.State.Ready) {
            val c = s.head.card
            if (c.key != shownKey) {
                shownKey = c.key; revealed = false; heard = false
                shownAt = System.currentTimeMillis()
            }
            header.text = (flash?.let { "$it · " } ?: "") + "${c.deck} · ${c.cardName} · ${s.head.remaining} left"
            skip.visibility = VISIBLE
            if (!revealed) {
                word.text = if (c.isListening) "🎧" else c.word
                word.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                reading.visibility = GONE
                meaning.text = when {
                    c.isListening && !heard -> "Listening card · tap to hear it"
                    c.isListening -> "tap again to reveal"
                    else -> "tap to reveal · swipe ← to skip"
                }
                meaning.setTextColor(MUTED)
                sentence.visibility = GONE
                grades.visibility = GONE
            } else {
                word.text = c.word
                word.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
                reading.text = c.reading
                reading.visibility = if (c.reading.isEmpty()) GONE else VISIBLE
                meaning.text = c.meaning
                meaning.setTextColor(TEXT)
                // The example sentence only where there is room for it.
                sentence.text = c.sentence
                sentence.visibility = if (c.sentence.isNotEmpty() && height > dp(SENTENCE_MIN_DP)) VISIBLE else GONE
                grades.visibility = VISIBLE
                gradeButtons.forEachIndexed { k, b ->
                    val ivl = s.head.intervals.getOrNull(k)
                    b.text = CardRepo.EASE_LABELS[k] + (ivl?.let { " $it" } ?: "")
                }
            }
        } else {
            val (title, sub) = when (s) {
                null -> "…" to ""
                Queue.State.NoDeck -> "No “${Queue.DECK_NAME}” deck" to "Create it in AnkiDroid as a filtered deck"
                Queue.State.Empty -> "All done 🎉" to "Rebuild “${Queue.DECK_NAME}” in AnkiDroid for more"
                is Queue.State.Error -> "Can't read AnkiDroid" to s.message
                is Queue.State.Ready -> "" to ""
            }
            header.text = (flash?.let { "$it · " } ?: "") + "Struggling"
            word.text = title
            word.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            reading.visibility = GONE
            meaning.text = sub
            meaning.setTextColor(MUTED)
            sentence.visibility = GONE
            grades.visibility = GONE
            skip.visibility = GONE
        }
    }

    private fun openFull() = runCatching {
        context.startActivity(Intent(context, ReviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openAnki() = runCatching {
        context.packageManager.getLaunchIntentForPackage(ANKI)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun text(sp: Float, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        includeFontPadding = false
        setPadding(0, dp(1), 0, dp(1))
    }

    private fun icon(res: Int) = ImageView(context).apply {
        setImageResource(res)
        setColorFilter(MUTED)
        scaleType = ImageView.ScaleType.CENTER
        val ripple = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }
        setBackgroundResource(ripple.resourceId)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val KIND = "struggle"
        private const val ANKI = "com.ichi2.anki"
        private const val RADIUS_DP = 22
        private const val SENTENCE_MIN_DP = 190
        private const val SWIPE_MS = 180L
        private val PILLS = intArrayOf(0xD9C8463D.toInt(), 0xD9D9822B.toInt(), 0xD92E8B57.toInt(), 0xD92F6FB5.toInt())
        private val IO = Executors.newSingleThreadExecutor()
    }
}
