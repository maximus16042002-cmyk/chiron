package com.kinesis.core.analysis.setup

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.profile.CameraView
import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic estimator of user camera view orientation relative to the camera lens.
 *
 * Grounded in CLAUDE.md section 10 and DECISIONS.md P3-55.
 *
 * In 2D image coordinates, when a person turns from facing the camera (FRONT_ON) to
 * side profile (SIDE_ON), the projected lateral distance between contralateral bilateral
 * landmarks (left vs right shoulder, left vs right hip) shrinks dramatically toward zero.
 *
 * Normalized by torso scale (mid-shoulder to mid-hip length), empirical analysis across
 * MM-Fit development subjects establishes clear bimodal separation:
 *   - Side-on (push-up, sit-up): ratio <= 0.42 (medians 0.17 - 0.34)
 *   - Front-on (lateral raise, shoulder press, curls): ratio >= 0.60 (medians 0.67 - 0.83)
 *   - Angled (45 degrees): 0.42 < ratio < 0.60 (medians 0.45 - 0.60)
 */
object CameraViewEstimator {

    const val SIDE_ON_THRESHOLD: Double = 0.42
    const val FRONT_ON_THRESHOLD: Double = 0.60

    /**
     * Estimates the camera view orientation of [landmarks].
     *
     * @param landmarks Anatomical pose landmarks
     * @param torsoScale Torso length in image-plane width units (from LandmarkNormalizer)
     * @return Estimated [ViewOrientation], or null if landmarks or torso scale are not measurable.
     */
    fun estimate(landmarks: Landmarks, torsoScale: Double): ViewOrientation? {
        if (!torsoScale.isFinite() || torsoScale <= 0.0) return null

        val lsX = landmarks.x(LandmarkId.LEFT_SHOULDER)
        val rsX = landmarks.x(LandmarkId.RIGHT_SHOULDER)
        val lhX = landmarks.x(LandmarkId.LEFT_HIP)
        val rhX = landmarks.x(LandmarkId.RIGHT_HIP)

        if (!lsX.isFinite() || !rsX.isFinite() || !lhX.isFinite() || !rhX.isFinite()) return null

        val shoulderSpan = abs(lsX - rsX)
        val hipSpan = abs(lhX - rhX)
        val lateralSpan = max(shoulderSpan, hipSpan)

        val ratio = lateralSpan / torsoScale

        val (view, confidence) = when {
            ratio <= SIDE_ON_THRESHOLD -> {
                val conf = ((SIDE_ON_THRESHOLD - ratio) / SIDE_ON_THRESHOLD * 0.5 + 0.5).coerceIn(0.5, 1.0)
                CameraView.SIDE_ON to conf
            }
            ratio >= FRONT_ON_THRESHOLD -> {
                val conf = ((ratio - FRONT_ON_THRESHOLD) / 0.40 * 0.5 + 0.5).coerceIn(0.5, 1.0)
                CameraView.FRONT_ON to conf
            }
            else -> {
                val mid = (SIDE_ON_THRESHOLD + FRONT_ON_THRESHOLD) * 0.5
                val dist = abs(ratio - mid)
                val conf = (1.0 - dist / (mid - SIDE_ON_THRESHOLD) * 0.4).coerceIn(0.5, 0.9)
                CameraView.ANGLED_45 to conf
            }
        }

        return ViewOrientation(view = view, lateralRatio = ratio, confidence = confidence)
    }
}

data class ViewOrientation(
    val view: CameraView,
    val lateralRatio: Double,
    val confidence: Double,
)
