package com.supernova.launcher.jp.cards

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

// JP fork: the shared shell of the feed cards - graphite card, a header with
// a title and optional ↻ / ↗ buttons, and a body. Same palette as jp/home.
@SuppressLint("ViewConstructor")
abstract class CardBase(context: Context, title: String) : LinearLayout(context), FeedThemed {

    // Instance colours: the home-screen look by default, the feed card's own
    // text colour once applyFeedColors runs (see CardViewBinder).
    protected var FG = DEFAULT_FG
    protected var SUB = DEFAULT_SUB
    protected var ACCENT = DEFAULT_ACCENT

    protected val header = text(11f, DEFAULT_SUB).apply { text = title }
    protected val body = LinearLayout(context).apply { orientation = VERTICAL }
    private val buttons = LinearLayout(context).apply { orientation = HORIZONTAL }

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(10), dp(10), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(CARD)
            setStroke(dp(1), EDGE)
        }
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(header, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(buttons)
        })
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(6) })
    }

    protected fun button(icon: Int, onClick: () -> Unit) {
        buttons.addView(ImageView(context).apply {
            setImageResource(icon)
            setColorFilter(this@CardBase.SUB)
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { onClick() }
        }, LayoutParams(dp(32), dp(28)))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        load()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) load()
    }

    // Called whenever the card comes into view; fetch off the main thread with io().
    abstract fun load()

    protected fun io(work: () -> Unit) = IO.execute(work)

    // Inside a feed card: no panel of our own, text in the card's colour and
    // accents dark enough for a light card. Recolours what is already drawn.
    override fun applyFeedColors(text: Int) {
        val light = androidx.core.graphics.ColorUtils.calculateLuminance(text) < 0.5
        val map = mapOf(
            FG to text,
            SUB to ((0xB3 shl 24) or (text and 0xFFFFFF)),
            ACCENT to if (light) 0xFFB23A2E.toInt() else DEFAULT_ACCENT
        )
        background = null
        setPadding(0, paddingTop / 2, 0, paddingBottom / 2)
        recolour(this, map)
        FG = map.getValue(FG); SUB = map.getValue(SUB); ACCENT = map.getValue(ACCENT)
    }

    private fun recolour(view: android.view.View, map: Map<Int, Int>) {
        when (view) {
            is TextView -> map[view.currentTextColor]?.let(view::setTextColor)
            is ImageView -> view.setColorFilter(map.getValue(SUB))
        }
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) recolour(view.getChildAt(i), map)
    }

    protected fun text(sp: Float, color: Int = FG) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        includeFontPadding = false
    }

    protected fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    protected fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        val DEFAULT_FG = 0xFFDCDCD8.toInt()
        val DEFAULT_SUB = 0xFFBEBEBA.toInt()
        val DEFAULT_ACCENT = 0xFFF4B3A6.toInt()
        const val CARD = 0x73303030
        const val EDGE = 0x33FFFFFF
        private val IO = Executors.newFixedThreadPool(2)
    }
}
