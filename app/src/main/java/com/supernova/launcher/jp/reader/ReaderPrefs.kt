package com.supernova.launcher.jp.reader

import android.content.Context
import com.supernova.launcher.BuildConfig
import androidx.core.content.edit

/** Kept outside launcher backup exports, which include only the files domain. */
class ReaderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("jp_reader", Context.MODE_PRIVATE)

    init {
        // 2026-11-12: double-tapping the back replaced the always-on floating button as the
        // way in. Applied once, so a later choice to bring the button back sticks.
        if (!prefs.contains("back_tap")) prefs.edit { putBoolean("back_tap", true); putBoolean("floating", false) }
    }

    var backTap: Boolean
        get() = prefs.getBoolean("back_tap", true)
        set(value) { prefs.edit { putBoolean("back_tap", value) } }
    /** 0 = low, 1 = medium, 2 = high. */
    var backTapSensitivity: Int
        get() = prefs.getInt("back_tap_sensitivity", 1)
        set(value) { prefs.edit { putInt("back_tap_sensitivity", value.coerceIn(0, 2)) } }
    val backTapThreshold: Float
        get() = when (backTapSensitivity) {
            0 -> BackTapDetector.SENSITIVITY_LOW
            2 -> BackTapDetector.SENSITIVITY_HIGH
            else -> BackTapDetector.SENSITIVITY_MEDIUM
        }
    var floating: Boolean
        get() = prefs.getBoolean("floating", false)
        set(value) { prefs.edit { putBoolean("floating", value) } }
    var buttonY: Int
        get() = prefs.getInt("button_y", 240)
        set(value) { prefs.edit { putInt("button_y", value) } }
    var claudeKey: String
        get() = prefs.getString("claude_key", null) ?: BuildConfig.READER_CLAUDE_KEY
        set(value) { prefs.edit { putString("claude_key", value.trim()) } }
    var model: String
        get() = prefs.getString("model", "claude-haiku-4-5") ?: "claude-haiku-4-5"
        set(value) { prefs.edit { putString("model", value.trim()) } }
    var furigana: Boolean
        get() = prefs.getBoolean("furigana", true)
        set(value) { prefs.edit { putBoolean("furigana", value) } }
}
