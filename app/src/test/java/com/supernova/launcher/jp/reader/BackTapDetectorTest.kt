package com.supernova.launcher.jp.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class BackTapDetectorTest {
    private val rateHz = 400
    private val stepNs = 1_000_000_000L / rateHz

    /** Phone lying still, face up; [knocksMs] become 15 ms z spikes with a little ringing. */
    private fun run(
        durationMs: Int,
        knocksMs: List<Int>,
        knockSize: Float = 8f,
        wobble: (Double) -> Float = { 0f },
        threshold: Float = BackTapDetector.SENSITIVITY_MEDIUM,
    ): Pair<Int, Int> {
        var doubles = 0
        var knocks = 0
        val detector = BackTapDetector(threshold, onDoubleTap = { doubles++ }, onKnock = { knocks++ })
        val samples = durationMs * rateHz / 1000
        for (i in 0 until samples) {
            val t = i * 1000.0 / rateHz
            var z = 9.81f + wobble(t)
            knocksMs.forEach { k ->
                val since = t - k
                if (since in 0.0..15.0) z += (knockSize * sin(PI * since / 15.0)).toFloat()
                else if (since in 15.0..40.0) z -= (knockSize * 0.3 * sin(PI * (since - 15) / 25.0)).toFloat()
            }
            detector.onSample(i * stepNs, 0.05f, 0.02f, z)
        }
        return doubles to knocks
    }

    @Test fun `two knocks 200 ms apart are a double tap`() {
        assertEquals(1, run(1500, listOf(500, 700)).first)
    }

    @Test fun `a single knock is not`() {
        assertEquals(0, run(1500, listOf(500)).first)
    }

    @Test fun `knocks too far apart are not`() {
        assertEquals(0, run(2000, listOf(500, 1100)).first)
    }

    @Test fun `three quick knocks are not`() {
        assertEquals(0, run(2000, listOf(500, 700, 900)).first)
    }

    @Test fun `knocks below the threshold are not`() {
        assertEquals(0, run(1500, listOf(500, 700), knockSize = 2f).first)
    }

    @Test fun `light knocks count on the light setting`() {
        assertEquals(1, run(1500, listOf(500, 700), knockSize = 3.5f, threshold = BackTapDetector.SENSITIVITY_HIGH).first)
    }

    @Test fun `walking jolts are not`() {
        // ~2 steps a second, each a strong low-frequency bounce: never "still" before a spike.
        val walk = { t: Double -> (6 * sin(2 * PI * 2.0 * t / 1000.0)).toFloat() }
        assertEquals(0, run(4000, listOf(1000, 1200, 2500, 2700), wobble = walk).first)
    }

    @Test fun `ringing after one knock is a single knock`() {
        assertEquals(1, run(1500, listOf(500)).second)
    }

    @Test fun `two doubles separated by the cooldown both fire`() {
        assertEquals(2, run(4000, listOf(500, 700, 2500, 2700)).first)
    }
}
