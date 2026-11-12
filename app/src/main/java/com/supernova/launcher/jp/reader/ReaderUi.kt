package com.supernova.launcher.jp.reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The reader's look: a sumi-ink sheet in the launcher's own graphite family, warm-white
 * text, and the launcher's salmon accent spent only on the main action and "where am I".
 */
object ReaderUi {
    const val SHEET = 0xFF1B1D1F.toInt()
    const val RAISED = 0xFF26292C.toInt()
    const val LINE = 0xFF34383C.toInt()
    const val INK = 0xFFEDEAE4.toInt()
    const val MUTED = 0xFF9A9EA3.toInt()
    const val RUBY = 0xFFA9AEB4.toInt()
    const val ACCENT = 0xFFF4B3A6.toInt()
    const val ON_ACCENT = 0xFF2A1612.toInt()

    val HEADING: Typeface = Typeface.create("serif", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        cornerRadius = radius; setColor(color)
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

    /** A pressable surface: the shape, plus a soft ripple clipped to it. */
    fun pressable(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0, ripple: Int = 0x26FFFFFF) =
        RippleDrawable(ColorStateList.valueOf(ripple), rounded(color, radius, stroke, strokeColor), rounded(0xFF000000.toInt(), radius))

    fun text(context: Context, size: Float, color: Int = INK, value: CharSequence = "") = TextView(context).apply {
        text = value; textSize = size; setTextColor(color)
    }

    enum class Pill { PRIMARY, SECONDARY, QUIET }

    /** Pill button. PRIMARY is the one accent-filled action on a screen. */
    fun pill(context: Context, label: String, kind: Pill = Pill.SECONDARY, onClick: () -> Unit) = TextView(context).apply {
        text = label; textSize = 15f; gravity = Gravity.CENTER; typeface = MEDIUM
        val r = context.dp(24).toFloat()
        when (kind) {
            Pill.PRIMARY -> { setTextColor(ON_ACCENT); background = pressable(ACCENT, r, ripple = 0x33000000) }
            Pill.SECONDARY -> { setTextColor(INK); background = pressable(RAISED, r) }
            Pill.QUIET -> { setTextColor(INK); background = pressable(0, r, context.dp(1), LINE) }
        }
        minHeight = context.dp(46)
        setPadding(context.dp(18), 0, context.dp(18), 0)
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }

    /** Round icon button for the header. */
    fun iconButton(context: Context, icon: Int, description: String, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(icon); imageTintList = ColorStateList.valueOf(INK)
        scaleType = ImageView.ScaleType.CENTER
        background = pressable(RAISED, context.dp(22).toFloat())
        contentDescription = description
        setOnClickListener { onClick() }
    }

    /** A plain text action, for low-stakes things like Edit text / Share. */
    fun link(context: Context, label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label; textSize = 14f; setTextColor(MUTED); typeface = MEDIUM
        setPadding(context.dp(4), context.dp(12), context.dp(16), context.dp(12))
        background = pressable(0, context.dp(10).toFloat())
        setOnClickListener { onClick() }
    }

    fun sectionTitle(context: Context, value: String) = text(context, 21f, INK, value).apply {
        typeface = HEADING
        setPadding(0, context.dp(28), 0, context.dp(10))
    }

    fun divider(context: Context) = View(context).apply { setBackgroundColor(LINE) }

    fun row(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
}

/** One segment per sentence; the current one is lit. Tap a segment to jump to it. */
@SuppressLint("ViewConstructor")
class SentenceBar(context: Context, private val onPick: (Int) -> Unit) : View(context) {
    var count = 0; set(value) { field = value; invalidate() }
    /** -1 = the whole text. */
    var current = 0; set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val gap = 3 * resources.displayMetrics.density
    private val barHeight = 4 * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        if (count <= 0) return
        val top = (height - barHeight) / 2
        val segment = (width - gap * (count - 1)) / count
        // Too many sentences for distinct segments: one track with a moving marker.
        if (segment < 6 * resources.displayMetrics.density) {
            paint.color = ReaderUi.LINE
            rect.set(0f, top, width.toFloat(), top + barHeight); canvas.drawRoundRect(rect, barHeight, barHeight, paint)
            paint.color = ReaderUi.ACCENT
            if (current < 0) canvas.drawRoundRect(rect, barHeight, barHeight, paint)
            else {
                val w = width.toFloat() / count
                rect.set(w * current, top, w * (current + 1), top + barHeight); canvas.drawRoundRect(rect, barHeight, barHeight, paint)
            }
            return
        }
        for (i in 0 until count) {
            val left = i * (segment + gap)
            paint.color = if (current == -1 || i == current) ReaderUi.ACCENT else ReaderUi.LINE
            rect.set(left, top, left + segment, top + barHeight)
            canvas.drawRoundRect(rect, barHeight / 2, barHeight / 2, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (count <= 0) return false
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            onPick((event.x / width * count).toInt().coerceIn(0, count - 1))
        }
        return true
    }
}

/** Recent knocks as bars against the trigger line; a recognised double gets an accent dot. */
class KnockMeter(context: Context) : View(context) {
    private data class Knock(val peak: Float, var double: Boolean = false)
    private val knocks = ArrayDeque<Knock>()
    var threshold = BackTapDetector.SENSITIVITY_MEDIUM; set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = ReaderUi.ACCENT
        strokeWidth = 1.5f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ReaderUi.MUTED; textSize = 11 * resources.displayMetrics.scaledDensity
    }
    private val rect = RectF()

    fun knock(peak: Float) { knocks.addLast(Knock(peak)); while (knocks.size > SLOTS) knocks.removeFirst(); invalidate() }
    fun double() { knocks.lastOrNull()?.double = true; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val floor = height - 18 * d
        val top = 14 * d
        fun y(v: Float) = floor - (v / SCALE).coerceIn(0f, 1f) * (floor - top)
        paint.color = ReaderUi.RAISED
        rect.set(0f, 0f, width.toFloat(), height.toFloat()); canvas.drawRoundRect(rect, 16 * d, 16 * d, paint)
        val slot = width.toFloat() / SLOTS
        knocks.forEachIndexed { i, k ->
            val cx = slot * i + slot / 2
            paint.color = if (k.peak >= threshold) ReaderUi.INK else ReaderUi.MUTED
            rect.set(cx - 5 * d, y(k.peak), cx + 5 * d, floor); canvas.drawRoundRect(rect, 3 * d, 3 * d, paint)
            if (k.double) { paint.color = ReaderUi.ACCENT; canvas.drawCircle(cx, y(k.peak) - 8 * d, 3.5f * d, paint) }
        }
        val line = y(threshold)
        canvas.drawLine(10 * d, line, width - 10 * d, line, dash)
        canvas.drawText(if (knocks.isEmpty()) "Knock twice on the back to test" else "%.0f m/s² needed".format(threshold),
            12 * d, height - 5 * d, label)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (110 * resources.displayMetrics.density).toInt())

    private companion object { const val SLOTS = 14; const val SCALE = 20f }
}
