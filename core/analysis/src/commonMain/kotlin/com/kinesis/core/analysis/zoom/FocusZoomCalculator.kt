package com.kinesis.core.analysis.zoom

import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.profile.Posture
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time display-only focus zoom calculator.
 *
 * Implements FEATURE_PLAN.md Batch 1 (Focus zoom / auto-framing).
 *
 * ### Architectural Guarantees
 * 1. **Display only**: Nothing zoomed is ever sent to MediaPipe or the pose model.
 *    Kinematics, repetition counting, and technique rules always evaluate full-frame landmarks.
 * 2. **Whole-body suppression**: Exercises where whole-body posture is required
 *    (plank, push-up, squat, etc.) are strictly clamped to 1.0x unzoomed full frame.
 * 3. **Overhead headroom**: Exercises involving overhead arm reach preserve headroom above
 *    the upper bounds.
 * 4. **Hysteresis & smoothing**: Requires [FocusZoomPolicy.enterHysteresisFrames] consecutive
 *    reliable frames to zoom in, and [FocusZoomPolicy.exitHysteresisFrames] degraded frames to zoom out.
 *    Transitions are smoothed with an exponential moving average.
 * 5. **Zero per-frame allocations**: Updates primitive fields directly ($O(1)$ arithmetic per frame).
 */
class FocusZoomCalculator(
    private val profile: ExerciseProfile,
    private val policy: FocusZoomPolicy = FocusZoomPolicy.DEFAULT,
    private val visibilityPolicy: VisibilityPolicy = VisibilityPolicy.PROVISIONAL,
) {

    private val isWholeBody: Boolean =
        profile.id in WHOLE_BODY_EXERCISES ||
            profile.posture in WHOLE_BODY_POSTURES

    private val isOverhead: Boolean =
        profile.id in OVERHEAD_EXERCISES

    var currentScale: Double = 1.0
        private set

    var currentCenterX: Double = 0.5
        private set

    var currentCenterY: Double = 0.5
        private set

    var isEngaged: Boolean = false
        private set

    private var reliableFrameCount: Int = 0
    private var unreliableFrameCount: Int = 0

    /**
     * Evaluates camera frame landmarks and updates smoothed display zoom.
     * $O(1)$ arithmetic with strictly zero heap allocations.
     */
    fun step(landmarks: Landmarks) {
        if (!policy.isEnabled || isWholeBody) {
            smoothTowards(targetScale = 1.0, targetCx = 0.5, targetCy = 0.5)
            return
        }

        val req = profile.requiredLandmarks
        var minX = 1.0
        var maxX = 0.0
        var minY = 1.0
        var maxY = 0.0
        var allLandmarksVisible = true

        for (i in 0 until req.size) {
            val id = req[i]
            val vis = landmarks.visibility(id)
            val x = landmarks.x(id)
            val y = landmarks.y(id)

            if (!vis.isFinite() || !visibilityPolicy.isMeasurable(vis) || !x.isFinite() || !y.isFinite()) {
                allLandmarksVisible = false
                break
            }

            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        if (allLandmarksVisible && minX < maxX && minY < maxY) {
            reliableFrameCount++
            unreliableFrameCount = 0
            if (reliableFrameCount >= policy.enterHysteresisFrames) {
                isEngaged = true
            }
        } else {
            unreliableFrameCount++
            reliableFrameCount = 0
            if (unreliableFrameCount >= policy.exitHysteresisFrames) {
                isEngaged = false
            }
        }

        if (isEngaged && allLandmarksVisible) {
            val spanX = maxX - minX
            val spanY = maxY - minY
            val marginX = spanX * policy.marginRatio + 0.05
            val marginY = spanY * policy.marginRatio + 0.05
            val extraHeadroom = if (isOverhead) spanY * policy.headroomRatio + 0.08 else 0.0

            val boxMinX = max(0.0, minX - marginX)
            val boxMaxX = min(1.0, maxX + marginX)
            val boxMinY = max(0.0, minY - marginY - extraHeadroom)
            val boxMaxY = min(1.0, maxY + marginY)

            val boxW = max(0.05, boxMaxX - boxMinX)
            val boxH = max(0.05, boxMaxY - boxMinY)

            val desiredScale = min(1.0 / boxW, 1.0 / boxH).coerceIn(1.0, policy.maxZoom)
            val targetCx = (boxMinX + boxMaxX) / 2.0
            val targetCy = (boxMinY + boxMaxY) / 2.0

            val halfSpan = 0.5 / desiredScale
            val clampedCx = targetCx.coerceIn(halfSpan, 1.0 - halfSpan)
            val clampedCy = targetCy.coerceIn(halfSpan, 1.0 - halfSpan)

            smoothTowards(targetScale = desiredScale, targetCx = clampedCx, targetCy = clampedCy)
        } else {
            smoothTowards(targetScale = 1.0, targetCx = 0.5, targetCy = 0.5)
        }
    }

    /**
     * Evaluates camera frame landmarks and returns snapshot of target.
     */
    fun onFrame(landmarks: Landmarks): FocusZoomTarget {
        step(landmarks)
        return snapshot()
    }

    /**
     * Handles lost tracking frame, driving exit hysteresis with zero allocations.
     */
    fun stepNothingSeen() {
        unreliableFrameCount++
        reliableFrameCount = 0
        if (unreliableFrameCount >= policy.exitHysteresisFrames) {
            isEngaged = false
        }
        smoothTowards(targetScale = 1.0, targetCx = 0.5, targetCy = 0.5)
    }

    /**
     * Handles lost tracking frame, driving exit hysteresis and returning target snapshot.
     */
    fun onNothingSeen(): FocusZoomTarget {
        stepNothingSeen()
        return snapshot()
    }

    /**
     * Resets state back to neutral 1.0x unzoomed full frame.
     */
    fun reset() {
        currentScale = 1.0
        currentCenterX = 0.5
        currentCenterY = 0.5
        isEngaged = false
        reliableFrameCount = 0
        unreliableFrameCount = 0
    }

    fun snapshot(): FocusZoomTarget = FocusZoomTarget(
        scale = currentScale,
        centerX = currentCenterX,
        centerY = currentCenterY,
    )

    private fun smoothTowards(targetScale: Double, targetCx: Double, targetCy: Double) {
        val alpha = policy.smoothingAlpha
        currentScale += alpha * (targetScale - currentScale)
        currentCenterX += alpha * (targetCx - currentCenterX)
        currentCenterY += alpha * (targetCy - currentCenterY)

        // Snap to identity when very close to 1.0 to avoid floating point drift
        if (abs(currentScale - 1.0) < 0.005 && targetScale == 1.0) {
            currentScale = 1.0
            currentCenterX = 0.5
            currentCenterY = 0.5
        }
    }

    companion object {
        val WHOLE_BODY_EXERCISES: Set<String> = setOf(
            "squat",
            "pushup",
            "plank",
            "side_plank",
            "wall_sit",
            "glute_bridge",
            "situp",
            "lunge",
        )

        val WHOLE_BODY_POSTURES: Set<Posture> = setOf(
            Posture.PRONE,
            Posture.SUPINE,
            Posture.SIDE_LYING,
        )

        val OVERHEAD_EXERCISES: Set<String> = setOf(
            "shoulder_press",
        )
    }
}
