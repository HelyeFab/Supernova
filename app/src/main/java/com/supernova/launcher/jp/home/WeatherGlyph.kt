package com.supernova.launcher.jp.home

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// JP fork: monochrome weather icons drawn in the widgets' foreground colour
// (emoji ignore text-presentation hints on this phone and come out coloured).
class WeatherGlyph(private val kind: Kind, color: Int = HomeWidget.FG, private val sizePx: Int = 0) : Drawable() {

    enum class Kind { SUN, MOON, PARTLY, CLOUD, RAIN, SNOW, STORM, FOG }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // -1: no intrinsic size, so an ImageView stretches it to its own bounds.
    override fun getIntrinsicWidth() = if (sizePx > 0) sizePx else -1
    override fun getIntrinsicHeight() = if (sizePx > 0) sizePx else -1

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        canvas.save()
        canvas.translate(b.left + (b.width() - s) / 2, b.top + (b.height() - s) / 2)
        line.strokeWidth = s * 0.07f
        when (kind) {
            Kind.SUN -> sun(canvas, s * 0.5f, s * 0.5f, s * 0.2f)
            Kind.MOON -> moon(canvas, s)
            Kind.PARTLY -> { sun(canvas, s * 0.36f, s * 0.36f, s * 0.15f); cloud(canvas, s, s * 0.12f, s * 0.1f) }
            Kind.CLOUD -> cloud(canvas, s, 0f, 0f)
            Kind.RAIN -> { cloud(canvas, s, 0f, -s * 0.12f); drops(canvas, s) }
            Kind.SNOW -> { cloud(canvas, s, 0f, -s * 0.12f); flakes(canvas, s) }
            Kind.STORM -> { cloud(canvas, s, 0f, -s * 0.12f); bolt(canvas, s) }
            Kind.FOG -> for (i in 0..2) {
                val y = s * (0.35f + i * 0.17f)
                canvas.drawLine(s * (0.18f + i * 0.04f), y, s * (0.82f - i * 0.04f), y, line)
            }
        }
        canvas.restore()
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
        c.drawCircle(cx, cy, r, fill)
        for (i in 0 until 8) {
            val a = i * Math.PI / 4
            c.drawLine(cx + (r * 1.45f) * cos(a).toFloat(), cy + (r * 1.45f) * sin(a).toFloat(),
                cx + (r * 1.95f) * cos(a).toFloat(), cy + (r * 1.95f) * sin(a).toFloat(), line)
        }
    }

    private fun moon(c: Canvas, s: Float) {
        val outer = Path().apply { addCircle(s * 0.5f, s * 0.5f, s * 0.3f, Path.Direction.CW) }
        val bite = Path().apply { addCircle(s * 0.64f, s * 0.4f, s * 0.26f, Path.Direction.CW) }
        outer.op(bite, Path.Op.DIFFERENCE)
        c.drawPath(outer, fill)
    }

    // A cloud from three circles on a rounded base, shifted by dx/dy.
    private fun cloud(c: Canvas, s: Float, dx: Float, dy: Float) {
        val p = Path()
        p.addCircle(s * 0.36f + dx, s * 0.56f + dy, s * 0.16f, Path.Direction.CW)
        p.addCircle(s * 0.54f + dx, s * 0.46f + dy, s * 0.21f, Path.Direction.CW)
        p.addCircle(s * 0.7f + dx, s * 0.58f + dy, s * 0.14f, Path.Direction.CW)
        p.addRoundRect(RectF(s * 0.2f + dx, s * 0.56f + dy, s * 0.84f + dx, s * 0.72f + dy), s * 0.08f, s * 0.08f, Path.Direction.CW)
        c.drawPath(p, fill)
    }

    private fun drops(c: Canvas, s: Float) {
        for (i in 0..2) {
            val x = s * (0.34f + i * 0.16f)
            c.drawLine(x, s * 0.7f, x - s * 0.05f, s * 0.86f, line)
        }
    }

    private fun flakes(c: Canvas, s: Float) {
        for (i in 0..2) c.drawCircle(s * (0.34f + i * 0.16f), s * (0.76f + (i % 2) * 0.08f), s * 0.04f, fill)
    }

    private fun bolt(c: Canvas, s: Float) {
        val p = Path().apply {
            moveTo(s * 0.54f, s * 0.6f); lineTo(s * 0.42f, s * 0.8f); lineTo(s * 0.52f, s * 0.8f)
            lineTo(s * 0.46f, s * 0.96f); lineTo(s * 0.62f, s * 0.74f); lineTo(s * 0.52f, s * 0.74f); close()
        }
        c.drawPath(p, fill)
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; line.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { fill.colorFilter = cf; line.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        fun forCode(code: Int, isDay: Boolean = true): Kind = when (code) {
            0, 1 -> if (isDay) Kind.SUN else Kind.MOON
            2 -> Kind.PARTLY
            45, 48 -> Kind.FOG
            in 51..67, in 80..82 -> Kind.RAIN
            in 71..77, 85, 86 -> Kind.SNOW
            in 95..99 -> Kind.STORM
            else -> Kind.CLOUD
        }
    }
}
