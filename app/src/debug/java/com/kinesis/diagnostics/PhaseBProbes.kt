package com.kinesis.diagnostics

import android.util.Log
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.pose.CameraFacing
import java.util.Locale
import kotlin.math.abs

/** The Logcat tag for the Phase B readout: `adb logcat -s KinesisPhaseB`. */
const val PHASE_B_LOG_TAG = "KinesisPhaseB"

/**
 * Debug builds: a probe that logs the readout to Logcat and hands it to [show].
 * The release twin of this function returns `null`.
 */
fun phaseBProbeOrNull(
    required: RequiredLandmarks,
    camera: CameraFacing,
    show: (String) -> Unit,
): PhaseBProbe? = DebugPhaseBProbe(required, camera) { line ->
    Log.i(PHASE_B_LOG_TAG, line)
    show(line)
}

/**
 * Publishes [PhaseBStats] about once a second - DECISIONS.md P3-17. The string
 * is built once a second, never per frame; everything per frame is in the stats.
 */
class DebugPhaseBProbe(
    required: RequiredLandmarks,
    private val camera: CameraFacing,
    private val publish: (String) -> Unit,
) : PhaseBProbe {

    val stats = PhaseBStats(required)
    private var lastPublishedMs = Long.MIN_VALUE

    override fun onFrame(timestampMs: Long, landmarks: Landmarks, leftAngle: Double, rightAngle: Double) {
        stats.onFrame(timestampMs, landmarks, leftAngle, rightAngle)
        publishIfDue(timestampMs)
    }

    override fun onNoPose(timestampMs: Long) {
        stats.onNoPose(timestampMs)
        publishIfDue(timestampMs)
    }

    private fun publishIfDue(timestampMs: Long) {
        if (lastPublishedMs != Long.MIN_VALUE && timestampMs - lastPublishedMs < PUBLISH_INTERVAL_MS) return
        lastPublishedMs = timestampMs
        publish(
            String.format(
                Locale.ROOT,
                "PHASE B  %.1f fps  max d-angle %.1f deg  vis %.2f-%.2f  %s camera",
                stats.framesPerSecond,
                stats.largestAngleChange,
                stats.minVisibility,
                stats.maxVisibility,
                camera.name,
            ),
        )
    }

    private companion object {
        const val PUBLISH_INTERVAL_MS = 1000L
    }
}

/**
 * The numbers behind the Phase B readout, over a sliding one-second window.
 * Pure, and allocation-free per frame: two preallocated ring arrays and a
 * handful of fields.
 *
 * - **Frames per second** counts every result MediaPipe delivered, pose or not,
 *   over the window's span.
 * - **Largest angle change** is the biggest frame-to-frame move of either side's
 *   unfiltered angle - the quantity the outlier gate judges against its 20
 *   degrees. A frame with nobody in it breaks the chain, so a change is never
 *   measured across a gap. `NaN` when nothing in the window was measurable.
 * - **Visibility** is the least and most visible of the profile's required
 *   landmarks on the latest frame. `NaN` when nobody is in shot.
 */
class PhaseBStats(private val required: RequiredLandmarks, capacity: Int = 256) {

    private val times = LongArray(capacity)
    private val changes = DoubleArray(capacity)
    private var oldest = 0
    private var count = 0

    private var previousLeft = Double.NaN
    private var previousRight = Double.NaN

    var minVisibility: Double = Double.NaN
        private set
    var maxVisibility: Double = Double.NaN
        private set

    fun onFrame(timestampMs: Long, landmarks: Landmarks, leftAngle: Double, rightAngle: Double) {
        val change = larger(change(previousLeft, leftAngle), change(previousRight, rightAngle))
        previousLeft = leftAngle
        previousRight = rightAngle

        var least = Double.NaN
        var most = Double.NaN
        for (index in 0 until required.size) {
            val visibility = landmarks.visibility(required[index])
            if (least.isNaN() || visibility < least) least = visibility
            if (most.isNaN() || visibility > most) most = visibility
        }
        minVisibility = least
        maxVisibility = most

        push(timestampMs, change)
    }

    fun onNoPose(timestampMs: Long) {
        previousLeft = Double.NaN
        previousRight = Double.NaN
        minVisibility = Double.NaN
        maxVisibility = Double.NaN
        push(timestampMs, Double.NaN)
    }

    val framesPerSecond: Double
        get() {
            if (count < 2) return Double.NaN
            val span = times[index(count - 1)] - times[oldest]
            return if (span <= 0L) Double.NaN else (count - 1) * 1000.0 / span
        }

    val largestAngleChange: Double
        get() {
            var largest = Double.NaN
            for (i in 0 until count) largest = larger(largest, changes[index(i)])
            return largest
        }

    private fun push(timestampMs: Long, change: Double) {
        while (count > 0 && timestampMs - times[oldest] > WINDOW_MS) {
            oldest = (oldest + 1) % times.size
            count--
        }
        if (count == times.size) {
            oldest = (oldest + 1) % times.size
            count--
        }
        val slot = index(count)
        times[slot] = timestampMs
        changes[slot] = change
        count++
    }

    private fun index(i: Int): Int = (oldest + i) % times.size

    private fun change(previous: Double, current: Double): Double =
        if (previous.isNaN() || current.isNaN()) Double.NaN else abs(current - previous)

    private fun larger(a: Double, b: Double): Double = when {
        a.isNaN() -> b
        b.isNaN() -> a
        a >= b -> a
        else -> b
    }

    private companion object {
        const val WINDOW_MS = 1000L
    }
}
