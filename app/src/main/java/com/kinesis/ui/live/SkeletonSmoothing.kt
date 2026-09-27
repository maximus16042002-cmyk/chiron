package com.kinesis.ui.live

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import kotlin.math.PI
import kotlin.math.abs

/**
 * Steadies the **drawn** skeleton - display only.
 *
 * On the phone the figure jittered badly: it drew each raw landmark position as
 * MediaPipe reported it, at the 12-16 results a second the phone manages, so
 * every tremble in the estimate showed. This runs each drawn joint through a One
 * Euro filter (Casiez, Roussel and Vogel, "1 Euro Filter", CHI 2012): a low-pass
 * filter whose cutoff rises with the joint's speed, so it smooths hard while a
 * joint is still and follows it closely while it moves.
 *
 * ### It changes nothing that is measured
 *
 * The analysis receives MediaPipe's frame before the overlay does, and this
 * never writes into it: it keeps its own copy of the positions it draws. No
 * count, angle or record is affected - CLAUDE.md section 2 keeps analysis in the
 * core, and this is rendering. The colours still come from the raw visibility.
 *
 * ### Per frame
 *
 * Fixed state per landmark in preallocated arrays, a constant amount of
 * arithmetic, and no allocation - CLAUDE.md section 6.
 *
 * ### The numbers are for the eye
 *
 * [minCutoffHz], [beta] and [derivativeCutoffHz] were chosen for how the figure
 * looks at about 15 results a second, with positions in frame widths. They make
 * no claim about anything, and changing them changes only the drawing.
 */
class SkeletonSmoothing(
    /** Cutoff while still. Lower is steadier. */
    private val minCutoffHz: Double = 0.3,
    /** How fast the cutoff rises with speed, per frame width a second. Higher follows movement more closely. */
    private val beta: Double = 12.0,
    /** Cutoff for the speed estimate itself. */
    private val derivativeCutoffHz: Double = 1.0,
) {

    /** The positions to draw, per landmark, in frame widths. */
    val x = DoubleArray(LandmarkId.COUNT)
    val y = DoubleArray(LandmarkId.COUNT)

    private val speedX = DoubleArray(LandmarkId.COUNT)
    private val speedY = DoubleArray(LandmarkId.COUNT)
    private val lastMs = LongArray(LandmarkId.COUNT)
    private val tracking = BooleanArray(LandmarkId.COUNT)

    /** One frame. Joints not seen well enough to measure are drawn raw and start again when seen. */
    fun update(landmarks: Landmarks, timestampMs: Long, policy: VisibilityPolicy) {
        for (id in JOINTS) {
            val i = id.index
            val rawX = landmarks.x(id)
            val rawY = landmarks.y(id)
            val seen = policy.isMeasurable(landmarks.visibility(id)) && rawX.isFinite() && rawY.isFinite()
            if (!seen || !tracking[i]) {
                // Lost, or first seen: draw it where it is. Gliding in from a stale
                // position would draw a limb somewhere it never was.
                x[i] = rawX
                y[i] = rawY
                speedX[i] = 0.0
                speedY[i] = 0.0
                lastMs[i] = timestampMs
                tracking[i] = seen
                continue
            }
            val dt = (timestampMs - lastMs[i]) / 1000.0
            if (dt <= 0.0) continue // the same instant again: nothing new to follow
            lastMs[i] = timestampMs

            val newSpeedX = smoothed(speedX[i], (rawX - x[i]) / dt, alpha(derivativeCutoffHz, dt))
            val newSpeedY = smoothed(speedY[i], (rawY - y[i]) / dt, alpha(derivativeCutoffHz, dt))
            speedX[i] = newSpeedX
            speedY[i] = newSpeedY
            x[i] = smoothed(x[i], rawX, alpha(minCutoffHz + beta * abs(newSpeedX), dt))
            y[i] = smoothed(y[i], rawY, alpha(minCutoffHz + beta * abs(newSpeedY), dt))
        }
    }

    /** Forgets every joint, for when the figure is cleared. */
    fun reset() {
        for (i in tracking.indices) tracking[i] = false
    }

    private fun smoothed(previous: Double, current: Double, alpha: Double): Double =
        previous + alpha * (current - previous)

    /** The exponential smoothing factor for a first-order low-pass at [cutoffHz] over [dt] seconds. */
    private fun alpha(cutoffHz: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoffHz)
        return 1.0 / (1.0 + tau / dt)
    }

    private companion object {
        /** An array, so iterating it per frame allocates no iterator. */
        val JOINTS: Array<LandmarkId> = LandmarkId.entries.toTypedArray()
    }
}
