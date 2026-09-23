package com.kinesis.core.domain.landmark

/**
 * Whether a frame is good enough to analyse - CLAUDE.md section 10,
 * CONTRACTS.md sections 7 and 8.
 *
 * A discrete verdict, so it must match exactly across platforms (CONTRACTS.md
 * section 6), and an enum so the per-frame path allocates nothing to produce
 * one. The detail of *which* landmarks were poor is a post-rep concern and is
 * not carried here.
 */
enum class FrameValidity {

    /** Every landmark the profile needs was seen clearly. */
    VALID,

    /**
     * Every landmark the profile needs was seen, but at least one only
     * marginally. Measurement may continue if it is honest to do so; technique
     * judgement may not.
     */
    DEGRADED,

    /**
     * At least one landmark the profile needs was effectively not seen. Scoring
     * is blocked rather than producing confident nonsense.
     */
    INVALID,
}

/**
 * The visibility thresholds a frame is judged against.
 *
 * These are configuration, not findings. A landmark at or above [reliable] is
 * treated as seen; below [marginal] it is treated as not seen; in between it is
 * usable but degraded.
 */
data class VisibilityPolicy(val reliable: Double, val marginal: Double) {

    init {
        require(reliable in 0.0..1.0) { "reliable must be in 0..1, got " + reliable }
        require(marginal in 0.0..1.0) { "marginal must be in 0..1, got " + marginal }
        require(marginal <= reliable) {
            "marginal (" + marginal + ") cannot exceed reliable (" + reliable + ")"
        }
    }

    /*
     * The only comparisons against these thresholds - DECISIONS.md P3-16.
     * Written as `>=` so a NaN visibility is neither: `NaN < marginal` is false,
     * and the `<` form every caller used to write let an unstated visibility
     * through as fully seen. One definition, so the validity gate, the
     * kinematics and the overlay cannot disagree about a landmark.
     */

    /** At or above [marginal]: seen well enough to measure. False for NaN. */
    fun isMeasurable(visibility: Double): Boolean = visibility >= marginal

    /** At or above [reliable]: seen clearly. False for NaN. */
    fun isReliable(visibility: Double): Boolean = visibility >= reliable

    companion object {

        /**
         * **Provisional. Not measured on this system.**
         *
         * Placeholder thresholds so the validity gate can be built and tested
         * before a device exists to calibrate them against. CLAUDE.md section 23
         * is MEASURE then TEST then DOCUMENT then CLAIM, and none of that has
         * happened for these two numbers - they are a starting point, chosen to
         * be unremarkable, and they support no claim of any kind.
         *
         * P4 device testing sets the real values. When it does, the outcome goes
         * in DECISIONS.md and this constant is replaced by one that cites it.
         */
        val PROVISIONAL: VisibilityPolicy = VisibilityPolicy(reliable = 0.70, marginal = 0.40)
    }
}

/**
 * The landmarks one exercise profile actually depends on.
 *
 * Validity is judged against these rather than against all 33. A squat does not
 * care that the wrists are out of frame, and a gate that insisted otherwise
 * would block scoring on perfectly good repetitions.
 *
 * Backed by an array with indexed access so the per-frame check can loop without
 * allocating an iterator.
 */
class RequiredLandmarks private constructor(private val ids: Array<LandmarkId>) {

    val size: Int get() = ids.size

    operator fun get(index: Int): LandmarkId = ids[index]

    fun toList(): List<LandmarkId> = ids.toList()

    override fun toString(): String = "RequiredLandmarks(" + ids.joinToString() + ")"

    companion object {

        fun of(vararg ids: LandmarkId): RequiredLandmarks {
            require(ids.isNotEmpty()) {
                "a profile must depend on at least one landmark"
            }
            return RequiredLandmarks(ids.distinct().toTypedArray())
        }
    }
}
