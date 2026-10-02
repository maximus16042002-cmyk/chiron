package com.kinesis.core.analysis.rep

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.FormMeasure
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The per-repetition form summary - DECISIONS.md P3-44: each measure from synthetic landmarks,
 * reduced over the repetition's own window, per side, and carried into the record.
 */
class FormTrackerTest {

    private val policy = VisibilityPolicy.PROVISIONAL

    private class Frame {
        val packed = DoubleArray(Landmarks.PACKED_SIZE) { if (it % Landmarks.STRIDE == Landmarks.STRIDE - 1) 0.0 else Double.NaN }
        fun put(id: LandmarkId, x: Double, y: Double, visibility: Double = 0.95) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = visibility
        }
        fun build(): Landmarks = Landmarks.fromPacked(packed)
    }

    private fun rad(deg: Double) = deg * PI / 180.0

    // --- The upper arm against the trunk (the curl) -----------------------------------------------

    /** Trunk vertical, upper arm [upperArmDeg] forward of it, the forearm at [elbowDeg] from the upper arm. */
    private fun armFrame(upperArmDeg: Double, elbowDeg: Double, rightUpperArmDeg: Double = 5.0, rightElbowDeg: Double = 170.0): Landmarks {
        val f = Frame()
        fun arm(shoulder: LandmarkId, hip: LandmarkId, elbow: LandmarkId, wrist: LandmarkId, x: Double, upper: Double, angle: Double) {
            f.put(shoulder, x, 0.30)
            f.put(hip, x, 0.60)
            val ex = x + 0.15 * sin(rad(upper))
            val ey = 0.30 + 0.15 * cos(rad(upper))
            f.put(elbow, ex, ey)
            // Elbow-to-shoulder direction, turned by the elbow angle, gives elbow-to-wrist.
            val ux = -sin(rad(upper))
            val uy = -cos(rad(upper))
            val wx = ux * cos(rad(angle)) - uy * sin(rad(angle))
            val wy = ux * sin(rad(angle)) + uy * cos(rad(angle))
            f.put(wrist, ex + 0.15 * wx, ey + 0.15 * wy)
        }
        arm(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_HIP, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST, 0.40, upperArmDeg, elbowDeg)
        arm(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST, 0.60, rightUpperArmDeg, rightElbowDeg)
        return f.build()
    }

    @Test
    fun the_upper_arm_measure_is_its_largest_angle_to_the_trunk_inside_the_window() {
        val tracker = FormTracker(ExerciseLibrary.bicepCurl, 64)
        for ((i, upper) in listOf(5.0, 15.0, 35.0, 10.0).withIndex()) {
            tracker.push(i * 100.0, armFrame(upper, 120.0), policy, 120.0, 170.0)
        }
        assertEquals(35.0, tracker.summarise(Side.LEFT, 0.0, 300.0).getValue(FormMeasure.UPPER_ARM_TRUNK_MAX), 1e-9)
        assertEquals(10.0, tracker.summarise(Side.LEFT, 250.0, 300.0).getValue(FormMeasure.UPPER_ARM_TRUNK_MAX), 1e-9)
        assertEquals(5.0, tracker.summarise(Side.RIGHT, 0.0, 300.0).getValue(FormMeasure.UPPER_ARM_TRUNK_MAX), 1e-9)
    }

    @Test
    fun a_joint_not_seen_leaves_its_measure_unmeasured_on_that_side_only() {
        val tracker = FormTracker(ExerciseLibrary.bicepCurl, 16)
        val packed = armFrame(30.0, 120.0).packed
        packed[LandmarkId.LEFT_HIP.index * Landmarks.STRIDE + 3] = 0.0
        tracker.push(0.0, Landmarks.fromPacked(packed), policy, 120.0, 170.0)
        assertTrue(tracker.summarise(Side.LEFT, 0.0, 0.0).getValue(FormMeasure.UPPER_ARM_TRUNK_MAX).isNaN())
        assertEquals(5.0, tracker.summarise(Side.RIGHT, 0.0, 0.0).getValue(FormMeasure.UPPER_ARM_TRUNK_MAX), 1e-9)
    }

    private val Landmarks.packed: DoubleArray get() = DoubleArray(Landmarks.PACKED_SIZE).also { out ->
        for (id in LandmarkId.entries) {
            val base = id.index * Landmarks.STRIDE
            out[base] = x(id); out[base + 1] = y(id); out[base + 2] = z(id); out[base + 3] = visibility(id)
        }
    }

    // --- The body line (the push-up) --------------------------------------------------------------

    /** Shoulder and ankle level, 0.6 apart; the hip [drop] below their midpoint (negative: above). */
    private fun plankFrame(drop: Double): Landmarks {
        val f = Frame()
        for ((shoulder, hip, ankle) in listOf(
            Triple(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_HIP, LandmarkId.LEFT_ANKLE),
            Triple(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_ANKLE),
        )) {
            f.put(shoulder, 0.2, 0.5)
            f.put(hip, 0.5, 0.5 + drop)
            f.put(ankle, 0.8, 0.5)
        }
        return f.build()
    }

    private fun bend(drop: Double) = 2.0 * atan(kotlin.math.abs(drop) / 0.3) * 180.0 / PI

    @Test
    fun a_hip_below_the_line_is_a_sag_and_above_it_a_hike_each_its_largest() {
        val tracker = FormTracker(ExerciseLibrary.pushUp, 64)
        for ((i, drop) in listOf(0.05, 0.10, -0.03, 0.0).withIndex()) tracker.push(i * 100.0, plankFrame(drop), policy, 150.0, 150.0)
        val form = tracker.summarise(Side.LEFT, 0.0, 300.0)
        assertEquals(bend(0.10), form.getValue(FormMeasure.BODY_LINE_SAG_MAX), 1e-9)
        assertEquals(bend(0.03), form.getValue(FormMeasure.BODY_LINE_HIKE_MAX), 1e-9)
    }

    @Test
    fun a_hip_that_never_rose_above_the_line_has_a_hike_of_zero_not_unmeasured() {
        val tracker = FormTracker(ExerciseLibrary.pushUp, 16)
        for ((i, drop) in listOf(0.02, 0.04).withIndex()) tracker.push(i * 100.0, plankFrame(drop), policy, 150.0, 150.0)
        val form = tracker.summarise(Side.RIGHT, 0.0, 100.0)
        assertEquals(0.0, form.getValue(FormMeasure.BODY_LINE_HIKE_MAX))
        assertEquals(bend(0.04), form.getValue(FormMeasure.BODY_LINE_SAG_MAX), 1e-9)
    }

    // --- Trunk against shin at the deepest frame (the squat) -------------------------------------

    /** The trunk leaning [trunkDeg] from vertical over the hip, the shin [shinDeg] over the ankle. */
    private fun squatFrame(trunkDeg: Double, shinDeg: Double): Landmarks {
        val f = Frame()
        for ((ids, x) in listOf(
            listOf(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE) to 0.45,
            listOf(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE) to 0.55,
        )) {
            f.put(ids[1], x, 0.55)
            f.put(ids[0], x + 0.3 * sin(rad(trunkDeg)), 0.55 - 0.3 * cos(rad(trunkDeg)))
            f.put(ids[3], x, 0.95)
            f.put(ids[2], x + 0.2 * sin(rad(shinDeg)), 0.95 - 0.2 * cos(rad(shinDeg)))
        }
        return f.build()
    }

    @Test
    fun trunk_minus_shin_is_read_at_the_deepest_frame_of_the_driving_angle() {
        val tracker = FormTracker(ExerciseLibrary.squat, 64)
        // The squat's far zone is the smaller knee angle: the deepest frame is the one at 95.
        val frames = listOf(Triple(170.0, 10.0, 5.0), Triple(120.0, 30.0, 20.0), Triple(95.0, 50.0, 25.0), Triple(130.0, 40.0, 30.0))
        for ((i, f) in frames.withIndex()) tracker.push(i * 100.0, squatFrame(f.second, f.third), policy, f.first, f.first)
        assertEquals(25.0, tracker.summarise(Side.LEFT, 0.0, 300.0).getValue(FormMeasure.TRUNK_MINUS_SHIN_LEAN), 1e-9)
        assertEquals(10.0, tracker.summarise(Side.RIGHT, 200.0, 300.0).getValue(FormMeasure.TRUNK_MINUS_SHIN_LEAN) - 15.0, 1e-9)
    }

    @Test
    fun an_exercise_that_asks_for_nothing_measures_nothing() {
        val tracker = FormTracker(ExerciseLibrary.lateralRaise, 16)
        tracker.push(0.0, armFrame(30.0, 120.0), policy, 60.0, 60.0)
        assertEquals(emptyMap(), tracker.summarise(Side.LEFT, 0.0, 0.0))
    }

    /** P3-44: a variant asks for its parent's measures, through delegation. */
    @Test
    fun the_goblet_squat_asks_for_the_squats_measures() {
        val goblet = ExerciseLibrary.all.first { it.id == "goblet_squat" } as com.kinesis.core.analysis.profile.RepetitionExercise
        assertEquals(ExerciseLibrary.squat.formMeasures, goblet.formMeasures)
    }

    // --- Into the record ------------------------------------------------------------------------

    /** One left-arm curl, the upper arm swinging 30 degrees forward at its top; the right arm still. */
    @Test
    fun a_curl_record_carries_the_working_arms_upper_arm_measure_and_not_the_other() {
        val records = ArrayList<RepRecord>()
        val analysis = LiveRepetitionAnalysis(ExerciseLibrary.bicepCurl, onRepetition = { records += it })
        var t = 0.0
        fun frame(upper: Double, elbow: Double) {
            analysis.onFrame(t, armFrame(upper, elbow))
            t += 33.0
        }
        repeat(20) { frame(5.0, 170.0) }
        for (i in 0..20) frame(5.0 + 25.0 * i / 20.0, 170.0 - 130.0 * i / 20.0) // up: elbow 170 -> 40, arm forward to 30
        for (i in 0..20) frame(30.0 - 25.0 * i / 20.0, 40.0 + 130.0 * i / 20.0) // down
        repeat(20) { frame(5.0, 170.0) }
        analysis.finish(t)

        val rep = records.single { it.validity == RepValidity.VALID }
        assertEquals(Side.LEFT, rep.attributedSide)
        val upper = rep.form.on(FormMeasure.UPPER_ARM_TRUNK_MAX)
        assertEquals(30.0, upper.left, 0.5)
        assertTrue(upper.right.isNaN(), "the resting arm's measure must not be carried on the working arm's record")
    }
}
