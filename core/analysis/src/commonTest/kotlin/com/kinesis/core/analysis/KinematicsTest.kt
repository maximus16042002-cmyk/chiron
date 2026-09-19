package com.kinesis.core.analysis

import com.kinesis.core.analysis.geometry.Geometry
import com.kinesis.core.analysis.kinematics.BodyPoint
import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.JointAngles
import com.kinesis.core.analysis.kinematics.LandmarkVelocity
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.analysis.kinematics.bothSidesMeasured
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Geometry primitives and current-frame kinematics.
 *
 * The angles here are checked against constructions whose answer is known by
 * hand - a right angle, a straight limb, an equilateral triangle - rather than
 * against whatever the code produced when it was written. A kinematics test that
 * records the implementation's own output tests nothing.
 *
 * Runs on JVM and JS.
 */
class KinematicsTest {

    private val policy = VisibilityPolicy.PROVISIONAL

    // --- Geometry -------------------------------------------------------------

    @Test
    fun a_right_angle_measures_ninety_degrees() {
        val angle = Geometry.angleAtVertex2dDegrees(
            ax = 0.0, ay = 1.0,
            bx = 0.0, by = 0.0,
            cx = 1.0, cy = 0.0,
        )
        assertEquals(90.0, angle, absoluteTolerance = 1e-12)
    }

    @Test
    fun a_straight_limb_measures_one_hundred_and_eighty_degrees() {
        val angle = Geometry.angleAtVertex2dDegrees(
            ax = -1.0, ay = 0.0,
            bx = 0.0, by = 0.0,
            cx = 1.0, cy = 0.0,
        )
        assertEquals(180.0, angle, absoluteTolerance = 1e-9)
    }

    @Test
    fun a_fully_folded_limb_measures_zero_degrees() {
        val angle = Geometry.angleAtVertex2dDegrees(
            ax = 1.0, ay = 0.0,
            bx = 0.0, by = 0.0,
            cx = 2.0, cy = 0.0,
        )
        assertEquals(0.0, angle, absoluteTolerance = 1e-9)
    }

    @Test
    fun an_equilateral_triangle_measures_sixty_degrees() {
        // Third vertex of an equilateral triangle on the unit base.
        val angle = Geometry.angleAtVertex2dDegrees(
            ax = 1.0, ay = 0.0,
            bx = 0.0, by = 0.0,
            cx = 0.5, cy = 0.8660254037844386,
        )
        assertEquals(60.0, angle, absoluteTolerance = 1e-9)
    }

    @Test
    fun the_three_dimensional_form_agrees_with_the_planar_one_when_depth_is_flat() {
        val planar = Geometry.angleAtVertex2dDegrees(0.0, 1.0, 0.0, 0.0, 1.0, 0.0)
        val spatial = Geometry.angleAtVertex3dDegrees(
            0.0, 1.0, 0.0,
            0.0, 0.0, 0.0,
            1.0, 0.0, 0.0,
        )
        assertEquals(planar, spatial, absoluteTolerance = 1e-12)
    }

    /**
     * The clamp exists for this case. For nearly collinear points the cosine can
     * land a few ulps outside [-1, 1], and an unclamped `acos` returns NaN for
     * geometry that is perfectly well defined.
     */
    @Test
    fun a_nearly_collinear_limb_does_not_fall_out_of_the_domain_of_acos() {
        val angle = Geometry.angleAtVertex2dDegrees(
            ax = -1.0, ay = 0.0,
            bx = 0.0, by = 0.0,
            cx = 1.0, cy = 1e-18,
        )
        assertTrue(Geometry.isMeasured(angle), "a nearly straight limb produced " + angle)
        assertEquals(180.0, angle, absoluteTolerance = 1e-6)
    }

