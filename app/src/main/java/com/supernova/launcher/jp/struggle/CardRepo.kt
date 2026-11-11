package com.supernova.launcher.jp.struggle

// JP fork: ported from ~/Dev/anki-struggle-widget (dev.fabiani.strugglewidget).

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * One struggling CARD (a note can have several card types, e.g. Core's "Listening";
 * only the ones that are actually struggling are listed), flattened for display.
 */
data class StruggleCard(
    val noteId: Long,
    val ord: Int,
    val cardName: String,
    val deck: String,
    val word: String,
    val reading: String,
    val meaning: String,
    val sentence: String,
    val due: Boolean,
) {
    val key get() = "$noteId:$ord"
    val isListening get() = cardName.contains("listen", ignoreCase = true)

    fun toJson() = JSONObject()
        .put("id", noteId).put("ord", ord).put("name", cardName).put("deck", deck).put("word", word)
        .put("reading", reading).put("meaning", meaning).put("sentence", sentence).put("due", due)

    companion object {
        fun fromJson(o: JSONObject) = StruggleCard(
            o.getLong("id"), o.optInt("ord"), o.optString("name"), o.getString("deck"), o.getString("word"),
            o.getString("reading"), o.getString("meaning"), o.getString("sentence"), o.optBoolean("due"),
        )
    }
}

/**
 * Reads struggling cards straight from AnkiDroid's content provider (FlashCardsContract)
 * and grades them through it. The provider takes a normal Anki search string as the
 * selection, so the rule lives in one place: RULE + DECKS.
 */
object CardRepo {
    private const val AUTHORITY = "com.ichi2.anki.flashcards"
    private val NOTES: Uri = Uri.parse("content://$AUTHORITY/notes")
    private val MODELS: Uri = Uri.parse("content://$AUTHORITY/models")
    private val SCHEDULE: Uri = Uri.parse("content://$AUTHORITY/schedule")
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

    /** "Struggling" = forgotten 3+ times and not suspended (chosen 2026-11-07). */
    const val RULE = "prop:lapses>=3 -is:suspended"

    /** label shown on the widget -> real Anki deck name (searched incl. subdecks). */
    val DECKS = linkedMapOf(
        "Core 2k" to "Core 2k/6k Optimized JP Vocab (JouzuJuls)",
        "JLPT Tango" to "JLPT Tango",
    )
    private const val MAX_TEMPLATES = 4

    private const val PREFS = "struggle"
    private const val KEY_CARDS = "cards"
    private const val KEY_FETCHED = "fetched_at"
    private const val KEY_ERROR = "error"

    val EASE_LABELS = listOf("Again", "Hard", "Good", "Easy")

