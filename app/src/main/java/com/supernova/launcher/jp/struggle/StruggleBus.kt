package com.supernova.launcher.jp.struggle

import android.os.Handler
import android.os.Looper

// JP fork: tells every Struggle card on the home screen that AnkiDroid's queue
// changed (graded in the review screen, or by another card). Main thread.
object StruggleBus {
    private val listeners = LinkedHashSet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun add(l: () -> Unit) { listeners += l }
    fun remove(l: () -> Unit) { listeners -= l }
    fun changed() = main.post { listeners.toList().forEach { it() } }
}
