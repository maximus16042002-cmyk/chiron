package com.kinesis.pose

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks

/**
 * Which lens produced a frame.
 *
 * This type lives in `:app` and must never appear in `:core:domain` or
 * `:core:analysis`. CONTRACTS.md section 4: there is no parameter, no flag and
 * no branch anywhere in the shared core that depends on which camera produced a
 * frame. SharedCoreSourceGuardTest fails the build if one appears.
 */
enum class CameraFacing {
    FRONT,
    BACK,
}

/**
 * One frame of pose exactly as the pose estimator reported it, in **camera**
 * coordinates.
 *
 * Deliberately a different type from [Landmarks], which is anatomical. The two
 * are the same shape and trivially confusable, and confusing them is the exact
 * failure this whole file exists to prevent, so the compiler is made to tell
 * them apart.
 *
 * Layout matches `Landmarks.fromPacked`: `x, y, z, visibility` per landmark, in
 * MediaPipe order.
 */
class RawLandmarks(packed: DoubleArray) {

    private val packed: DoubleArray

    init {
        require(packed.size == Landmarks.PACKED_SIZE) {
            "expected a packed buffer of ${Landmarks.PACKED_SIZE} doubles, got ${packed.size}"
        }
        this.packed = packed.copyOf()
    }

    fun copyPacked(): DoubleArray = packed.copyOf()
}

/**
 * Converts camera coordinates into anatomical left and right, before anything
 * reaches the shared core.
 *
 * FINAL_ARCHITECTURE section 12-D names front-camera mirroring as one of the
 * three highest technical risks, and the one that fails most quietly. When it
 * goes wrong nothing crashes and nothing looks wrong on screen: the skeleton
 * tracks the user perfectly, and every left/right measurement in the system is
 * simply swapped. Asymmetry findings then report the wrong limb, with full
 * confidence, forever.
 *
 * So the correction happens exactly once, here, at the edge. Downstream of this
 * class a landmark named `LEFT_KNEE` is the subject's left knee, and nothing
 * needs to ask again.
 *
 * **The assumption this makes, stated plainly.** The front camera feed is taken
 * to be delivered horizontally mirrored, which is what a front-facing preview
 * conventionally does. A mirrored image has two consequences, and both have to
 * be undone: the pose model labels landmarks from the image it was given, so the
 * subject's left wrist arrives in the `RIGHT_WRIST` slot; and the x coordinate
 * is reflected about the centre of the frame.
 *
 * If P4 device testing shows the analysis stream is *not* mirrored - CameraX can
 * be configured either way, and the preview and the analysis use case do not
 * have to agree - this is the one place that changes. Nowhere else in the
 * codebase knows the difference exists.
 *
 * ### One unit for both axes - DECISIONS.md P3-16
 *
 * MediaPipe reports x as a fraction of the frame's width and y as a fraction of
 * its height. On a frame that is not square those are different units, and an
 * angle computed from them is wrong by up to about sixteen degrees on a portrait
 * 3:4 frame (P3-15). So y is rescaled into frame widths: x runs 0..1 across the
 * frame, y runs 0..height/width down it, and a pixel is the same length in both
 * directions. z is left alone - MediaPipe gives it "roughly the same scale as x",
 * which is the unit both axes now share.
 *
 * Frame shape is camera geometry, so it is corrected here and the shared core
 * never learns a frame had one. `AspectCorrectionRegressionTest` pins it.
 */
class PoseAdapter {

    /**
     * @param raw one frame in camera coordinates.
     * @param facing which lens produced it.
     * @param frameWidthPx width of the image the pose model was given, upright.
     * @param frameHeightPx its height.
     * @return the same frame in the anatomical frame of reference, both axes in
     *   frame widths, ready for the shared core.
     */
    fun toAnatomical(
        raw: RawLandmarks,
        facing: CameraFacing,
        frameWidthPx: Int,
        frameHeightPx: Int,
    ): Landmarks {
        require(frameWidthPx > 0 && frameHeightPx > 0) {
            "frame size must be positive, got " + frameWidthPx + "x" + frameHeightPx
        }
        val anatomical = when (facing) {
            // The back camera sees the subject the way another person would.
            // Nothing to undo.
            CameraFacing.BACK -> raw.copyPacked()

            CameraFacing.FRONT -> unmirror(raw.copyPacked())
        }
        val heightInWidths = frameHeightPx.toDouble() / frameWidthPx
        for (index in 0 until LandmarkId.COUNT) {
            anatomical[index * Landmarks.STRIDE + 1] *= heightInWidths
        }
        return Landmarks.fromPacked(anatomical)
    }

    /**
     * Undoes a horizontal mirror: reflects x about the centre of the normalised
     * frame, and moves each landmark into its opposite-side slot.
     *
     * Both halves are needed. Reflecting x alone leaves the left wrist labelled
     * as the right one; swapping the slots alone leaves it on the wrong side of
     * the image. Either on its own still passes a naive round-trip check, which
     * is why MirroringRegressionTest checks the two separately.
     */
    private fun unmirror(source: DoubleArray): DoubleArray {
        val out = DoubleArray(Landmarks.PACKED_SIZE)
        val stride = Landmarks.STRIDE
        for (index in 0 until LandmarkId.COUNT) {
            val from = index * stride
            val to = LandmarkId.fromIndex(index).mirrored.index * stride
            out[to] = MIRROR_AXIS - source[from]
            out[to + 1] = source[from + 1]
            out[to + 2] = source[from + 2]
            out[to + 3] = source[from + 3]
        }
        return out
    }

    private companion object {
        /**
         * x is reflected about the centre of the frame. MediaPipe reports x in
         * 0..1 across the image width, so the reflection of x is 1.0 - x.
         */
        const val MIRROR_AXIS = 1.0
    }
}