    fun load(ctx: Context): List<StruggleCard> {
        val raw = prefs(ctx).getString(KEY_CARDS, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return List(arr.length()) { StruggleCard.fromJson(arr.getJSONObject(it)) }
    }

    fun lastError(ctx: Context): String? = prefs(ctx).getString(KEY_ERROR, null)

    fun ageMillis(ctx: Context): Long =
        System.currentTimeMillis() - prefs(ctx).getLong(KEY_FETCHED, 0L)

    /** Query AnkiDroid and cache the result. Returns the card count, or null on failure. */
    fun refresh(ctx: Context): Int? {
        return try {
            val fieldNames = modelFieldNames(ctx)
            val templateNames = HashMap<Long, Map<Int, String>>() // per note-type, filled lazily
            val cards = ArrayList<StruggleCard>()
            for ((label, deck) in DECKS) {
                for (n in 1..MAX_TEMPLATES) {
                    // card:N restricts the search to template N, so each hit is one specific card.
                    val base = "\"deck:$deck\" $RULE card:$n"
                    val dueIds = noteIds(ctx, "$base is:due")
                    // A card:N past the note type's last template is rejected (null cursor): just none.
                    queryNotes(ctx, base, required = n == 1) { nid, mid, f ->
                        val names = templateNames.getOrPut(mid) { cardNames(ctx, nid) }
                        toCard(nid, n - 1, names[n - 1] ?: "Card $n", label, fieldNames[mid] ?: return@queryNotes, f, nid in dueIds)
                            ?.let(cards::add)
                    }
                }
            }
            // Group a note's cards together, Core first then Tango, in note order.
            val order = DECKS.keys.toList()
            cards.sortWith(compareBy({ order.indexOf(it.deck) }, { it.noteId }, { it.ord }))
            val arr = JSONArray().apply { cards.forEach { put(it.toJson()) } }
            prefs(ctx).edit()
                .putString(KEY_CARDS, arr.toString())
                .putLong(KEY_FETCHED, System.currentTimeMillis())
                .remove(KEY_ERROR)
                .apply()
            cards.size
        } catch (e: Exception) {
            android.util.Log.w("StruggleWidget", "refresh failed", e)
            prefs(ctx).edit().putString(KEY_ERROR, e.javaClass.simpleName + ": " + e.message).apply()
            null
        }
    }

    /** Display data for one card: from the cached struggling list, else read from AnkiDroid. */
    fun cardFor(ctx: Context, nid: Long, ord: Int): StruggleCard? {
        load(ctx).firstOrNull { it.noteId == nid && it.ord == ord }?.let { return it }
        val fieldNames = modelFieldNames(ctx)
        var out: StruggleCard? = null
        ctx.contentResolver.query(Uri.parse("content://$AUTHORITY/notes/$nid"), arrayOf("mid", "flds"), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val names = fieldNames[c.getLong(0)] ?: return null
                val values = c.getString(1).split('\u001f')
                val label = when {
                    "Vocab" in names -> "JLPT Tango"
                    "Expression" in names && "Meaning" in names -> "Core 2k"
                    else -> "Struggling"
                }
                out = toCard(nid, ord, cardNames(ctx, nid)[ord] ?: "Card ${ord + 1}", label, names, values, true)
            }
        }
        return out
    }

