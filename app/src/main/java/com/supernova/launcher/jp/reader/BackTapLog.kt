package com.supernova.launcher.jp.reader

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Every knock the background listener sees, and what came of it, so thresholds can be
 * tuned from real use (including false triggers on walks or table set-downs).
 * One line each: time, event, peak m/s², which screen, threshold.
 * Read it with: adb shell run-as dev.fabiani.supernova.debug cat no_backup/backtap.log
 * No screen content and nothing leaves the phone; outside backups (no_backup).
 */
class BackTapLog(context: Context) {
    private val file = File(context.noBackupFilesDir, "backtap.log")
    private val writer = Executors.newSingleThreadExecutor()
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun add(event: String, peak: Float? = null, screen: String = "", threshold: Float? = null) {
        val line = listOfNotNull(
            time.format(Date()), event.padEnd(8),
            peak?.let { "%5.1f".format(Locale.US, it) } ?: "    -",
            screen.ifBlank { null }, threshold?.let { "th=%.1f".format(Locale.US, it) },
        ).joinToString("  ")
        writer.execute {
            runCatching {
                // Keep it small: past ~64 KB, drop the older half.
                if (file.length() > 64_000) file.writeText(file.readLines().takeLast(400).joinToString("\n", postfix = "\n"))
                file.appendText(line + "\n")
            }
        }
    }

    fun close() = writer.shutdown()
}
