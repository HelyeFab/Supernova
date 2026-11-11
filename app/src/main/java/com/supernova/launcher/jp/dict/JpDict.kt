package com.supernova.launcher.jp.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

// JP fork: the offline JMdict (built by tools/jp/build_jmdict.py into
// assets/jp/jmdict.db). SQLite can't open an asset in place, so the first
// lookup copies it to no-backup storage, again after each app update.
// A build without the asset has no dictionary: lookups return nothing.
object JpDict {

    data class Entry(
        val id: Long,
        val head: String,
        val reading: String,
        val pos: String,
        val gloss: String,
        val common: Boolean
    )

    private const val ASSET = "jp/jmdict.db"
    private const val PREFIX_MIN_CHARS = 2
    private var db: SQLiteDatabase? = null
    private var unavailable = false

    // The query as a lookup key: Japanese folded to hiragana, or romaji
    // converted; null when it is neither (an ordinary Latin app query).
    fun keyFor(query: String): String? {
        val q = query.trim()
        if (q.isEmpty()) return null
        if (q.any(::isJapanese)) return hiragana(q)
        return Romaji.toHiragana(q)
    }

    @Synchronized
    fun lookup(context: Context, key: String, limit: Int): List<Entry> {
        val database = open(context) ?: return emptyList()
        val exact = query(database, "k.k = ?", arrayOf(key), limit)
        if (exact.size >= limit || key.length < PREFIX_MIN_CHARS) return exact
        val prefix = query(
            database, "k.k > ? AND k.k < ?", arrayOf(key, key + '￿'), limit - exact.size
        ).filter { e -> exact.none { it.id == e.id } }
        return exact + prefix
    }

    private fun query(db: SQLiteDatabase, where: String, args: Array<String>, limit: Int): List<Entry> {
        val sql = "SELECT e.id, e.head, e.reading, e.pos, e.gloss, e.common FROM key k " +
            "JOIN entry e ON e.id = k.id WHERE $where GROUP BY e.id " +
            "ORDER BY e.common DESC, e.rank DESC, length(e.head) LIMIT $limit"
        return db.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Entry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5) == 1))
                }
            }
        }
    }

    private fun open(context: Context): SQLiteDatabase? {
        db?.let { return it }
        if (unavailable) return null
        return runCatching {
            val file = File(context.noBackupFilesDir, "jmdict.db")
            val stamp = File(context.noBackupFilesDir, "jmdict.stamp")
            val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            if (!file.exists() || stamp.takeIf { it.exists() }?.readText() != version) {
                val tmp = File(file.path + ".part")
                context.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
                stamp.writeText(version)
            }
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        }.onFailure { unavailable = true }.getOrNull()?.also { db = it }
    }

    fun isJapanese(c: Char): Boolean =
        c in '぀'..'ヿ' || c in '㐀'..'鿿' || c in 'ｦ'..'ﾟ' || c == '々'

    fun hiragana(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
    }
}
