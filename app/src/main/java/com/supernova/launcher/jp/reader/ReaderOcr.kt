package com.supernova.launcher.jp.reader

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bundled Japanese model: screenshot pixels stay on the phone. */
object ReaderOcr {
    private val recognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }

    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val blocks = result.textBlocks.map { block ->
                    block.lines.map { OcrLayout.Line(it.text, it.boundingBox?.height() ?: 0) }
                }
                if (continuation.isActive) continuation.resume(OcrLayout.text(blocks))
            }
            .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }
}

/**
 * Turns recognised lines back into reading text. Pure, so it is unit-tested.
 *
 * Two things a page with furigana gets wrong otherwise:
 *  - the readings printed above kanji come back as lines of their own (きょうとふ…), so lines
 *    of kana alone that are much shorter than the page's body lines are dropped;
 *  - Japanese wraps mid-sentence, so lines in one block are joined without a break.
 */
object OcrLayout {
    data class Line(val text: String, val height: Int)

    private val kanaOnly = Regex("^[\\u3040-\\u30FF\\u3000\\s・ー]+$")

    fun text(blocks: List<List<Line>>): String {
        // Body height comes from lines with kanji: furigana never contain any, and on a page like
        // NHK Easy the many short reading lines outnumber the real ones, dragging a plain median down.
        val lines = blocks.flatten().filter { it.height > 0 }
        val withKanji = lines.filter { line -> line.text.any(ReaderLanguage::isKanji) }.map { it.height }.sorted()
        val body = withKanji.getOrNull(withKanji.size / 2)
            ?: lines.map { it.height }.sorted().let { it.getOrNull(it.size * 3 / 4) } ?: 0
        fun isReading(line: Line) =
            body > 0 && line.height in 1 until (body * READING_SCALE).toInt() && kanaOnly.matches(line.text)
        val kept = blocks.map { lines -> lines.filterNot(::isReading) }.filter { it.isNotEmpty() }
        val out = StringBuilder()
        var previous: List<Line>? = null
        for (lines in kept) {
            val text = lines.joinToString("") { it.text.trim() }
            if (text.isBlank()) continue
            // A block that stops mid-sentence and the next one at the same size are one wrapped
            // sentence (NHK Easy returns each line as its own block); otherwise a new line.
            val before = previous
            val continues = before != null && !out.endsWith(SENTENCE_END) &&
                kotlin.math.abs(height(before) - height(lines)) <= height(lines) * 0.10f
            if (out.isNotEmpty() && !continues) out.append('\n')
            out.append(text)
            previous = lines
        }
        return out.toString().trim()
    }

    private fun height(lines: List<Line>) = lines.maxOf { it.height }
    private fun StringBuilder.endsWith(chars: String) = isNotEmpty() && this[length - 1] in chars
    private const val SENTENCE_END = "。！？!?」』）)"

    /** Furigana are printed at about half the body size; anything under 65% counts. */
    private const val READING_SCALE = 0.65f
}
