package com.supernova.launcher.jp.home

// JP fork: the moon's phase from its mean synodic month, counted from the
// new moon of 2000-01-06 18:14 UTC. Good to within a few hours - plenty for
// a picture of it.
object Moon {
    private const val SYNODIC_DAYS = 29.530588853
    private const val EPOCH_MS = 947_182_440_000L

    // 0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter.
    fun phase(nowMs: Long): Double {
        val days = (nowMs - EPOCH_MS) / 86_400_000.0
        val p = (days / SYNODIC_DAYS) % 1.0
        return if (p < 0) p + 1 else p
    }

    // The lit fraction of the disc, 0..1.
    fun illumination(phase: Double): Double = (1 - kotlin.math.cos(2 * Math.PI * phase)) / 2

    fun name(phase: Double): String = when {
        phase < 0.03 || phase >= 0.97 -> "新月"
        phase < 0.22 -> "三日月"
        phase < 0.28 -> "上弦"
        phase < 0.47 -> "十三夜"
        phase < 0.53 -> "満月"
        phase < 0.72 -> "寝待月"
        phase < 0.78 -> "下弦"
        else -> "有明月"
    }
}
