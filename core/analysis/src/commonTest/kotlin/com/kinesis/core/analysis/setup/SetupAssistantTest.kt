package com.kinesis.core.analysis.setup

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.profile.CameraView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The setup assistant - DECISIONS.md P3-55, corrected by P3-59.
 *
 * Frames here are in the core's own unit (P3-16): x runs 0..1 across the frame and y runs
 * 0..height/width down it, about 1.33 on the phone's portrait frame. P3-55's fixtures kept y in
 * 0..1, which is how its feet check passed while being wrong on every real frame.
 */
class SetupAssistantTest {

    private val squat = ExerciseLibrary.squat // side-on, standing
    private val curl = ExerciseLibrary.bicepCurl // side-on, standing
    private val lateralRaise = ExerciseLibrary.lateralRaise // front-on, seated
    private val sidePlank = ExerciseLibrary.sidePlank // front-on, lying on one side

    /**
     * A standing person on a portrait frame: shoulders at y 0.40, hips at 0.75 (a torso of 0.35
     * frame widths), ankles at 1.25 - well inside a 1.33-tall frame. [halfSpan] is half the
     * horizontal gap between the left and right shoulder: 0.01 side-on, 0.12 front-on.
     */
    private fun standing(
        halfSpan: Double = SIDE_ON,
        noseY: Double = 0.25,
        shoulderY: Double = 0.40,
        hipY: Double = 0.75,
        ankleY: Double = 1.25,
        leftAnkleVisibility: Double = 0.95,
        rightAnkleVisibility: Double = 0.95,
        rightArmVisibility: Double = 0.95,
    ): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double, v: Double = 0.95) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 3] = v
        }
        put(LandmarkId.NOSE, 0.50, noseY)
        put(LandmarkId.LEFT_SHOULDER, 0.50 - halfSpan, shoulderY)
        put(LandmarkId.RIGHT_SHOULDER, 0.50 + halfSpan, shoulderY, rightArmVisibility)
        put(LandmarkId.LEFT_ELBOW, 0.50 - halfSpan, shoulderY + 0.15)
        put(LandmarkId.RIGHT_ELBOW, 0.50 + halfSpan, shoulderY + 0.15, rightArmVisibility)
        put(LandmarkId.LEFT_WRIST, 0.50 - halfSpan, shoulderY + 0.28)
        put(LandmarkId.RIGHT_WRIST, 0.50 + halfSpan, shoulderY + 0.28, rightArmVisibility)
        put(LandmarkId.LEFT_HIP, 0.50 - halfSpan * 0.8, hipY)
        put(LandmarkId.RIGHT_HIP, 0.50 + halfSpan * 0.8, hipY)
        put(LandmarkId.LEFT_KNEE, 0.50 - halfSpan * 0.8, (hipY + ankleY) / 2.0)
        put(LandmarkId.RIGHT_KNEE, 0.50 + halfSpan * 0.8, (hipY + ankleY) / 2.0)
        put(LandmarkId.LEFT_ANKLE, 0.50 - halfSpan * 0.8, ankleY, leftAnkleVisibility)
        put(LandmarkId.RIGHT_ANKLE, 0.50 + halfSpan * 0.8, ankleY, rightAnkleVisibility)
        return Landmarks.fromPacked(packed)
    }

    @Test
    fun nobody_in_shot_blocks_scoring() {
        val verdict = SetupAssistant.evaluate(squat, Landmarks.fromPacked(DoubleArray(Landmarks.PACKED_SIZE)))
        assertEquals(SetupVerdict.NoPerson, verdict)
        assertTrue(verdict.blocksScoring)
    }

    /** The P3-55 unit error: a standing person's ankles are below y = 0.98 on a portrait frame. */
    @Test
    fun a_standing_person_side_on_in_a_portrait_frame_is_ready() {
        val verdict = SetupAssistant.evaluate(squat, standing())
        assertEquals(SetupVerdict.Ready, verdict)
        assertFalse(verdict.blocksScoring)
    }

    @Test
    fun feet_out_of_frame_when_neither_ankle_is_seen_and_it_only_advises() {
        val verdict = SetupAssistant.evaluate(squat, standing(leftAnkleVisibility = 0.0, rightAnkleVisibility = 0.0))
        assertEquals(SetupVerdict.FeetOutOfFrame, verdict)
        assertFalse(verdict.blocksScoring)
        assertTrue(verdict.coachingMessage.contains("feet", ignoreCase = true))
    }

    /** Side-on, the far ankle is often hidden behind the near leg. That is not a cropped frame. */
    @Test
    fun one_unseen_ankle_is_not_feet_out_of_frame() {
        assertEquals(SetupVerdict.Ready, SetupAssistant.evaluate(squat, standing(rightAnkleVisibility = 0.0)))
    }

    /**
     * P3-55 called this "landmarks missing", and blocking on it would have stopped every side-on
     * curl: the far arm is often unseen, and the tracker already measures the near arm alone
     * (P3-2, P3-15).
     */
    @Test
    fun a_side_on_curl_with_its_far_arm_unseen_is_ready() {
        assertEquals(SetupVerdict.Ready, SetupAssistant.evaluate(curl, standing(rightArmVisibility = 0.05)))
    }

    @Test
    fun a_cropped_head_advises_without_blocking() {
        val verdict = SetupAssistant.evaluate(squat, standing(noseY = 0.01))
        assertEquals(SetupVerdict.HeadOutOfFrame, verdict)
        assertFalse(verdict.blocksScoring)
    }

    @Test
    fun too_close_and_too_far_advise_without_blocking() {
        // A torso of 0.75 frame widths, and one of 0.07.
        val close = SetupAssistant.evaluate(squat, standing(shoulderY = 0.10, hipY = 0.85))
        val far = SetupAssistant.evaluate(squat, standing(shoulderY = 0.60, hipY = 0.67, ankleY = 0.85))
        assertEquals(SetupVerdict.TooClose, close)
        assertEquals(SetupVerdict.TooFar, far)
        assertFalse(close.blocksScoring)
        assertFalse(far.blocksScoring)
    }

    /** P3-31's failure: the movement filmed from the wrong side. The one thing that blocks. */
    @Test
    fun a_squat_filmed_front_on_is_the_wrong_view_and_blocks_scoring() {
        val verdict = assertIs<SetupVerdict.WrongView>(SetupAssistant.evaluate(squat, standing(halfSpan = FRONT_ON)))
        assertEquals(CameraView.FRONT_ON, verdict.detectedView)
        assertEquals(CameraView.SIDE_ON, verdict.requiredView)
        assertTrue(verdict.blocksScoring)
        assertTrue(verdict.coachingMessage.contains("side", ignoreCase = true))
        assertTrue(verdict.coachingMessage.contains("paused", ignoreCase = true))
    }

    @Test
    fun a_lateral_raise_filmed_side_on_is_the_wrong_view() {
        val verdict = assertIs<SetupVerdict.WrongView>(SetupAssistant.evaluate(lateralRaise, standing()))
        assertEquals(CameraView.FRONT_ON, verdict.requiredView)
        assertTrue(verdict.coachingMessage.contains("face", ignoreCase = true))
        assertEquals(SetupVerdict.Ready, SetupAssistant.evaluate(lateralRaise, standing(halfSpan = FRONT_ON)))
    }

    /**
     * A side plank is filmed from the front, lying on one side: the shoulders are stacked
     * vertically, so their horizontal gap is near zero and the estimator reads "side-on" however
     * the camera stands. P3-59 does not judge a lying body's front-on view at all.
     */
    @Test
    fun the_side_plank_is_not_judged_by_its_view() {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = 0.95
        }
        put(LandmarkId.LEFT_SHOULDER, 0.25, 0.80)
        put(LandmarkId.RIGHT_SHOULDER, 0.25, 0.88)
        put(LandmarkId.LEFT_HIP, 0.60, 0.82)
        put(LandmarkId.RIGHT_HIP, 0.60, 0.90)
        put(LandmarkId.LEFT_ANKLE, 0.90, 0.86)
        put(LandmarkId.RIGHT_ANKLE, 0.90, 0.94)
        val verdict = SetupAssistant.evaluate(sidePlank, Landmarks.fromPacked(packed))
        assertFalse(verdict is SetupVerdict.WrongView, "a side plank was judged by its view: " + verdict)
        assertFalse(verdict.blocksScoring)
    }

    /**
     * P3-55's `Ready` carried a confidence that changed every frame, and the live screen compared
     * verdicts for equality, so a correctly set-up user recomposed the screen on every frame.
     * Two ready frames must now be equal however their geometry differs.
     */
    @Test
    fun two_ready_frames_give_equal_verdicts_whatever_their_geometry() {
        val a = SetupAssistant.evaluate(squat, standing(halfSpan = 0.005))
        val b = SetupAssistant.evaluate(squat, standing(halfSpan = 0.03))
        assertEquals(a, b)
        assertNotEquals<SetupVerdict>(a, SetupAssistant.evaluate(squat, standing(halfSpan = FRONT_ON)))
    }

    private companion object {
        const val SIDE_ON = 0.01
        const val FRONT_ON = 0.12
    }
}
