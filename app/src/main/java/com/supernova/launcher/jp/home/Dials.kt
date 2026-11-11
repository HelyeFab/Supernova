package com.supernova.launcher.jp.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min

// JP fork: the small round badges of the clock widget, drawn directly.

// The moon as it looks tonight: a dark disc with the lit part bounded by the
// limb on one side and the terminator (a half-ellipse) on the other.
class MoonView(context: Context) : View(context) {
    var phase = 0.0
        set(v) { field = v; invalidate() }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HomeWidget.PILL }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A2A.toInt() }
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HomeWidget.FG }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        canvas.drawCircle(cx, cy, r, pill)
        val m = r * 0.58f
        canvas.drawCircle(cx, cy, m, dark)
        val waxing = phase < 0.5
        // Terminator half-width: +m at new, 0 at quarters, -m at full.
        val k = cos(2 * Math.PI * phase).toFloat() * m
        val path = Path()
        val disc = RectF(cx - m, cy - m, cx + m, cy + m)
        // Limb: the lit semicircle (right when waxing, left when waning).
        path.arcTo(disc, -90f, if (waxing) 180f else -180f)
        // Terminator back from bottom to top.
        val term = RectF(cx - kotlin.math.abs(k), cy - m, cx + kotlin.math.abs(k), cy + m)
        val bulgesOut = if (waxing) k < 0 else k < 0
        val sweep = if (waxing) (if (bulgesOut) 180f else -180f) else (if (bulgesOut) -180f else 180f)
        path.arcTo(term, 90f, sweep)
        path.close()
        canvas.drawPath(path, lit)
    }
}

// Battery as a ring filled clockwise from 12 o'clock, percentage inside.
class BatteryRingView(context: Context) : View(context) {
    var percent = -1
        set(v) { field = v; invalidate() }
    var charging = false
        set(v) { field = v; invalidate() }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HomeWidget.PILL }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x40FFFFFF }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HomeWidget.FG
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        canvas.drawCircle(cx, cy, r, pill)
        val stroke = r * 0.13f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        arc.color = when {
            charging -> 0xFF8FD18F.toInt()
            percent in 0..15 -> HomeWidget.ACCENT
            else -> HomeWidget.FG
        }
        val ring = r * 0.7f
        val box = RectF(cx - ring, cy - ring, cx + ring, cy + ring)
        canvas.drawArc(box, 0f, 360f, false, track)
        if (percent >= 0) canvas.drawArc(box, -90f, 360f * percent / 100f, false, arc)
        text.textSize = r * 0.5f
        canvas.drawText(if (percent >= 0) "$percent" else "–", cx, cy - (text.ascent() + text.descent()) / 2, text)
    }
}
