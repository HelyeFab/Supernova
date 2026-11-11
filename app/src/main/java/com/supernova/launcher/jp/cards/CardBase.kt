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
abstract class CardBase(context: Context, title: String) : LinearLayout(context) {

    protected val header = text(11f, SUB).apply { text = title }
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
            setColorFilter(SUB)
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
        val FG = 0xFFDCDCD8.toInt()
        val SUB = 0xFFBEBEBA.toInt()
        val ACCENT = 0xFFF4B3A6.toInt()
        const val CARD = 0x73303030
        const val EDGE = 0x33FFFFFF
        private val IO = Executors.newFixedThreadPool(2)
    }
}
