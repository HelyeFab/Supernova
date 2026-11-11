package com.supernova.launcher.jp.dict

import android.content.Context
import com.supernova.launcher.search.SearchProvider
import com.supernova.launcher.search.SearchResult

// JP fork: dictionary rows in launcher search, for queries in kana, kanji or
// fully-convertible romaji. Async tier: the first lookup copies the database.
class JpDictSearchProvider(private val context: Context) : SearchProvider {

    override val id: String = ID

    override fun search(query: String): List<SearchResult> {
        val key = JpDict.keyFor(query) ?: return emptyList()
        // A Latin query needs a few letters before it reads as Japanese.
        if (query.none(JpDict::isJapanese) && query.trim().length < MIN_ROMAJI) return emptyList()
        return JpDict.lookup(context, key, MAX_RESULTS).map { SearchResult.Dictionary(it) }
    }

    companion object {
        const val ID = "jp-dict"
        private const val MAX_RESULTS = 4
        private const val MIN_ROMAJI = 3
    }
}
