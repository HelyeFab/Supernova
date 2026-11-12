package com.supernova.launcher.jp.reader

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Double knock on the back of the phone, from raw accelerometer samples.
 *
 * A knock is a short, sharp spike mostly along the screen normal (z) after gravity is
 * removed. Two knocks 90–450 ms apart, on a phone that was otherwise still, then
 * 220 ms with no third knock, is a double tap. The quiet-before and no-third-knock
 * rules are what reject walking, shaking and putting the phone down.
 *
 * Pure Kotlin (no Android types) so it can be driven by recorded samples in tests.
 */
class BackTapDetector(
    /** High-passed z spike that counts as a knock, m/s². */
    @Volatile var threshold: Float = SENSITIVITY_MEDIUM,
    private val onDoubleTap: () -> Unit,
    /** Every knock, with its peak, for the calibration panel. */
    private val onKnock: (peak: Float) -> Unit = {},
) {
    private val gravity = FloatArray(3)
    private var primed = false
    private var lastNs = 0L
    private var activity = 0f // slow average of motion, the "was the phone still?" gate

    private var knockStartNs = -1L // inside a knock's refractory window
    private var knockPeak = 0f
    private var firstKnockNs = -1L
    private var secondKnockNs = -1L
    private var cooldownUntilNs = 0L

    fun reset() {
        primed = false; activity = 0f
        knockStartNs = -1L; firstKnockNs = -1L; secondKnockNs = -1L
    }

    fun onSample(timestampNs: Long, x: Float, y: Float, z: Float) {
        if (!primed) {
            gravity[0] = x; gravity[1] = y; gravity[2] = z
            lastNs = timestampNs; primed = true; return
        }
        val dt = ((timestampNs - lastNs) / 1e9f).coerceIn(0.0005f, 0.1f)
        lastNs = timestampNs
        val a = dt / (GRAVITY_TAU + dt)
        gravity[0] += a * (x - gravity[0]); gravity[1] += a * (y - gravity[1]); gravity[2] += a * (z - gravity[2])
        val hx = x - gravity[0]; val hy = y - gravity[1]; val hz = z - gravity[2]
        val magnitude = sqrt(hx * hx + hy * hy + hz * hz)
        val zShare = if (magnitude > 0f) abs(hz) / magnitude else 0f

        // A pending double fires once the "no third knock" window has passed.
        if (secondKnockNs >= 0 && timestampNs - secondKnockNs > CONFIRM_NS) {
            firstKnockNs = -1L; secondKnockNs = -1L
            cooldownUntilNs = timestampNs + COOLDOWN_NS
            onDoubleTap()
        }
        // A lone knock that never got a partner expires.
        if (firstKnockNs >= 0 && secondKnockNs < 0 && timestampNs - firstKnockNs > MAX_GAP_NS) firstKnockNs = -1L

        if (knockStartNs >= 0) {
            knockPeak = max(knockPeak, abs(hz))
            if (timestampNs - knockStartNs > REFRACTORY_NS) {
                knockStartNs = -1L
                onKnock(knockPeak)
            }
        } else if (abs(hz) > threshold && zShare > Z_SHARE) {
            knock(timestampNs, abs(hz))
        }
        // Updated after the knock check, so a knock's own spike never counts as prior motion.
        val b = dt / (ACTIVITY_TAU + dt)
        activity += b * (magnitude - activity)
    }

    private fun knock(timestampNs: Long, value: Float) {
        knockStartNs = timestampNs; knockPeak = value
        if (timestampNs < cooldownUntilNs) return
        when {
            secondKnockNs >= 0 -> { // a third knock: someone is shaking or drumming, not double-tapping
                firstKnockNs = -1L; secondKnockNs = -1L
                cooldownUntilNs = timestampNs + COOLDOWN_NS
            }
            firstKnockNs >= 0 -> {
                val gap = timestampNs - firstKnockNs
                if (gap >= MIN_GAP_NS) secondKnockNs = timestampNs
            }
            activity < threshold * STILL_FRACTION -> firstKnockNs = timestampNs
        }
    }

    companion object {
        const val SENSITIVITY_HIGH = 2.5f
        const val SENSITIVITY_MEDIUM = 4f
        const val SENSITIVITY_LOW = 6f

        private const val GRAVITY_TAU = 0.08f
        private const val ACTIVITY_TAU = 0.3f
        private const val STILL_FRACTION = 0.3f
        private const val Z_SHARE = 0.6f
        private const val REFRACTORY_NS = 70_000_000L
        private const val MIN_GAP_NS = 90_000_000L
        private const val MAX_GAP_NS = 450_000_000L
        private const val CONFIRM_NS = 220_000_000L
        private const val COOLDOWN_NS = 1_000_000_000L
    }
}
