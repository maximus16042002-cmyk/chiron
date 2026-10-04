package com.kinesis.core.domain.rep

/**
 * 145-dimensional multi-signal movement context for exercise verification (P3-34, P3-52).
 *
 * Consists of 9 continuous kinematic signals resampled to 16 points each over the repetition window,
 * followed by the repetition duration in milliseconds.
 */
class MovementContext(
    val features: DoubleArray,
) {
    init {
        require(features.size == FEATURE_COUNT) {
            "MovementContext must have $FEATURE_COUNT features, got ${features.size}"
        }
    }

    val durationMs: Double get() = features[FEATURE_COUNT - 1]

    companion object {
        const val SIGNAL_COUNT = 9
        const val POINTS_PER_SIGNAL = 16
        const val FEATURE_COUNT = SIGNAL_COUNT * POINTS_PER_SIGNAL + 1 // 145

        val SIGNAL_NAMES = listOf(
            "left_elbow",
            "right_elbow",
            "left_shoulder",
            "right_shoulder",
            "left_hip",
            "right_hip",
            "left_knee",
            "right_knee",
            "trunk_lean",
        )
    }
}
