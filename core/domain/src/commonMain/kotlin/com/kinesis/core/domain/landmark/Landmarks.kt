package com.kinesis.core.domain.landmark

/**
 * One landmark, as MediaPipe reports it.
 *
 * `Double` throughout, per CLAUDE.md section 5 and CONTRACTS.md section 6 - not
 * `Float`, anywhere in the analysis core.
 */
data class Landmark(
    val x: Double,
    val y: Double,
    val z: Double,

    /** MediaPipe's confidence that this landmark is visible, in 0..1. */
    val visibility: Double,
)

/**
 * One frame of pose, in the **anatomical** frame of reference.
 *
 * CONTRACTS.md section 4 is the contract this type exists to carry: landmarks
 * entering the shared core are always anatomical. [LandmarkId.LEFT_KNEE] is the
 * subject's left knee whichever camera produced the frame and whichever side of
 * the image it appeared on. There is no `CameraFacing` here, no mirroring flag,
 * and no branch anywhere downstream that depends on either - that conversion has
 * already happened, in the platform pose adapter, before a `Landmarks` exists.
 *
 * `x` and `y` share one unit, the width of the image the pose model was given, so
 * an angle computed from them is the angle in the image; `z` is on roughly `x`'s
 * scale. The producer guarantees it - CONTRACTS.md section 4, DECISIONS.md P3-16.
 *
 * Stored as one flat `DoubleArray` of `x, y, z, visibility` per landmark, so
 * reads in the real-time path are index arithmetic with no allocation and no
 * boxing (CLAUDE.md section 6).
 */
class Landmarks private constructor(private val packed: DoubleArray) {

    fun x(id: LandmarkId): Double = packed[id.index * STRIDE]

    fun y(id: LandmarkId): Double = packed[id.index * STRIDE + 1]

    fun z(id: LandmarkId): Double = packed[id.index * STRIDE + 2]

    fun visibility(id: LandmarkId): Double = packed[id.index * STRIDE + 3]

    /**
     * The whole landmark as a value. Allocates, so prefer [x], [y], [z] and
     * [visibility] on the per-frame path and keep this for tests and export.
     */
    operator fun get(id: LandmarkId): Landmark = Landmark(
        x = x(id),
        y = y(id),
        z = z(id),
        visibility = visibility(id),
    )

    /** A copy of the backing array, in the layout [fromPacked] expects. */
    fun copyPacked(): DoubleArray = packed.copyOf()

    companion object {

        /** `x, y, z, visibility` per landmark. */
        const val STRIDE: Int = 4

        /** The required length of a packed buffer: [STRIDE] times 33. */
        const val PACKED_SIZE: Int = STRIDE * LandmarkId.COUNT

        /**
         * Builds a frame from a packed `x, y, z, visibility` buffer in MediaPipe
         * landmark order. The buffer is copied, so a caller reusing its own
         * scratch array cannot mutate a frame that has already been handed on.
         */
        fun fromPacked(values: DoubleArray): Landmarks {
            require(values.size == PACKED_SIZE) {
                "expected a packed buffer of " + PACKED_SIZE + " doubles, got " + values.size
            }
            return Landmarks(values.copyOf())
        }

        /**
         * Builds a frame from the two arrays the JS facade takes: `x, y, z` per
         * landmark in one, and visibility per landmark in the other.
         *
         * This is the seam between CONTRACTS.md section 5's primitives-only
         * boundary and the internal layout, and it is the only place the two
         * shapes are interleaved.
         */
        fun fromXyzAndVisibility(xyz: DoubleArray, visibility: DoubleArray): Landmarks {
            require(xyz.size == 3 * LandmarkId.COUNT) {
                "expected " + (3 * LandmarkId.COUNT) + " coordinates, got " + xyz.size
            }
            require(visibility.size == LandmarkId.COUNT) {
                "expected " + LandmarkId.COUNT + " visibility values, got " + visibility.size
            }
            val packed = DoubleArray(PACKED_SIZE)
            for (index in 0 until LandmarkId.COUNT) {
                packed[index * STRIDE] = xyz[index * 3]
                packed[index * STRIDE + 1] = xyz[index * 3 + 1]
                packed[index * STRIDE + 2] = xyz[index * 3 + 2]
                packed[index * STRIDE + 3] = visibility[index]
            }
            return Landmarks(packed)
        }
    }
}
