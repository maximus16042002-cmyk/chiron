package com.kinesis.core.analysis.kinematics

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepSignalSpec
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The trunk's inclination as a driving signal - DECISIONS.md P3-45: the hip-to-shoulder line's
 * angle above horizontal in the image, per side, the same whichever way the subject faces.
 */
class TrunkInclinationTest {

    private val policy = VisibilityPolicy.PROVISIONAL

    /** Each side's trunk at its own inclination; [facingLeft] puts the shoulder left of the hip. */
    private fun trunkFrame(leftDeg: Double, rightDeg: Double, facingLeft: Boolean = false, hideLeftHip: Boolean = false): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE) { if (it % Landmarks.STRIDE == Landmarks.STRIDE - 1) 0.0 else Double.NaN }
        fun put(id: LandmarkId, x: Double, y: Double, visibility: Double = 0.95) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x; packed[base + 1] = y; packed[base + 2] = 0.0; packed[base + 3] = visibility
        }
        val direction = if (facingLeft) -1.0 else 1.0
        fun trunk(hip: LandmarkId, shoulder: LandmarkId, x: Double, deg: Double, hipVisibility: Double) {
            put(hip, x, 0.70, hipVisibility)
            val r = deg * PI / 180.0
            put(shoulder, x + direction * 0.3 * cos(r), 0.70 - 0.3 * sin(r))
        }
        trunk(LandmarkId.LEFT_HIP, LandmarkId.LEFT_SHOULDER, 0.45, leftDeg, if (hideLeftHip) 0.0 else 0.95)
        trunk(LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_SHOULDER, 0.55, rightDeg, 0.95)
        return Landmarks.fromPacked(packed)
    }

    private fun inclination(frame: Landmarks) = JointAngles.compute(frame, SegmentInclination.TRUNK, MeasurementPlane.IMAGE_2D, policy)

    @Test
    fun lying_flat_is_zero_upright_is_ninety_and_between_is_the_angle() {
        assertEquals(0.0, inclination(trunkFrame(0.0, 0.0)).left, 1e-9)
        assertEquals(90.0, inclination(trunkFrame(90.0, 90.0)).right, 1e-9)
        val tilted = inclination(trunkFrame(30.0, 62.5))
        assertEquals(30.0, tilted.left, 1e-9)
        assertEquals(62.5, tilted.right, 1e-9)
    }

    @Test
    fun the_subject_facing_either_way_reads_the_same() {
        assertEquals(inclination(trunkFrame(40.0, 40.0)).left, inclination(trunkFrame(40.0, 40.0, facingLeft = true)).left, 1e-9)
    }

    @Test
    fun a_shoulder_below_the_hip_reads_negative() {
        assertEquals(-10.0, inclination(trunkFrame(-10.0, 5.0)).left, 1e-9)
    }

    /** CLAUDE.md section 3: an unseen hip on one side leaves only that side unmeasured. */
    @Test
    fun an_unseen_landmark_leaves_only_its_own_side_unmeasured() {
        val signal = inclination(trunkFrame(30.0, 30.0, hideLeftHip = true))
        assertTrue(signal.left.isNaN())
        assertEquals(30.0, signal.right, 1e-9)
    }

    /** The engine counts on it as on any joint angle: start lying back, far sitting up. */
    @Test
    fun a_trunk_driven_profile_counts_a_sit_up() {
        val situp = ExerciseLibrary.situp
        val trunkSitUp = object : RepetitionExercise by situp {
            override val repSignal: RepSignalSpec = RepSignalSpec(
                joint = SegmentInclination.TRUNK,
                plane = MeasurementPlane.IMAGE_2D,
                topThreshold = 25.0,
                bottomThreshold = 60.0,
                hysteresis = 8.0,
                minRepDurationMs = 500.0,
                maxRepDurationMs = 12_000.0,
            )
        }
        val records = ArrayList<RepRecord>()
        val analysis = LiveRepetitionAnalysis(trunkSitUp, onRepetition = { records += it })
        var t = 0.0
        fun frame(deg: Double) {
            analysis.onFrame(t, trunkFrame(deg, deg))
            t += 33.0
        }
        repeat(15) { frame(8.0) }
        for (i in 0..24) frame(8.0 + 67.0 * i / 24.0) // up to 75
        for (i in 0..24) frame(75.0 - 67.0 * i / 24.0) // back down
        repeat(15) { frame(8.0) }
        analysis.finish(t)
        assertEquals(1, records.count { it.validity == RepValidity.VALID })
        assertEquals(1, analysis.repCount)
    }
}
