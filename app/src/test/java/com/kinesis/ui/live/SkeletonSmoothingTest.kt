package com.kinesis.ui.live

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The drawn skeleton is smoothed; the measured one is not.
 *
 * On the phone the figure jittered badly: it drew each raw landmark position as
 * MediaPipe reported it, at the 12-16 results a second the phone manages. The
 * overlay now draws through a One Euro filter (Casiez, Roussel and Vogel, CHI
 * 2012), which smooths hard while a joint is still and follows it closely while
 * it moves. These tests pin both halves, and that the analysis never sees it.
 */
class SkeletonSmoothingTest {

    private val policy = VisibilityPolicy.PROVISIONAL
    private val frameMs = 67L // about 15 results a second, as measured on the phone

    /** A frame with the left wrist at ([x], [y]) seen with [visibility], every other landmark still. */
    private fun frame(x: Double, y: Double, visibility: Double = 0.95): Landmarks {
        val xyz = DoubleArray(3 * LandmarkId.COUNT) { if (it % 3 == 2) 0.0 else 0.5 }
        val seen = DoubleArray(LandmarkId.COUNT) { 0.95 }
        xyz[LandmarkId.LEFT_WRIST.index * 3] = x
        xyz[LandmarkId.LEFT_WRIST.index * 3 + 1] = y
        seen[LandmarkId.LEFT_WRIST.index] = visibility
        return Landmarks.fromXyzAndVisibility(xyz, seen)
    }

    private val wrist = LandmarkId.LEFT_WRIST.index

    private fun deviation(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    @Test
    fun a_still_joint_jitters_less_than_half_as_much() {
        val smoothing = SkeletonSmoothing()
        val random = Random(3)
        val raw = ArrayList<Double>()
        val drawn = ArrayList<Double>()
        for (i in 0 until 300) {
            val x = 0.5 + random.nextDouble(-0.01, 0.01)
            smoothing.update(frame(x, 0.6), i * frameMs, policy)
            if (i >= 30) {
                raw += x
                drawn += smoothing.x[wrist]
            }
        }
        assertTrue(
            "raw jitter ${deviation(raw)}, drawn ${deviation(drawn)}",
            deviation(drawn) < 0.5 * deviation(raw),
        )
    }

    /** A curl's wrist moves about 0.3 frame widths a second; the figure must stay on it. */
    @Test
    fun a_moving_joint_is_followed_closely() {
        val smoothing = SkeletonSmoothing()
        val random = Random(5)
        var worst = 0.0
        for (i in 0 until 60) {
            val truth = 0.3 + 0.3 * (i * frameMs) / 1000.0
            smoothing.update(frame(truth + random.nextDouble(-0.003, 0.003), 0.6), i * frameMs, policy)
            if (i >= 10) worst = maxOf(worst, abs(smoothing.x[wrist] - truth))
        }
        assertTrue("the drawn wrist trailed by up to $worst frame widths", worst < 0.015)
    }

    @Test
    fun a_sudden_move_is_caught_up_within_a_few_frames() {
        val smoothing = SkeletonSmoothing()
        for (i in 0 until 20) smoothing.update(frame(0.5, 0.6), i * frameMs, policy)
        for (i in 20 until 24) smoothing.update(frame(0.7, 0.6), i * frameMs, policy)
        assertEquals(0.7, smoothing.x[wrist], 0.01)
    }

    /** A joint that was lost starts again where it is seen, rather than gliding in from where it was. */
    @Test
    fun a_joint_seen_again_starts_where_it_is() {
        val smoothing = SkeletonSmoothing()
        for (i in 0 until 20) smoothing.update(frame(0.2, 0.6), i * frameMs, policy)
        smoothing.update(frame(0.8, 0.6, visibility = 0.1), 20 * frameMs, policy)
        smoothing.update(frame(0.8, 0.6), 21 * frameMs, policy)
        assertEquals(0.8, smoothing.x[wrist], 1e-12)
    }

    @Test
    fun clearing_the_figure_forgets_every_joint() {
        val smoothing = SkeletonSmoothing()
        for (i in 0 until 20) smoothing.update(frame(0.2, 0.6), i * frameMs, policy)
        smoothing.reset()
        smoothing.update(frame(0.8, 0.3), 21 * frameMs, policy)
        assertEquals(0.8, smoothing.x[wrist], 1e-12)
        assertEquals(0.3, smoothing.y[wrist], 1e-12)
    }

    /**
     * Smoothing is for the eye. The frame the counting receives is the one
     * MediaPipe produced, and the filter must not write into it.
     */
    @Test
    fun the_frame_the_analysis_receives_is_never_changed() {
        val smoothing = SkeletonSmoothing()
        val random = Random(9)
        for (i in 0 until 40) {
            val landmarks = frame(0.5 + random.nextDouble(-0.02, 0.02), 0.6)
            val before = LandmarkId.entries.flatMap { listOf(landmarks.x(it), landmarks.y(it)) }.toDoubleArray()
            smoothing.update(landmarks, i * frameMs, policy)
            val after = LandmarkId.entries.flatMap { listOf(landmarks.x(it), landmarks.y(it)) }.toDoubleArray()
            assertArrayEquals(before, after, 0.0)
        }
    }
}
