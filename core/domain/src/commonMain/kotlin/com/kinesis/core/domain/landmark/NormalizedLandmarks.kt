package com.kinesis.core.domain.landmark

/** A point in the normalised frame. Allocates, so not for the per-frame path. */
data class NormalizedPoint(val x: Double, val y: Double, val z: Double)

/**
 * One frame of pose after normalisation: translated so the mid-hip sits at the
 * origin, and scaled so one unit is one torso length.
 *
 * This is a distinct type from [Landmarks] on purpose. Un-normalised
 * coordinates depend on where the user stood and how far away they were, so any
 * threshold expressed against them is really a threshold on camera placement.
 * Keeping the two apart means a function that needs scale-invariant input says
 * so in its signature, and cannot be handed raw image coordinates by mistake.
 *
 * Still anatomical, and still free of any notion of which camera produced it.
 *
 * `y` follows MediaPipe's image convention and increases downward, so a landmark
 * above the hips has a negative `y`.
 */
class NormalizedLandmarks private constructor(

    /** `x, y, z` per landmark, in [LandmarkId] order. */
    private val packed: DoubleArray,

    /** The mid-hip in the input frame, i.e. what was subtracted. */
    val originX: Double,
    val originY: Double,
    val originZ: Double,

    /**
     * The torso length used as the unit, in input-frame units. Kept so that a
     * normalised quantity can be reported back in the frame it came from, and so
     * that a suspiciously small torso is visible downstream rather than hidden
     * inside a division.
     */
    val torsoScale: Double,
) {

    fun x(id: LandmarkId): Double = packed[id.index * STRIDE]

    fun y(id: LandmarkId): Double = packed[id.index * STRIDE + 1]

    fun z(id: LandmarkId): Double = packed[id.index * STRIDE + 2]

    operator fun get(id: LandmarkId): NormalizedPoint = NormalizedPoint(x(id), y(id), z(id))

    fun copyPacked(): DoubleArray = packed.copyOf()

    companion object {

        /** `x, y, z` per landmark. Visibility is not normalised, so is not carried. */
        const val STRIDE: Int = 3

        const val PACKED_SIZE: Int = STRIDE * LandmarkId.COUNT

        /**
         * Wraps an already-normalised buffer.
         *
         * Called by the normaliser in `:core:analysis`, and by tests that need a
         * known normalised frame. It validates shape and scale but cannot verify
         * that the values really are normalised - that is the normaliser's job,
         * and it is the only production caller.
         */
        fun of(
            packed: DoubleArray,
            originX: Double,
            originY: Double,
            originZ: Double,
            torsoScale: Double,
        ): NormalizedLandmarks {
            require(packed.size == PACKED_SIZE) {
                "expected a packed buffer of " + PACKED_SIZE + " doubles, got " + packed.size
            }
            require(torsoScale > 0.0) {
                "torso scale must be positive, got " + torsoScale
            }
            return NormalizedLandmarks(packed.copyOf(), originX, originY, originZ, torsoScale)
        }
    }
}