    /** Front/back HTML exactly as AnkiDroid renders this card (media not included). */
    fun cardHtml(ctx: Context, nid: Long, ord: Int): Pair<String, String>? {
        ctx.contentResolver.query(Uri.parse("content://$AUTHORITY/notes/$nid/cards/$ord"),
            arrayOf("question", "answer"), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) to c.getString(1)
        }
        return null
    }

    /** The note's raw fields, by name (used to work out what each [sound:] tag says). */
    fun noteFields(ctx: Context, nid: Long): Map<String, String> {
        val fieldNames = modelFieldNames(ctx)
        ctx.contentResolver.query(Uri.parse("content://$AUTHORITY/notes/$nid"), arrayOf("mid", "flds"), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val names = fieldNames[c.getLong(0)] ?: return emptyMap()
                return names.zip(c.getString(1).split('\u001f')).toMap()
            }
        }
        return emptyMap()
    }

    /** JP fork: the [day]'s note among those matching [search], stable for the whole day. */
    fun dailyCard(ctx: Context, search: String, day: Long): StruggleCard? {
        val ids = noteIds(ctx, search).sorted()
        if (ids.isEmpty()) return null
        val nid = ids[Math.floorMod(day * 7919L, ids.size.toLong()).toInt()]
        return cardFor(ctx, nid, 0)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun modelFieldNames(ctx: Context): Map<Long, List<String>> {
        val out = HashMap<Long, List<String>>()
        ctx.contentResolver.query(MODELS, arrayOf("_id", "field_names"), null, null, null)?.use { c ->
            val idI = c.getColumnIndexOrThrow("_id")
            val fI = c.getColumnIndexOrThrow("field_names")
            while (c.moveToNext()) out[c.getLong(idI)] = c.getString(fI).split('\u001f')
        }
        return out
    }

    private fun cardNames(ctx: Context, nid: Long): Map<Int, String> {
        val out = HashMap<Int, String>()
        ctx.contentResolver.query(Uri.parse("content://$AUTHORITY/notes/$nid/cards"), arrayOf("ord", "card_name"), null, null, null)?.use { c ->
            while (c.moveToNext()) out[c.getInt(0)] = c.getString(1)
        }
        return out
    }

    private fun noteIds(ctx: Context, search: String): Set<Long> {
        val out = HashSet<Long>()
        ctx.contentResolver.query(NOTES, arrayOf("_id"), search, null, null)?.use { c ->
            while (c.moveToNext()) out += c.getLong(0)
        }
        return out
    }

    private fun queryNotes(ctx: Context, search: String, required: Boolean, each: (Long, Long, List<String>) -> Unit) {
        val cursor = ctx.contentResolver.query(NOTES, arrayOf("_id", "mid", "flds"), search, null, null)
            ?: if (required) throw IllegalStateException("AnkiDroid returned no data for: $search") else return
        cursor.use { c ->
            while (c.moveToNext()) each(c.getLong(0), c.getLong(1), c.getString(2).split('\u001f'))
        }
    }

    /**
     * Two note shapes:
     *  - "Japanese Vocab Dynamic" (Core): Expression / Reading / Meaning / Sentence
     *  - "Custom Japanese" (Tango): Vocab / Vocab_Furigana / Vocab_English, Expression = sentence
     */
    private fun toCard(
        id: Long, ord: Int, cardName: String, deck: String,
        names: List<String>, values: List<String>, due: Boolean,
    ): StruggleCard? {
        val f = names.zip(values).toMap()
        val vocab = clean(f["Vocab"])
        var card = if (vocab.isNotEmpty()) {
            StruggleCard(id, ord, cardName, deck, vocab,
                reading = bracketReading(f["Vocab_Furigana"]),
                meaning = clean(f["Vocab_English"]),
                sentence = clean(f["Expression"]), due = due)
        } else {
            StruggleCard(id, ord, cardName, deck, clean(f["Expression"]),
                reading = clean(f["Reading"]),
                meaning = clean(f["Meaning"]),
                sentence = clean(f["Sentence"]), due = due)
        }
        // Other note types in the folder (RTK, kotoba, Talk in Tokyo): the first
        // non-empty field is the prompt, a meaning-like field the answer.
        if (card.word.isEmpty()) {
            val first = values.map(::clean).firstOrNull { it.isNotEmpty() } ?: return null
            val answer = listOf("Keyword", "Meaning", "English", "Translation", "Back", "SentenceTranslation")
                .firstNotNullOfOrNull { k -> f[k]?.let(::clean)?.takeIf { it.isNotEmpty() && it != first } }
                ?: values.map(::clean).drop(1).firstOrNull { it.isNotEmpty() && it != first }.orEmpty()
            card = card.copy(word = first, reading = "", meaning = answer, sentence = "")
        }
        card = card.copy(word = joinJp(card.word))
        // Don't repeat the word as its own reading (kana-only vocab).
        return if (card.reading == card.word) card.copy(reading = "") else card
    }

    /** Tango's fields keep furigana-spacing gaps ("招待 状"); drop spaces between Japanese characters. */
    private fun joinJp(s: String) =
        s.replace(Regex("(?<=[\\u3040-\\u30ff\\u3400-\\u9fff]) +(?=[\\u3040-\\u30ff\\u3400-\\u9fff])"), "")

    /** "来週[らいしゅう]は 再来週[さらいしゅう]" -> "来週は再来週": text for TTS / display. */
    fun plainJapanese(raw: String?): String =
        joinJp(clean(raw).replace(Regex("\\[[^\\]]*]"), "")).trim()

    /** "再来週[さらいしゅう]" -> "さらいしゅう"; kana-only input comes back unchanged. */
    private fun bracketReading(raw: String?): String {
        val s = clean(raw)
        if (!s.contains('[')) return s
        return Regex("""\s*([^\s\[\]]*?)\[([^\]]*)]""").replace(s) { it.groupValues[2] }.replace(" ", "")
    }

    fun clean(raw: String?): String {
        if (raw == null) return ""
        return raw
            .substringBefore("<!--")              // Core readings carry a pitch-accent SVG after a comment
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\[sound:[^]]*]"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
