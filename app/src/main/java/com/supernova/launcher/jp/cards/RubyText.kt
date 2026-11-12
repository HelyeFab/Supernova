package com.supernova.launcher.jp.cards

import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan

// JP fork: furigana for TextViews. RubySpan draws its base text with the
// reading centred above it in a smaller size; the TextView needs line
// spacing above each line for the readings (see FURIGANA_SPACING).
class RubySpan(
    private val reading: String,
    private val scale: Float = 0.5f,
    /** Reading colour; null draws it in the base text's colour. */
    private val rubyColor: Int? = null,
    /**
     * How far (in ems of the base text, both sides together) a long reading may hang over
     * the neighbouring kana instead of pushing them apart: 美しい stays 美しい, not 美 しい.
     */
    private val overhang: Float = 0f,
) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val base = paint.measureText(text, start, end)
        val small = Paint(paint).apply { textSize = paint.textSize * scale }
        fm?.let { paint.getFontMetricsInt(it) }
        return maxOf(base, small.measureText(reading) - overhang * paint.textSize).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val base = paint.measureText(text, start, end)
        val small = Paint(paint).apply { textSize = paint.textSize * scale; rubyColor?.let { color = it } }
        val width = maxOf(base, small.measureText(reading) - overhang * paint.textSize)
        canvas.drawText(text, start, end, x + (width - base) / 2, y.toFloat(), paint)
        // Centred over the base; may extend past the span by up to half the overhang each side,
        // except at the start of a line (x = 0), where the TextView would clip it.
        val rubyX = (x + (width - small.measureText(reading)) / 2).coerceAtLeast(0f)
        canvas.drawText(reading, rubyX, y + paint.ascent() - small.descent(), small)
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
