package com.supernova.launcher.jp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

// JP fork: several built-in widgets in one slot, one showing at a time.
// Swipe up or down on it to rotate to the next card (sideways swipes stay
// with the cards: word-of-the-day tabs, Struggle's skip). Dots on the right
// edge show where you are; the card on top is remembered per placed stack.
@SuppressLint("ViewConstructor")
class StackWidgetView(context: Context, private val itemId: String, cards: List<View>) : FrameLayout(context) {

    private val cards = cards
    private var index = prefs().getInt(key(), 0).coerceIn(0, (cards.size - 1).coerceAtLeast(0))
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var downX = 0f
    private var downY = 0f
    private var rotating = false
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
        cards.forEachIndexed { i, card ->
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            card.visibility = if (i == index) VISIBLE else INVISIBLE
            // A long press anywhere on a card is the stack's (move, resize, remove).
            card.setOnLongClickListener { this@StackWidgetView.performLongClick() }
        }
    }

    // Watches every touch, even ones the cards claim for themselves: once a
    // drag is clearly vertical the card gets a cancel and the stack takes it.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; rotating = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (!rotating) {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dy) > slop && abs(dy) > abs(dx) * 1.5f) {
                    rotating = true
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
            }
            MotionEvent.ACTION_UP -> if (rotating) {
                rotate(if (ev.y < downY) 1 else -1)
                rotating = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> rotating = false
        }
        if (rotating) return true
        return super.dispatchTouchEvent(ev)
    }

    private fun rotate(step: Int) {
        if (cards.size < 2) return
        val from = cards[index]
        index = Math.floorMod(index + step, cards.size)
        prefs().edit().putInt(key(), index).apply()
        val to = cards[index]
        val h = height.toFloat()
        // Up: the next card rises from below; down: the previous drops from above.
        to.translationY = if (step > 0) h * 0.4f else -h * 0.4f
        to.alpha = 0f
        to.visibility = VISIBLE
        to.animate().translationY(0f).alpha(1f).setDuration(ANIM_MS).start()
        from.animate().translationY(if (step > 0) -h * 0.4f else h * 0.4f).alpha(0f).setDuration(ANIM_MS)
            .withEndAction { from.visibility = INVISIBLE; from.translationY = 0f; from.alpha = 1f }.start()
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (cards.size < 2) return
        val d = resources.displayMetrics.density
        val r = 2.5f * d
        val gap = 9f * d
        val x = width - 7f * d
        val top = height / 2f - gap * (cards.size - 1) / 2f
        cards.indices.forEach { i ->
            dot.color = if (i == index) 0xEEFFFFFF.toInt() else 0x66FFFFFF
            canvas.drawCircle(x, top + i * gap, r, dot)
        }
    }

    private fun prefs() = context.getSharedPreferences("jp_stack", Context.MODE_PRIVATE)
    private fun key() = "top_$itemId"

    companion object {
        const val KIND = "jp-stack"
        private const val ANIM_MS = 220L
    }
}
