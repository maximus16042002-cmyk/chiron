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

    /** MediaPipe's thread. */
    val stats = PhaseBStats(required)

    /** The analysis thread's numbers, read here through its published snapshot. */
    val capture = CaptureStats()

    // MediaPipe's thread: latency since the last readout.
    private var latencyCount = 0
    private var latencySumNanos = 0L
    private var latencyMaxNanos = 0L

    private var lastPublishedMs = Long.MIN_VALUE

    override fun onFrame(timestampMs: Long, landmarks: Landmarks, leftAngle: Double, rightAngle: Double) {
        stats.onFrame(timestampMs, landmarks, leftAngle, rightAngle)
        publishIfDue(timestampMs)
    }

    override fun onNoPose(timestampMs: Long) {
        stats.onNoPose(timestampMs)
        publishIfDue(timestampMs)
    }

    override fun onCameraFrame(timestampMs: Long) = capture.onCameraFrame(timestampMs)

    override fun onSubmitted(timestampMs: Long, convertNanos: Long, handOverNanos: Long) =
        capture.onSubmitted(convertNanos, handOverNanos)

    override fun onAnswered(latencyNanos: Long) {
        latencyCount++
        latencySumNanos += latencyNanos
        if (latencyNanos > latencyMaxNanos) latencyMaxNanos = latencyNanos
    }

    /**
     * Three lines, about once a second - DECISIONS.md P3-19. Everything one
     * camera session needs to explain its frame rate: frames the camera delivered,
     * frames handed to MediaPipe, results back, what each stage cost, and each
     * side's runs of unmeasurable frames - the input a gap tolerance needs.
     */
    private fun publishIfDue(timestampMs: Long) {
        if (lastPublishedMs != Long.MIN_VALUE && timestampMs - lastPublishedMs < PUBLISH_INTERVAL_MS) return
        lastPublishedMs = timestampMs
        val latencyMeanMs = if (latencyCount == 0) Double.NaN else latencySumNanos / 1e6 / latencyCount
        val latencyMaxMs = if (latencyCount == 0) Double.NaN else latencyMaxNanos / 1e6
        latencyCount = 0
        latencySumNanos = 0L
        latencyMaxNanos = 0L
        val gaps = stats.gaps
        publish(
            String.format(
                Locale.ROOT,
                "PHASE B  %s camera  results %.1f fps  camera %.1f fps  sent %.1f fps\n" +
                    "convert %.1f ms (max %.1f)  hand-over %.1f ms  mediapipe %.0f ms (max %.0f)\n" +
                    "max d-angle %.1f deg  vis %.2f-%.2f  gaps L %d (max %d f, %d ms)  R %d (max %d f, %d ms)",
                camera.name,
                stats.framesPerSecond,
                capture.cameraFramesPerSecond,
                capture.submittedPerSecond,
                capture.meanConvertMs,
                capture.maxConvertMs,
                capture.meanHandOverMs,
                latencyMeanMs,
                latencyMaxMs,
                stats.largestAngleChange,
                stats.minVisibility,
                stats.maxVisibility,
                gaps.leftRuns, gaps.leftLongestFrames, gaps.leftLongestMs,
                gaps.rightRuns, gaps.rightLongestFrames, gaps.rightLongestMs,
            ),
        )
        gaps.clear()
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

    /** Each side's runs of unmeasurable frames since the last [GapRuns.clear]. */
    val gaps = GapRuns()

    fun onFrame(timestampMs: Long, landmarks: Landmarks, leftAngle: Double, rightAngle: Double) {
        gaps.onFrame(timestampMs, leftAngle.isNaN(), rightAngle.isNaN())
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
        gaps.onFrame(timestampMs, leftUnmeasured = true, rightUnmeasured = true)
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

/**
 * Each side's runs of unmeasurable frames - DECISIONS.md P3-19, for the gap
 * tolerance P3-19 proposes and leaves unset until these are measured.
 *
 * A run is one or more consecutive results in which a side's angle could not be
 * measured: a required landmark below the visibility threshold, or nobody in the
 * frame. Its length is counted in results, and in milliseconds from the last
 * measured result before it to the first after it - the time a repetition in
 * progress would have to bridge. A run still open is not counted until it ends.
 * Allocation-free. MediaPipe's thread.
 */
class GapRuns {

    var leftRuns = 0
        private set
    var leftLongestFrames = 0
        private set
    var leftLongestMs = 0L
        private set
    var rightRuns = 0
        private set
    var rightLongestFrames = 0
        private set
    var rightLongestMs = 0L
        private set

    private var leftOpen = 0
    private var rightOpen = 0
    private var leftLastSeenMs = Long.MIN_VALUE
    private var rightLastSeenMs = Long.MIN_VALUE

    fun onFrame(timestampMs: Long, leftUnmeasured: Boolean, rightUnmeasured: Boolean) {
        if (leftUnmeasured) {
            leftOpen++
        } else {
            if (leftOpen > 0 && leftLastSeenMs != Long.MIN_VALUE) {
                leftRuns++
                if (leftOpen > leftLongestFrames) leftLongestFrames = leftOpen
                val span = timestampMs - leftLastSeenMs
                if (span > leftLongestMs) leftLongestMs = span
            }
            leftOpen = 0
            leftLastSeenMs = timestampMs
        }
        if (rightUnmeasured) {
            rightOpen++
        } else {
            if (rightOpen > 0 && rightLastSeenMs != Long.MIN_VALUE) {
                rightRuns++
                if (rightOpen > rightLongestFrames) rightLongestFrames = rightOpen
                val span = timestampMs - rightLastSeenMs
                if (span > rightLongestMs) rightLongestMs = span
            }
            rightOpen = 0
            rightLastSeenMs = timestampMs
        }
    }

    /** Clears the counts after a readout; open runs carry on. */
    fun clear() {
        leftRuns = 0
        leftLongestFrames = 0
        leftLongestMs = 0L
        rightRuns = 0
        rightLongestFrames = 0
        rightLongestMs = 0L
    }
}

/**
 * The analysis thread's half of the readout - DECISIONS.md P3-19: frames the
 * camera delivered, frames handed to MediaPipe, and what converting and handing
 * over cost, over one-second windows of camera time.
 *
 * Counted on the analysis thread and published at each window's end into
 * volatile fields, which MediaPipe's thread reads for the readout. The two
 * threads share nothing else. Allocation-free.
 */
class CaptureStats {

    @Volatile var cameraFramesPerSecond: Double = Double.NaN
        private set

    @Volatile var submittedPerSecond: Double = Double.NaN
        private set

    @Volatile var meanConvertMs: Double = Double.NaN
        private set

    @Volatile var maxConvertMs: Double = Double.NaN
        private set

    @Volatile var meanHandOverMs: Double = Double.NaN
        private set

    // Analysis thread only.
    private var windowStartMs = Long.MIN_VALUE
    private var frames = 0
    private var submitted = 0
    private var convertSumNanos = 0L
    private var convertMaxNanos = 0L
    private var handOverSumNanos = 0L

    fun onCameraFrame(timestampMs: Long) {
        if (windowStartMs == Long.MIN_VALUE) windowStartMs = timestampMs
        val span = timestampMs - windowStartMs
        if (span >= WINDOW_MS) {
            cameraFramesPerSecond = frames * 1000.0 / span
            submittedPerSecond = submitted * 1000.0 / span
            meanConvertMs = if (submitted == 0) Double.NaN else convertSumNanos / 1e6 / submitted
            maxConvertMs = if (submitted == 0) Double.NaN else convertMaxNanos / 1e6
            meanHandOverMs = if (submitted == 0) Double.NaN else handOverSumNanos / 1e6 / submitted
            windowStartMs = timestampMs
            frames = 0
            submitted = 0
            convertSumNanos = 0L
            convertMaxNanos = 0L
            handOverSumNanos = 0L
        }
        frames++
    }

    fun onSubmitted(convertNanos: Long, handOverNanos: Long) {
        submitted++
        convertSumNanos += convertNanos
        if (convertNanos > convertMaxNanos) convertMaxNanos = convertNanos
        handOverSumNanos += handOverNanos
    }

    private companion object {
        const val WINDOW_MS = 1000L
    }
}
