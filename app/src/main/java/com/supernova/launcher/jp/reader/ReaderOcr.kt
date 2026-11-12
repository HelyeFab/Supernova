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
        val heights = blocks.flatten().map { it.height }.filter { it > 0 }.sorted()
        val body = heights.getOrNull(heights.size / 2) ?: 0
        fun isReading(line: Line) =
            body > 0 && line.height in 1 until (body * READING_SCALE).toInt() && kanaOnly.matches(line.text)
        return blocks.map { lines -> lines.filterNot(::isReading).joinToString("") { it.text.trim() } }
            .filter { it.isNotBlank() }.joinToString("\n").trim()
    }

    /** Furigana are printed at about half the body size; anything under 65% counts. */
    private const val READING_SCALE = 0.65f
}
