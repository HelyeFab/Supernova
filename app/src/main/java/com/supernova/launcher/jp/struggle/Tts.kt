package com.supernova.launcher.jp.struggle

// JP fork: ported from ~/Dev/anki-struggle-widget (dev.fabiani.strugglewidget).

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicLong

/**
 * Speaks Japanese through Emmanuel's AivisSpeech server (the same voice as his other
 * study apps). AnkiDroid's own media folder is private to AnkiDroid, so the card's
 * original mp3s can't be played from here. Clips are cached by text.
 */
object Tts {
    private const val VOICE = "1310138976" // 阿井田茂
    private var player: MediaPlayer? = null
    private val generation = AtomicLong()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    fun speak(ctx: Context, text: String, onError: (String) -> Unit = {}) =
        play(ctx, text, 1.0, onError) {}

    /** Plays at [speed], then calls [onDone] whether it finished or failed. */
    fun speakAt(ctx: Context, text: String, speed: Double, onDone: () -> Unit) =
        play(ctx, text, speed, { onDone() }, onDone)

    // JP fork: the reader exposes errors instead of silently skipping broken audio.
    fun speakReader(ctx: Context, text: String, speed: Double, onError: (String) -> Unit, onDone: () -> Unit) =
        play(ctx, text, speed, onError, onDone)

    private fun play(ctx: Context, text: String, speed: Double, onError: (String) -> Unit, onDone: () -> Unit) {
        if (text.isBlank()) return onDone()
        val request = generation.incrementAndGet()
        main.post { player?.release(); player = null }
        thread {
            try {
                val f = clip(ctx, text, speed)
                main.post {
                    // Stop/new sentence must also cancel a clip still downloading.
                    if (request != generation.get()) return@post
                    try {
                        player?.release()
                        player = MediaPlayer().apply {
                            setDataSource(f.absolutePath)
                            setOnCompletionListener { it.release(); if (player === it) player = null; onDone() }
                            setOnErrorListener { media, _, _ ->
                                media.release(); if (player === media) player = null
                                onError("Could not play this audio."); true
                            }
                            setOnPreparedListener { if (request == generation.get()) it.start() else it.release() }
                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        player?.release(); player = null
                        onError(e.message ?: "Could not play this audio.")
                    }
                }
            } catch (e: Exception) {
                main.post { if (request == generation.get()) onError(e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        main.post { player?.release(); player = null }
    }

    private fun clip(ctx: Context, text: String, speed: Double): File {
        val cacheKey = if (speed == 1.0) text else "$speed|$text" // 1.0 keeps the clips already cached
        val name = MessageDigest.getInstance("SHA-1").digest(cacheKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val f = File(ctx.cacheDir, "tts/$name.mp3")
        if (f.length() > 0) return f
        check(com.supernova.launcher.BuildConfig.TTS_KEY.isNotBlank()) { "Speech key missing from this build. Rebuild with tts.key." }
        f.parentFile?.mkdirs()
        val conn = URL(com.supernova.launcher.BuildConfig.TTS_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-API-Key", com.supernova.launcher.BuildConfig.TTS_KEY)
        conn.setRequestProperty("User-Agent", "SupernovaJP/1.0")
        try {
        conn.outputStream.use {
            it.write(JSONObject().put("model", "tts-1").put("input", text).put("voice", VOICE).put("speed", speed)
                .toString().toByteArray())
        }
        if (conn.responseCode !in 200..299) throw IllegalStateException("Speech HTTP ${conn.responseCode}. Check the service key and connection.")
        val tmp = File.createTempFile("clip-", ".part", f.parentFile)
        try {
            conn.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            check(tmp.length() > 0 && tmp.renameTo(f)) { "Could not cache speech audio." }
        } finally { tmp.delete() }
        return f
        } finally { conn.disconnect() }
    }
}
