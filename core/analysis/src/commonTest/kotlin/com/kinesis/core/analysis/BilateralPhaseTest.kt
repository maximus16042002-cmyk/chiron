package com.kinesis.core.analysis

import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.analysis.rep.BilateralPhase
import com.kinesis.core.analysis.rep.BilateralRepTracker
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.core.domain.rep.MovementPhase.ASCENDING
import com.kinesis.core.domain.rep.MovementPhase.BOTTOM
import com.kinesis.core.domain.rep.MovementPhase.DESCENDING
import com.kinesis.core.domain.rep.MovementPhase.IDLE
import com.kinesis.core.domain.rep.MovementPhase.TOP
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the one rule that shows two sides' phases as one - CLAUDE.md section 3,
 * DECISIONS.md P3-15.
 *
 * The rule is display-only, but it is still a reduction of bilateral state, so
 * it is fixed here rather than left to drift. Changing it means changing the
 * table below and writing down why.
 */
class BilateralPhaseTest {

    /** Rows are left, columns are right, in [MovementPhase] declaration order. */
    private val expected: Map<Pair<MovementPhase, MovementPhase>, MovementPhase> = mapOf(
        (IDLE to IDLE) to IDLE,
        (IDLE to TOP) to TOP,
        (IDLE to DESCENDING) to DESCENDING,
        (IDLE to BOTTOM) to BOTTOM,
        (IDLE to ASCENDING) to ASCENDING,

        (TOP to IDLE) to TOP,
        (TOP to TOP) to TOP,
        (TOP to DESCENDING) to DESCENDING,
        (TOP to BOTTOM) to BOTTOM,
        (TOP to ASCENDING) to ASCENDING,

        (DESCENDING to IDLE) to DESCENDING,
        (DESCENDING to TOP) to DESCENDING,
        (DESCENDING to DESCENDING) to DESCENDING,
        (DESCENDING to BOTTOM) to BOTTOM,
        (DESCENDING to ASCENDING) to ASCENDING,

        (BOTTOM to IDLE) to BOTTOM,
        (BOTTOM to TOP) to BOTTOM,
        (BOTTOM to DESCENDING) to BOTTOM,
        (BOTTOM to BOTTOM) to BOTTOM,
        (BOTTOM to ASCENDING) to ASCENDING,

        (ASCENDING to IDLE) to ASCENDING,
        (ASCENDING to TOP) to ASCENDING,
        (ASCENDING to DESCENDING) to ASCENDING,
        (ASCENDING to BOTTOM) to ASCENDING,
        (ASCENDING to ASCENDING) to ASCENDING,
    )

    @Test
    fun every_pair_of_phases_reduces_as_the_decision_record_states() {
        assertEquals(25, expected.size, "the table must cover every pair of phases")
        for (left in MovementPhase.entries) {
            for (right in MovementPhase.entries) {
                assertEquals(
                    expected.getValue(left to right),
                    BilateralPhase.furthestThroughRepetition(left, right),
                    "left " + left + ", right " + right,
                )
            }
        }
    }

    @Test
    fun the_reduction_does_not_depend_on_which_side_is_which() {
        for (a in MovementPhase.entries) {
            for (b in MovementPhase.entries) {
                assertEquals(
                    BilateralPhase.furthestThroughRepetition(a, b),
                    BilateralPhase.furthestThroughRepetition(b, a),
                    a.toString() + " / " + b,
                )
            }
        }
    }

    @Test
    fun idle_is_shown_only_when_both_sides_are_idle() {
        for (left in MovementPhase.entries) {
            for (right in MovementPhase.entries) {
                val shown = BilateralPhase.furthestThroughRepetition(left, right)
                assertEquals(
                    left == IDLE && right == IDLE,
                    shown == IDLE,
                    "an untracked side hid a tracked one: left " + left + ", right " + right,
                )
            }
        }
    }

    // --- Against the real tracker ---------------------------------------------------------

    /**
     * Under SIMULTANEOUS semantics a repetition is counted when the second side
     * returns to the top. The display phase reaches TOP on that same frame, so
     * the readout never shows a finished repetition that has not been counted.
     */
    @Test
    fun a_squat_shows_top_on_the_same_frame_its_repetition_is_counted() {
        val tracker = BilateralRepTracker(ArchetypeProfiles.squat)
        var previousCount = 0
        var previousShown = IDLE
        var countedFrames = 0

        // The right knee trails the left by four frames, as a real squat does.
        for ((index, knees) in squatKnees(reps = 4, rightLagFrames = 4).withIndex()) {
            tracker.onFrame(index * FRAME_MS, legFrame(knees.first, knees.second), policy) { }
            val shown = BilateralPhase.furthestThroughRepetition(tracker.leftPhase, tracker.rightPhase)
            val counted = tracker.repetitionCount > previousCount

            if (counted) {
                countedFrames++
                assertEquals(TOP, shown, "frame " + index + ": counted a repetition while showing " + shown)
            }
            if (previousShown == ASCENDING && shown == TOP) {
                assertTrue(counted, "frame " + index + ": showed the repetition finished before it was counted")
            }
            previousCount = tracker.repetitionCount
            previousShown = shown
        }

        assertEquals(4, countedFrames, "the synthetic set did not produce four counted repetitions")
    }

