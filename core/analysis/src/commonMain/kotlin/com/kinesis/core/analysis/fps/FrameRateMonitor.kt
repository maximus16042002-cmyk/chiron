package com.kinesis.core.analysis.fps

import kotlin.math.roundToInt

/**
 * Health classification of the incoming camera/pose frame rate.
 *
 * Implements HANDOVER.md §8.2 item 4 and DECISIONS.md P3-33 / P3-56.
 */
enum class FrameRateHealth {
    /** >= 20.0 FPS. Ideal for real-time kinematic tracking and phase detection. */
    HEALTHY,

    /** 12.0 <= FPS < 20.0. Tracking is functional but fine velocity loss precision is reduced. */
    MODERATE,

    /** < 12.0 FPS. MediaPipe pose estimation is severely lagging or thermally throttled. */
    DEGRADED,
}

/**
 * Real-time sliding-window frame rate monitor.
 *
 * Operates in O(1) arithmetic on a fixed-capacity circular buffer with zero
 * heap allocations per frame. Uses debouncing hysteresis to prevent state
 * flickering between health boundaries.
 */
class FrameRateMonitor(
    private val windowCapacity: Int = DEFAULT_WINDOW_CAPACITY,
) {

    private val timestamps = DoubleArray(windowCapacity)
    private var head = 0
    private var count = 0

    var currentFps: Double = Double.NaN
        private set

    var health: FrameRateHealth = FrameRateHealth.HEALTHY
        private set

    val isDegraded: Boolean get() = health == FrameRateHealth.DEGRADED

    /** Core-composed warning string when frame rate is degraded; null when healthy. */
    val warningMessage: String?
        get() = if (isDegraded && !currentFps.isNaN()) {
            "Camera frame rate low (${currentFps.roundToInt()} FPS) — tracking accuracy reduced"
        } else {
            null
        }

    private var degradedStreak = 0
    private var recoveryStreak = 0

    /**
     * Records one incoming frame at [timestampMs].
     *
     * O(1) execution, zero memory allocation.
     */
    fun onFrame(timestampMs: Double) {
        if (timestampMs.isNaN() || timestampMs <= 0.0) return

        timestamps[head] = timestampMs
        head = (head + 1) % windowCapacity
        if (count < windowCapacity) count++

        if (count >= MIN_FRAMES_FOR_FPS) {
            val oldestIndex = if (count < windowCapacity) 0 else head
            val oldestTime = timestamps[oldestIndex]
            val durationSec = (timestampMs - oldestTime) / 1000.0

            if (durationSec > 0.0) {
                currentFps = (count - 1) / durationSec
                updateHealth()
            }
        }
    }

    private fun updateHealth() {
        val fps = currentFps
        if (fps.isNaN()) return

        when (health) {
            FrameRateHealth.HEALTHY, FrameRateHealth.MODERATE -> {
                if (fps < DEGRADED_THRESHOLD_FPS) {
                    degradedStreak++
                    recoveryStreak = 0
                    if (degradedStreak >= HYSTERESIS_FRAMES) {
                        health = FrameRateHealth.DEGRADED
                    }
                } else {
                    degradedStreak = 0
                    health = if (fps >= HEALTHY_THRESHOLD_FPS) {
                        FrameRateHealth.HEALTHY
                    } else {
                        FrameRateHealth.MODERATE
                    }
                }
            }
            FrameRateHealth.DEGRADED -> {
                if (fps >= RECOVERY_THRESHOLD_FPS) {
                    recoveryStreak++
                    degradedStreak = 0
                    if (recoveryStreak >= HYSTERESIS_FRAMES) {
                        health = if (fps >= HEALTHY_THRESHOLD_FPS) {
                            FrameRateHealth.HEALTHY
                        } else {
                            FrameRateHealth.MODERATE
                        }
                    }
                } else {
                    recoveryStreak = 0
                }
            }
        }
    }

    /** Clears buffer and resets streaks for a new set. */
    fun reset() {
        head = 0
        count = 0
        degradedStreak = 0
        recoveryStreak = 0
        currentFps = Double.NaN
        health = FrameRateHealth.HEALTHY
    }

    companion object {
        const val DEFAULT_WINDOW_CAPACITY: Int = 15
        const val MIN_FRAMES_FOR_FPS: Int = 5

        /** Below 12.0 FPS, inflection points and velocity loss cannot be reliably tracked. */
        const val DEGRADED_THRESHOLD_FPS: Double = 12.0

        /** Recovery threshold requires sustained FPS >= 15.0 FPS. */
        const val RECOVERY_THRESHOLD_FPS: Double = 15.0

        /** 20.0+ FPS is healthy. */
        const val HEALTHY_THRESHOLD_FPS: Double = 20.0

        /** Number of consecutive frames required to confirm a transition into or out of DEGRADED. */
        const val HYSTERESIS_FRAMES: Int = 8
    }
}