    @Test
    fun coincident_points_report_no_angle_rather_than_zero_or_one_eighty() {
        // The failure the prototype warned about: 0 and 180 are real angles, so
        // returning either as a failure signal is indistinguishable from success.
        val angle = Geometry.angleAtVertex2dDegrees(
            ax = 0.5, ay = 0.5,
            bx = 0.5, by = 0.5,
            cx = 1.0, cy = 0.0,
        )
        assertFalse(Geometry.isMeasured(angle))
    }

    @Test
    fun non_finite_input_reports_no_angle() {
        assertFalse(
            Geometry.isMeasured(
                Geometry.angleAtVertex2dDegrees(Double.NaN, 1.0, 0.0, 0.0, 1.0, 0.0),
            ),
        )
        assertFalse(
            Geometry.isMeasured(
                Geometry.angleAtVertex3dDegrees(
                    0.0, 1.0, 0.0,
                    0.0, 0.0, Double.POSITIVE_INFINITY,
                    1.0, 0.0, 0.0,
                ),
            ),
        )
    }

    @Test
    fun distance_is_euclidean_and_reports_non_finite_input() {
        assertEquals(5.0, Geometry.distance2d(0.0, 0.0, 3.0, 4.0), absoluteTolerance = 1e-12)
        assertEquals(
            13.0,
            Geometry.distance3d(0.0, 0.0, 0.0, 3.0, 4.0, 12.0),
            absoluteTolerance = 1e-12,
        )
        assertFalse(Geometry.isMeasured(Geometry.distance2d(Double.NaN, 0.0, 1.0, 1.0)))
    }

    // --- Joint angles are bilateral -------------------------------------------

    @Test
    fun a_joint_angle_comes_back_with_both_sides_separate() {
        // Left elbow bent to a right angle, right elbow straight. Asymmetric on
        // purpose: a symmetric pose cannot detect the sides being crossed.
        val frame = armsFrame(leftElbowBent = true, rightElbowBent = false)
        val elbow = JointAngles.compute(frame, JointAngleId.ELBOW, MeasurementPlane.IMAGE_2D, policy)

        assertEquals(90.0, elbow.left, absoluteTolerance = 1e-9)
        assertEquals(180.0, elbow.right, absoluteTolerance = 1e-9)
        assertTrue(elbow.bothSidesMeasured())
    }

    @Test
    fun the_sides_are_not_crossed() {
        val bentLeft = JointAngles.compute(
            armsFrame(leftElbowBent = true, rightElbowBent = false),
            JointAngleId.ELBOW,
            MeasurementPlane.IMAGE_2D,
            policy,
        )
        val bentRight = JointAngles.compute(
            armsFrame(leftElbowBent = false, rightElbowBent = true),
            JointAngleId.ELBOW,
            MeasurementPlane.IMAGE_2D,
            policy,
        )
        assertEquals(bentLeft.left, bentRight.right, absoluteTolerance = 1e-9)
        assertEquals(bentLeft.right, bentRight.left, absoluteTolerance = 1e-9)
    }

    /**
     * The defect this whole design exists to prevent. In the old prototype an
     * occluded side was folded into the other by `left ?: right`, so a one-armed
     * measurement was indistinguishable from a two-armed one. Here the occluded
     * side is unmeasured and says so, and the visible side keeps its real value.
     */
    @Test
    fun an_occluded_side_is_unmeasured_and_does_not_borrow_the_other_side() {
        val frame = armsFrame(
            leftElbowBent = true,
            rightElbowBent = false,
            rightVisibility = 0.05,
        )
        val elbow = JointAngles.compute(frame, JointAngleId.ELBOW, MeasurementPlane.IMAGE_2D, policy)

        assertEquals(90.0, elbow.left, absoluteTolerance = 1e-9)
        assertFalse(Geometry.isMeasured(elbow.right), "right was " + elbow.right)
        assertFalse(elbow.bothSidesMeasured())

        // And the absence propagates rather than being absorbed: any reduction
        // over an unmeasured side is itself unmeasured.
        assertFalse(Geometry.isMeasured(elbow.meanExplicit()))
        assertFalse(Geometry.isMeasured(elbow.asymmetry()))
    }

