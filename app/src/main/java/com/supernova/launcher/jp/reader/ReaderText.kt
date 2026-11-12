package com.supernova.launcher.jp.reader

object ReaderText {
    /** Keep punctuation and closing quotes with their sentence; don't split on ASCII dots in names/URLs. */
    fun sentences(text: String): List<String> = Regex("[^。！？!?\n]+[。！？!?]*[」』）)]*|[。！？!?]+")
        .findAll(text).map { it.value.trim() }.filter { it.isNotBlank() }.toList()
}
