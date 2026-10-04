package com.kinesis.core.analysis.setup

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.profile.CameraView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupAssistantTest {

    private val squat = ExerciseLibrary.squat // requiredView = SIDE_ON, standing
    private val lateralRaise = ExerciseLibrary.lateralRaise // requiredView = FRONT_ON, seated

    private fun fullBodyFrame(
        shoulderLeftX: Double = 0.40,
        shoulderRightX: Double = 0.60,
        shoulderY: Double = 0.30,
        hipLeftX: Double = 0.44,
        hipRightX: Double = 0.56,
        hipY: Double = 0.55,
        noseY: Double = 0.15,
        ankleY: Double = 0.90,
        visibility: Double = 0.95,
    ): Landmarks {
        val packed = DoubleArray(Landmarks.STRIDE * LandmarkId.COUNT)
        fun put(id: LandmarkId, x: Double, y: Double, v: Double = visibility) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = v
        }
        put(LandmarkId.NOSE, (shoulderLeftX + shoulderRightX) * 0.5, noseY)
        put(LandmarkId.LEFT_SHOULDER, shoulderLeftX, shoulderY)
        put(LandmarkId.RIGHT_SHOULDER, shoulderRightX, shoulderY)
        put(LandmarkId.LEFT_ELBOW, shoulderLeftX - 0.08, shoulderY + 0.12)
        put(LandmarkId.RIGHT_ELBOW, shoulderRightX + 0.08, shoulderY + 0.12)
        put(LandmarkId.LEFT_WRIST, shoulderLeftX - 0.10, shoulderY + 0.22)
        put(LandmarkId.RIGHT_WRIST, shoulderRightX + 0.10, shoulderY + 0.22)
        put(LandmarkId.LEFT_HIP, hipLeftX, hipY)
        put(LandmarkId.RIGHT_HIP, hipRightX, hipY)
        put(LandmarkId.LEFT_KNEE, hipLeftX, (hipY + ankleY) * 0.5)
        put(LandmarkId.RIGHT_KNEE, hipRightX, (hipY + ankleY) * 0.5)
        put(LandmarkId.LEFT_ANKLE, hipLeftX, ankleY)
        put(LandmarkId.RIGHT_ANKLE, hipRightX, ankleY)
        return Landmarks.fromPacked(packed)
    }

    @Test
    fun no_person_when_landmarks_are_empty_or_zero_visibility() {
        val empty = Landmarks.fromPacked(DoubleArray(Landmarks.PACKED_SIZE))
        val verdict = SetupAssistant.evaluate(squat, empty)
        assertEquals(SetupVerdict.NoPerson, verdict)
        assertFalse(verdict.isScoringPermitted)
    }

    @Test
    fun head_out_of_frame_when_nose_is_cropped_at_top() {
        val croppedHead = fullBodyFrame(noseY = 0.01) // < 0.03 margin
        val verdict = SetupAssistant.evaluate(squat, croppedHead)
        assertEquals(SetupVerdict.HeadOutOfFrame, verdict)
        assertFalse(verdict.isScoringPermitted)
        assertTrue(verdict.coachingMessage.contains("head", ignoreCase = true))
    }

    @Test
    fun feet_out_of_frame_for_standing_movement_when_ankles_at_bottom_edge() {
        val croppedFeet = fullBodyFrame(ankleY = 0.99) // > 0.98 margin
        val verdict = SetupAssistant.evaluate(squat, croppedFeet)
        assertEquals(SetupVerdict.FeetOutOfFrame, verdict)
        assertFalse(verdict.isScoringPermitted)
        assertTrue(verdict.coachingMessage.contains("feet", ignoreCase = true))
    }

    @Test
    fun too_close_when_torso_scale_exceeds_maximum() {
        // Torso scale from y=0.10 to y=0.85 is 0.75 > 0.68
        val closeFrame = fullBodyFrame(shoulderY = 0.10, hipY = 0.85, ankleY = 0.95)
        val verdict = SetupAssistant.evaluate(squat, closeFrame)
        assertEquals(SetupVerdict.TooClose, verdict)
        assertFalse(verdict.isScoringPermitted)
    }

    @Test
    fun too_far_when_torso_scale_below_minimum() {
        // Torso scale from y=0.45 to y=0.52 is 0.07 < 0.12
        val farFrame = fullBodyFrame(shoulderY = 0.45, hipY = 0.52, ankleY = 0.65)
        val verdict = SetupAssistant.evaluate(squat, farFrame)
        assertEquals(SetupVerdict.TooFar, verdict)
        assertFalse(verdict.isScoringPermitted)
    }

    @Test
    fun squat_facing_front_emits_wrong_view_requiring_side_on() {
        // Front-on frame: shoulders span 0.20 on 0.25 torso -> ratio = 0.80 (FRONT_ON)
        val frontFrame = fullBodyFrame(shoulderLeftX = 0.40, shoulderRightX = 0.60)
        val verdict = SetupAssistant.evaluate(squat, frontFrame)
        assertTrue(verdict is SetupVerdict.WrongView)
        assertEquals(CameraView.FRONT_ON, verdict.detectedView)
        assertEquals(CameraView.SIDE_ON, verdict.requiredView)
        assertFalse(verdict.isScoringPermitted)
        assertTrue(verdict.coachingMessage.contains("side", ignoreCase = true))
    }

    @Test
    fun squat_side_on_emits_ready() {
        // Side-on frame: shoulders span 0.04 on 0.25 torso -> ratio = 0.16 (SIDE_ON)
        val sideFrame = fullBodyFrame(
            shoulderLeftX = 0.48, shoulderRightX = 0.52,
            hipLeftX = 0.485, hipRightX = 0.515,
        )
        val verdict = SetupAssistant.evaluate(squat, sideFrame)
        assertTrue(verdict is SetupVerdict.Ready)
        assertEquals(CameraView.SIDE_ON, verdict.detectedView)
        assertTrue(verdict.isScoringPermitted)
    }

    @Test
    fun lateral_raise_side_on_emits_wrong_view_requiring_front_on() {
        val sideFrame = fullBodyFrame(
            shoulderLeftX = 0.48, shoulderRightX = 0.52,
            hipLeftX = 0.485, hipRightX = 0.515,
        )
        val verdict = SetupAssistant.evaluate(lateralRaise, sideFrame)
        assertTrue(verdict is SetupVerdict.WrongView)
        assertEquals(CameraView.SIDE_ON, verdict.detectedView)
        assertEquals(CameraView.FRONT_ON, verdict.requiredView)
        assertFalse(verdict.isScoringPermitted)
        assertTrue(verdict.coachingMessage.contains("front", ignoreCase = true))
    }

    @Test
    fun lateral_raise_front_on_emits_ready() {
        val frontFrame = fullBodyFrame(shoulderLeftX = 0.40, shoulderRightX = 0.60)
        val verdict = SetupAssistant.evaluate(lateralRaise, frontFrame)
        assertTrue(verdict is SetupVerdict.Ready)
        assertEquals(CameraView.FRONT_ON, verdict.detectedView)
        assertTrue(verdict.isScoringPermitted)
    }
}
