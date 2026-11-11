package com.supernova.launcher.jp.cards

import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan

// JP fork: furigana for TextViews. RubySpan draws its base text with the
// reading centred above it in a smaller size; the TextView needs line
// spacing above each line for the readings (see FURIGANA_SPACING).
class RubySpan(private val reading: String, private val scale: Float = 0.5f) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val base = paint.measureText(text, start, end)
        val small = Paint(paint).apply { textSize = paint.textSize * scale }
        fm?.let { paint.getFontMetricsInt(it) }
        return maxOf(base, small.measureText(reading)).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val base = paint.measureText(text, start, end)
        val small = Paint(paint).apply { textSize = paint.textSize * scale }
        val width = maxOf(base, small.measureText(reading))
        canvas.drawText(text, start, end, x + (width - base) / 2, y.toFloat(), paint)
        canvas.drawText(reading, x + (width - small.measureText(reading)) / 2, y + paint.ascent() - small.descent(), small)
    }

    companion object {
        // Extra line spacing (as a multiple of the text size) that leaves room for readings.
        const val FURIGANA_SPACING = 0.55f
        private val RUBY = Regex("<ruby>(.*?)<rt>(.*?)</rt></ruby>", RegexOption.DOT_MATCHES_ALL)
        private val TAG = Regex("<[^>]+>")

        // "<ruby>漢字<rt>かんじ</rt></ruby>を" -> 漢字 (with かんじ above) + を.
        fun fromHtml(html: String): CharSequence {
            val out = SpannableStringBuilder()
            var last = 0
            for (m in RUBY.findAll(html)) {
                out.append(TAG.replace(html.substring(last, m.range.first), ""))
                val base = TAG.replace(m.groupValues[1], "")
                val at = out.length
                out.append(base)
                out.setSpan(RubySpan(TAG.replace(m.groupValues[2], "")), at, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                last = m.range.last + 1
            }
            out.append(TAG.replace(html.substring(last), ""))
            return out
        }
    }
}
