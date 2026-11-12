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
                if (continuation.isActive) continuation.resume(result.text.trim())
            }
            .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }
}