    @Test
    fun every_joint_definition_names_the_vertex_between_its_two_rays() {
        // A joint whose vertex is also one of its rays would always measure zero.
        for (joint in JointAngleId.entries) {
            assertTrue(
                joint.vertex != joint.proximal && joint.vertex != joint.distal,
                joint.name + " has its vertex as one of its own rays",
            )
        }
    }

    @Test
    fun every_body_point_pairs_two_different_sides() {
        for (point in BodyPoint.entries) {
            assertTrue(
                point.leftId != point.rightId,
                point.name + " maps both sides to the same landmark",
            )
            assertEquals(
                point.rightId,
                point.leftId.mirrored,
                point.name + " is not a mirrored pair",
            )
        }
    }

    // --- Velocity --------------------------------------------------------------

    @Test
    fun velocity_is_displacement_over_the_measured_interval() {
        val first = wristFrame(leftX = 0.30, rightX = 0.70)
        val second = wristFrame(leftX = 0.40, rightX = 0.70)

        val velocity = LandmarkVelocity.compute(
            previous = first,
            previousTimestampMs = 1000.0,
            current = second,
            currentTimestampMs = 1500.0,
            point = BodyPoint.WRIST,
            plane = MeasurementPlane.IMAGE_2D,
            policy = policy,
        )

        // 0.10 normalised units over 0.5 s.
        assertEquals(0.2, velocity.left, absoluteTolerance = 1e-12)
        assertEquals(0.0, velocity.right, absoluteTolerance = 1e-12)
    }

    @Test
    fun a_repeated_or_reversed_timestamp_yields_no_velocity_rather_than_a_spike() {
        val first = wristFrame(leftX = 0.30, rightX = 0.70)
        val second = wristFrame(leftX = 0.40, rightX = 0.70)

        for (secondTimestamp in listOf(1000.0, 999.0)) {
            val velocity = LandmarkVelocity.compute(
                first, 1000.0, second, secondTimestamp,
                BodyPoint.WRIST, MeasurementPlane.IMAGE_2D, policy,
            )
            assertFalse(Geometry.isMeasured(velocity.left))
            assertFalse(Geometry.isMeasured(velocity.right))
        }
    }

    // --- Frame builders --------------------------------------------------------

    private fun armsFrame(
        leftElbowBent: Boolean,
        rightElbowBent: Boolean,
        rightVisibility: Double = 0.95,
    ): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double, visibility: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = visibility
        }

        // Left arm: shoulder above elbow. Bent puts the wrist out sideways (90
        // degrees at the elbow); straight puts it directly below (180).
        put(LandmarkId.LEFT_SHOULDER, 0.40, 0.30, 0.95)
        put(LandmarkId.LEFT_ELBOW, 0.40, 0.50, 0.95)
        if (leftElbowBent) {
            put(LandmarkId.LEFT_WRIST, 0.20, 0.50, 0.95)
        } else {
            put(LandmarkId.LEFT_WRIST, 0.40, 0.70, 0.95)
        }

        put(LandmarkId.RIGHT_SHOULDER, 0.60, 0.30, rightVisibility)
        put(LandmarkId.RIGHT_ELBOW, 0.60, 0.50, rightVisibility)
        if (rightElbowBent) {
            put(LandmarkId.RIGHT_WRIST, 0.80, 0.50, rightVisibility)
        } else {
            put(LandmarkId.RIGHT_WRIST, 0.60, 0.70, rightVisibility)
        }

        return Landmarks.fromPacked(packed)
    }

    private fun wristFrame(leftX: Double, rightX: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = 0.50
            packed[base + 2] = 0.0
            packed[base + 3] = 0.95
        }
        put(LandmarkId.LEFT_WRIST, leftX)
        put(LandmarkId.RIGHT_WRIST, rightX)
        return Landmarks.fromPacked(packed)
    }
}
