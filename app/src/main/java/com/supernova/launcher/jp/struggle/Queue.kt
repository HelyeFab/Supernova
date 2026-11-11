package com.supernova.launcher.jp.struggle

// JP fork: ported from ~/Dev/anki-struggle-widget (dev.fabiani.strugglewidget).

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import org.json.JSONArray

/**
 * Review through AnkiDroid's own queue for the "Struggling" filtered deck.
 *
 * WHY: AnkiDroid's API can only answer the card at the FRONT of the selected deck's queue.
 * Answering any other card throws inside AnkiDroid (it shows its crash-report dialog) while
 * the provider still reports success, and nothing is saved (found 2026-11-07 with 会長).
 * So every grade/skip here re-reads the queue head first and refuses on a mismatch, and
 * afterwards checks that the queue actually moved.
 */
object Queue {
    /**
     * The filtered deck holding every struggling card. It is OWNED BY THE DESKTOP SHIBA DASH
     * add-on (struggle folder, config folder_top/struggle_extra/folder_id): Shiba flags cards with
     * 3+ lapses in Core 2k + JLPT Tango, releases them after 3 correct answers in a row and sets the
     * deck's search. Do not edit the deck's search in AnkiDroid — Shiba overwrites it, and a
     * different search on each side splits the cards between two decks.
     */
    // Renamed by Shiba Dash's 2026-11-10 fix (one top-level folder fed by Core 2k,
    // JLPT Tango, RTK, kotoba and Talk in Tokyo); the old name is still accepted.
    const val DECK_NAME = "Struggling Cards"
    private val DECK_NAMES = setOf(DECK_NAME, "Struggling")

    private const val AUTHORITY = "com.ichi2.anki.flashcards"
    private val SCHEDULE: Uri = Uri.parse("content://$AUTHORITY/schedule")
    private val DECKS: Uri = Uri.parse("content://$AUTHORITY/decks")

    data class Head(val card: StruggleCard, val intervals: List<String>, val remaining: Int)

    sealed class State {
        object NoDeck : State()
        object Empty : State()
        data class Ready(val head: Head) : State()
        data class Error(val message: String) : State()
    }

    enum class Result { OK, MOVED_ON, NOT_SAVED }

    fun state(ctx: Context): State {
        return try {
            val deck = deck(ctx) ?: return State.NoDeck
            val top = top(ctx, deck.first) ?: return State.Empty
            val card = CardRepo.cardFor(ctx, top.first, top.second) ?: return State.Error("Card ${top.first} not readable")
            State.Ready(Head(card, top.third, deck.second))
        } catch (e: Exception) {
            State.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Grade [shown] — only if it is still the head of the queue. ease: 1 Again … 4 Easy. */
    fun grade(ctx: Context, shown: StruggleCard, ease: Int, timeTakenMs: Long): Result =
        act(ctx, shown) {
            put("answer_ease", ease)
            put("time_taken", timeTakenMs.coerceIn(1_000, 60_000))
        }

    /** Skip: bury [shown] until tomorrow (AnkiDroid's own bury). */
    fun skip(ctx: Context, shown: StruggleCard): Result = act(ctx, shown) {
        put("buried", 1)
    }

    private fun act(ctx: Context, shown: StruggleCard, values: ContentValues.() -> Unit): Result {
        val deck = deck(ctx) ?: return Result.NOT_SAVED
        val before = top(ctx, deck.first) ?: return Result.MOVED_ON
        if (before.first != shown.noteId || before.second != shown.ord) return Result.MOVED_ON
        val cv = ContentValues().apply {
            put("note_id", shown.noteId)
            put("ord", shown.ord)
            values()
        }
        ctx.contentResolver.update(SCHEDULE, cv, null, null)
        // The provider says "1 row updated" even when AnkiDroid failed internally — check for real.
        val after = deck(ctx)?.let { top(ctx, it.first) }
        // Saved => the head moved on, or (after "Again") the same card came back in a new state,
        // which shows up as different next-interval labels. Identical head + labels => nothing saved.
        val same = after != null && after.first == shown.noteId && after.second == shown.ord
        return if (!same || after!!.third != before.third) Result.OK else Result.NOT_SAVED
    }

    /** (deckId, cards left today) of the Struggling filtered deck, or null if it doesn't exist. */
    private fun deck(ctx: Context): Pair<Long, Int>? {
        ctx.contentResolver.query(DECKS, arrayOf("deck_id", "deck_name", "deck_count"), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) in DECK_NAMES) {
                    val counts = runCatching { JSONArray(c.getString(2)) }.getOrNull()
                    val left = counts?.let { (0 until it.length()).sumOf { i -> it.optInt(i) } } ?: 0
                    return c.getLong(0) to left
                }
            }
        }
        return null
    }

    /** Head of the deck's queue: (noteId, ord, next-interval labels). Also selects the deck in AnkiDroid. */
    private fun top(ctx: Context, deckId: Long): Triple<Long, Int, List<String>>? {
        ctx.contentResolver.query(SCHEDULE, arrayOf("note_id", "ord", "next_review_times"),
            "limit=?,deckID=?", arrayOf("1", deckId.toString()), null)?.use { c ->
            if (!c.moveToFirst()) return null
            val times = runCatching { JSONArray(c.getString(2)) }.getOrNull()
            val labels = times?.let { t -> List(t.length()) { t.getString(it).replace(Regex("[\\u2066-\\u2069]"), "") } } ?: emptyList()
            return Triple(c.getLong(0), c.getInt(1), labels)
        }
        return null
    }
}