    /**
     * Under PER_LIMB semantics the resting arm sits at TOP while the other
     * works. The readout shows the working arm.
     */
    @Test
    fun an_alternating_curl_shows_the_working_arm_and_not_the_resting_one() {
        val tracker = BilateralRepTracker(ArchetypeProfiles.bicepCurl)
        var sawLeftWorking = false
        var sawRightWorking = false

        for ((index, elbows) in alternatingElbows(curlsPerArm = 2).withIndex()) {
            tracker.onFrame(index * FRAME_MS, armFrame(elbows.first, elbows.second), policy) { }
            val shown = BilateralPhase.furthestThroughRepetition(tracker.leftPhase, tracker.rightPhase)

            if (tracker.leftPhase.isMidRepetition() && tracker.rightPhase == TOP) {
                sawLeftWorking = true
                assertEquals(tracker.leftPhase, shown, "frame " + index)
            }
            if (tracker.rightPhase.isMidRepetition() && tracker.leftPhase == TOP) {
                sawRightWorking = true
                assertEquals(tracker.rightPhase, shown, "frame " + index)
            }
        }

        assertTrue(sawLeftWorking && sawRightWorking, "the synthetic set never exercised both arms")
    }

    // --- Synthetic frames -----------------------------------------------------------------

    private fun MovementPhase.isMidRepetition(): Boolean =
        this == DESCENDING || this == BOTTOM || this == ASCENDING

    private fun squatKnees(reps: Int, rightLagFrames: Int): List<Pair<Double, Double>> {
        val left = ArrayList<Double>()
        repeat(10) { left += 170.0 }
        repeat(reps) {
            for (step in 1..30) left += 170.0 - 100.0 * step / 30.0
            for (step in 1..30) left += 70.0 + 100.0 * step / 30.0
            repeat(10) { left += 170.0 }
        }
        repeat(rightLagFrames) { left += 170.0 }
        return left.indices.map { i -> left[i] to (if (i < rightLagFrames) 170.0 else left[i - rightLagFrames]) }
    }

    private fun alternatingElbows(curlsPerArm: Int): List<Pair<Double, Double>> {
        val frames = ArrayList<Pair<Double, Double>>()
        repeat(10) { frames += 170.0 to 170.0 }
        repeat(curlsPerArm) {
            for (step in 1..20) frames += (170.0 - 130.0 * step / 20.0) to 170.0
            for (step in 1..20) frames += (40.0 + 130.0 * step / 20.0) to 170.0
            for (step in 1..20) frames += 170.0 to (170.0 - 130.0 * step / 20.0)
            for (step in 1..20) frames += 170.0 to (40.0 + 130.0 * step / 20.0)
        }
        repeat(10) { frames += 170.0 to 170.0 }
        return frames
    }

    private fun legFrame(leftKneeDeg: Double, rightKneeDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 3] = 0.95
        }
        fun leg(hip: LandmarkId, knee: LandmarkId, ankle: LandmarkId, x: Double, deg: Double) {
            put(hip, x, 0.50)
            put(knee, x, 0.70)
            val radians = deg * PI / 180.0
            put(ankle, x + 0.20 * sin(radians), 0.70 - 0.20 * cos(radians))
        }
        leg(LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE, 0.44, leftKneeDeg)
        leg(LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE, 0.56, rightKneeDeg)
        return Landmarks.fromPacked(packed)
    }

    private fun armFrame(leftElbowDeg: Double, rightElbowDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 3] = 0.95
        }
        fun arm(shoulder: LandmarkId, elbow: LandmarkId, wrist: LandmarkId, x: Double, deg: Double) {
            put(shoulder, x, 0.30)
            put(elbow, x, 0.50)
            val radians = deg * PI / 180.0
            put(wrist, x + 0.20 * sin(radians), 0.50 - 0.20 * cos(radians))
        }
        arm(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST, 0.40, leftElbowDeg)
        arm(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST, 0.60, rightElbowDeg)
        return Landmarks.fromPacked(packed)
    }

    private companion object {
        const val FRAME_MS = 1000.0 / 30.0
        val policy = VisibilityPolicy.PROVISIONAL
    }
}
