package com.supernova.launcher.jp.reader

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/** Claude sometimes emits headings/bold despite a plain-text request. Render those safely,
 * without treating model output as HTML, links, or executable content. */
object ReaderExplanation {
    private val heading = Regex("^#{1,6}\\s+")
    private val bold = Regex("\\*\\*(.+?)\\*\\*")

    fun render(text: String): CharSequence = SpannableStringBuilder().apply {
        text.lines().forEachIndexed { index, line ->
            if (index > 0) append('\n')
            val isHeading = heading.containsMatchIn(line)
            val content = heading.replaceFirst(line, "")
            val lineStart = length
            var previous = 0
            bold.findAll(content).forEach { match ->
                append(content.substring(previous, match.range.first))
                val start = length
                append(match.groupValues[1])
                setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                previous = match.range.last + 1
            }
            append(content.substring(previous))
            if (isHeading && length > lineStart) {
                setSpan(StyleSpan(Typeface.BOLD), lineStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(1.1f), lineStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
