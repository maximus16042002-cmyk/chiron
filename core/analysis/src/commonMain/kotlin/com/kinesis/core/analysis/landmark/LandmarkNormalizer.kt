package com.kinesis.core.analysis.landmark

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.NormalizedLandmarks
import kotlin.math.sqrt

/**
 * Step one of the real-time path - CONTRACTS.md section 7:
 *
 *     landmarks -> normalise -> validity -> current-frame kinematics
 *
 * Removes the two things about a frame that are properties of the filming rather
 * than of the movement: where in the image the subject stood, and how far away
 * they were. The mid-hip becomes the origin, and one torso length becomes the
 * unit. After this, the same squat measures the same whether it was filmed from
 * two metres or four.
 *
 * O(1) per frame in the sense CLAUDE.md section 6 requires: a fixed 33-landmark
 * pass, no session history, no growth over a set.
 */
object LandmarkNormalizer {

    /**
     * Below this, the torso is too short to divide by and the frame carries no
     * usable scale - the subject is edge-on, badly occluded, or the pose
     * estimate has collapsed. Expressed in input-frame units, which since
     * DECISIONS.md P3-16 means widths of the analysis frame on both axes.
     *
     * Provisional, like VisibilityPolicy.PROVISIONAL, and for the same reason:
     * P4 device testing sets the real value.
     */
    const val MIN_TORSO_SCALE: Double = 1e-6

    /**
     * Normalises one frame, or returns `null` when the frame has no measurable
     * torso to scale by.
     *
     * Null rather than a thrown exception or a silent fallback scale: a frame
     * without a torso is an ordinary event during a set - the user steps out of
     * shot, turns, or is briefly occluded - and the caller decides what to do
     * about it. Substituting a default scale here would make every downstream
     * measurement quietly wrong instead of absent.
     */
    fun normalize(landmarks: Landmarks): NormalizedLandmarks? {
        val torsoScale = torsoScale(landmarks)
        if (!(torsoScale > MIN_TORSO_SCALE)) return null

        val midHipX = midHipX(landmarks)
        val midHipY = midHipY(landmarks)
        val midHipZ = (landmarks.z(LandmarkId.LEFT_HIP) + landmarks.z(LandmarkId.RIGHT_HIP)) * 0.5

        val packed = DoubleArray(NormalizedLandmarks.PACKED_SIZE)
        for (index in 0 until LandmarkId.COUNT) {
            val id = LandmarkId.fromIndex(index)
            val out = index * NormalizedLandmarks.STRIDE
            packed[out] = (landmarks.x(id) - midHipX) / torsoScale
            packed[out + 1] = (landmarks.y(id) - midHipY) / torsoScale
            packed[out + 2] = (landmarks.z(id) - midHipZ) / torsoScale
        }

        return NormalizedLandmarks.of(
            packed = packed,
            originX = midHipX,
            originY = midHipY,
            originZ = midHipZ,
            torsoScale = torsoScale,
        )
    }

    /**
     * Whether [normalize] would succeed, without building its output - the
     * question the per-frame path actually asks, answered with no allocation.
     * DECISIONS.md P3-16. Same computation, so the two cannot disagree.
     */
    fun hasMeasurableTorso(landmarks: Landmarks): Boolean = torsoScale(landmarks) > MIN_TORSO_SCALE

    // Explicit midpoints of two named landmarks. A geometric centre between two
    // body parts, not a reduction of a BilateralSignal - no left/right quantity
    // is averaged away, and both hips remain separately readable in the output.
    private fun midHipX(landmarks: Landmarks): Double =
        (landmarks.x(LandmarkId.LEFT_HIP) + landmarks.x(LandmarkId.RIGHT_HIP)) * 0.5

    private fun midHipY(landmarks: Landmarks): Double =
        (landmarks.y(LandmarkId.LEFT_HIP) + landmarks.y(LandmarkId.RIGHT_HIP)) * 0.5

    /**
     * Mid-shoulder to mid-hip, in the image plane only. MediaPipe's z is a
     * relative depth estimate rather than a measured one, and folding it into the
     * scale would make the unit fluctuate with depth noise on every frame.
     * `NaN` when any of the four landmarks has no coordinates.
     */
    private fun torsoScale(landmarks: Landmarks): Double {
        val midShoulderX =
            (landmarks.x(LandmarkId.LEFT_SHOULDER) + landmarks.x(LandmarkId.RIGHT_SHOULDER)) * 0.5
        val midShoulderY =
            (landmarks.y(LandmarkId.LEFT_SHOULDER) + landmarks.y(LandmarkId.RIGHT_SHOULDER)) * 0.5
        val torsoDx = midShoulderX - midHipX(landmarks)
        val torsoDy = midShoulderY - midHipY(landmarks)
        return sqrt(torsoDx * torsoDx + torsoDy * torsoDy)
    }
}
