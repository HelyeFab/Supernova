package com.supernova.launcher.jp.dict

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

// JP fork: one dictionary hit in the search results: headword, reading, part
// of speech, then the senses. Tap opens the word in Akebi (Takoboto, then
// jisho.org, when it isn't installed).
class JpDictRowView(context: Context) : LinearLayout(context) {

    private val head = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f) }
    private val reading = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }
    private val pos = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val gloss = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
    }

    init {
        orientation = VERTICAL
        val d = resources.displayMetrics.density
        setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), (8 * d).toInt())
        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(head)
            addView(reading, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = (8 * d).toInt(); bottomMargin = (2 * d).toInt()
            })
            addView(pos, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * d).toInt(); bottomMargin = (3 * d).toInt()
            })
        }
        addView(top)
        addView(gloss)
        val ripple = TypedValue().also { context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }
        setBackgroundResource(ripple.resourceId)
    }

    fun bind(entry: JpDict.Entry, text: Int, accent: Int, onOpened: () -> Unit) {
        head.text = if (entry.common) "${entry.head} ★" else entry.head
        head.setTextColor(text)
        reading.text = entry.reading
        reading.setTextColor(accent)
        pos.text = entry.pos
        pos.setTextColor(text and 0x00FFFFFF or 0x99000000.toInt())
        gloss.text = entry.gloss
        gloss.setTextColor(text and 0x00FFFFFF or 0xDD000000.toInt())
        setOnClickListener {
            open(context, entry.head)
            onOpened()
        }
    }

    companion object {
        fun open(context: Context, word: String) {
            val process = { pkg: String, cls: String ->
                Intent(Intent.ACTION_PROCESS_TEXT).setComponent(ComponentName(pkg, cls))
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, word)
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            }
            val tries = listOf(
                process("com.craxic.akebifree", "com.craxic.akebifree.activities.search.SearchActivity"),
                Intent(Intent.ACTION_PROCESS_TEXT).setPackage("jp.takoboto").setType("text/plain")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, word).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://jisho.org/search/" + Uri.encode(word)))
            )
            for (intent in tries) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (_: ActivityNotFoundException) {
                } catch (_: SecurityException) {
                }
            }
        }
    }
}
