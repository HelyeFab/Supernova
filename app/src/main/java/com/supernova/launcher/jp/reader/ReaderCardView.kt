package com.supernova.launcher.jp.reader

import android.content.Context
import android.content.Intent
import com.supernova.launcher.R
import com.supernova.launcher.jp.cards.CardBase

/** Home/feed entry to the same reader used by the floating button and text sharing. */
class ReaderCardView(context: Context) : CardBase(context, context.getString(R.string.jp_reader_title)) {
    init {
        body.addView(text(16f).apply {
            setText(R.string.jp_reader_card_action)
            setPadding(0, dp(10), 0, dp(10))
        })
        setOnClickListener { open(Intent(context, ReaderActivity::class.java)) }
        contentDescription = context.getString(R.string.jp_reader_summary)
    }
    override fun load() = Unit
    companion object { const val KIND = "jp-reader" }
}
