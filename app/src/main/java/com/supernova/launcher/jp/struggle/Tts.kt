package com.supernova.launcher.jp.struggle

// JP fork: ported from ~/Dev/anki-struggle-widget (dev.fabiani.strugglewidget).

import android.content.Context
import android.media.MediaPlayer
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * Speaks Japanese through Emmanuel's AivisSpeech server (the same voice as his other
 * study apps). AnkiDroid's own media folder is private to AnkiDroid, so the card's
 * original mp3s can't be played from here. Clips are cached by text.
 */
object Tts {
    private const val VOICE = "1310138976" // 阿井田茂
    private var player: MediaPlayer? = null

    fun speak(ctx: Context, text: String, onError: (String) -> Unit = {}) =
        play(ctx, text, 1.0, onError) {}

    /** Plays at [speed], then calls [onDone] whether it finished or failed. */
    fun speakAt(ctx: Context, text: String, speed: Double, onDone: () -> Unit) =
        play(ctx, text, speed, { onDone() }, onDone)

    private fun play(ctx: Context, text: String, speed: Double, onError: (String) -> Unit, onDone: () -> Unit) {
        if (text.isBlank()) return onDone()
        thread {
            try {
                val f = clip(ctx, text, speed)
                synchronized(this) {
                    player?.release()
                    player = MediaPlayer().apply {
                        setDataSource(f.absolutePath)
                        setOnCompletionListener { it.release(); if (player === it) player = null; onDone() }
                        prepare(); start()
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun stop() = synchronized(this) { player?.release(); player = null }

    private fun clip(ctx: Context, text: String, speed: Double): File {
        val cacheKey = if (speed == 1.0) text else "$speed|$text" // 1.0 keeps the clips already cached
        val name = MessageDigest.getInstance("SHA-1").digest(cacheKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val f = File(ctx.cacheDir, "tts/$name.mp3")
        if (f.length() > 0) return f
        f.parentFile?.mkdirs()
        val conn = URL(com.supernova.launcher.BuildConfig.TTS_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-API-Key", com.supernova.launcher.BuildConfig.TTS_KEY)
        conn.setRequestProperty("User-Agent", "SupernovaJP/1.0")
        conn.outputStream.use {
            it.write(JSONObject().put("model", "tts-1").put("input", text).put("voice", VOICE).put("speed", speed)
                .toString().toByteArray())
        }
        if (conn.responseCode !in 200..299) throw IllegalStateException("TTS HTTP ${conn.responseCode}")
        val tmp = File(f.path + ".part")
        conn.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
        tmp.renameTo(f)
        return f
    }
}
