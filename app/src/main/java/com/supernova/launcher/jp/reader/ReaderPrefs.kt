package com.supernova.launcher.jp.reader

import android.content.Context
import com.supernova.launcher.BuildConfig
import androidx.core.content.edit

/** Kept outside launcher backup exports, which include only the files domain. */
class ReaderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("jp_reader", Context.MODE_PRIVATE)
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
