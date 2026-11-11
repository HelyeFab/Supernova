package com.supernova.launcher.jp.cards

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import org.json.JSONArray

// JP fork: 今日の復習 - what AnkiDroid has due today, read live from its
// provider: the total and each top-level deck (learning + review + new).
// Tap to open AnkiDroid.
@SuppressLint("ViewConstructor")
class ReviewsView(context: Context) : CardBase(context, "今日の復習 · due today") {

    private class Deck(val name: String, val learn: Int, val review: Int, val new: Int) {
        val total get() = learn + review + new
    }

    private val total = text(34f).apply { gravity = Gravity.CENTER }
    private val totalLabel = text(12f, SUB).apply { gravity = Gravity.CENTER }
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        body.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(total); addView(totalLabel)
            }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.8f))
            addView(list, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.6f))
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnClickListener {
            context.packageManager.getLaunchIntentForPackage("com.ichi2.anki")?.let(::open)
        }
    }

    override fun load() {
        val ctx = context.applicationContext
        io {
            val decks = runCatching { decks(ctx) }.getOrNull()
            post { render(decks) }
        }
    }

    private fun render(decks: List<Deck>?) {
        list.removeAllViews()
        if (decks == null) {
            total.text = "–"
            totalLabel.text = "AnkiDroid not readable"
            return
        }
        val due = decks.filter { it.total > 0 }.sortedByDescending { it.total }
        total.text = due.sumOf { it.total }.toString()
        totalLabel.text = if (due.isEmpty()) "全部終わり！ all done" else "cards left today"
        due.take(MAX_DECKS).forEach { d ->
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
                addView(text(13f).apply {
                    text = d.name; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(text(13f, ACCENT).apply { text = d.total.toString() })
            })
        }
    }

    companion object {
        const val KIND = "jp-reviews"
        private const val MAX_DECKS = 5

        // Top-level decks only: a parent's count already includes its subdecks.
        private fun decks(ctx: Context): List<Deck> {
            val out = ArrayList<Deck>()
            ctx.contentResolver.query(Uri.parse("content://com.ichi2.anki.flashcards/decks"),
                arrayOf("deck_name", "deck_count"), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)
                    if ("::" in name) continue
                    val counts = runCatching { JSONArray(c.getString(1)) }.getOrNull() ?: continue
                    out += Deck(name, counts.optInt(0), counts.optInt(1), counts.optInt(2))
                }
            } ?: error("no provider")
            return out
        }
    }
}
