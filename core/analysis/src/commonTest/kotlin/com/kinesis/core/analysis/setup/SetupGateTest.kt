package com.kinesis.core.analysis.setup

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The setup gate - DECISIONS.md P3-55, corrected by P3-59.
 *
 * Two debounced outputs: whether scoring is blocked (only a wrong view or nobody in shot blocks
 * it), and the one message the screen shows. Neither moves on a single frame.
 */
class SetupGateTest {

    private val squat = ExerciseLibrary.squat

    /** Side-on ([halfSpan] 0.01) or front-on (0.12), on a portrait frame as in SetupAssistantTest. */
    private fun standing(halfSpan: Double = 0.01, noseY: Double = 0.25): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = 0.95
        }
        put(LandmarkId.NOSE, 0.50, noseY)
        put(LandmarkId.LEFT_SHOULDER, 0.50 - halfSpan, 0.40)
        put(LandmarkId.RIGHT_SHOULDER, 0.50 + halfSpan, 0.40)
        put(LandmarkId.LEFT_HIP, 0.50 - halfSpan, 0.75)
        put(LandmarkId.RIGHT_HIP, 0.50 + halfSpan, 0.75)
        put(LandmarkId.LEFT_KNEE, 0.50 - halfSpan, 1.00)
        put(LandmarkId.RIGHT_KNEE, 0.50 + halfSpan, 1.00)
        put(LandmarkId.LEFT_ANKLE, 0.50 - halfSpan, 1.25)
        put(LandmarkId.RIGHT_ANKLE, 0.50 + halfSpan, 1.25)
        return Landmarks.fromPacked(packed)
    }

    private val sideOn = standing()
    private val frontOn = standing(halfSpan = 0.12)
    private val headCropped = standing(noseY = 0.01)

    @Test
    fun scoring_is_blocked_until_ten_frames_without_a_blocking_reason() {
        val gate = SetupGate(squat)
        assertTrue(gate.isScoringBlocked, "nothing has been seen yet")
        repeat(9) { gate.onFrame(sideOn) }
        assertTrue(gate.isScoringBlocked, "after nine frames")
        gate.onFrame(sideOn)
        assertFalse(gate.isScoringBlocked, "after ten")
    }

    @Test
    fun advice_never_blocks_scoring() {
        val gate = SetupGate(squat)
        repeat(10) { gate.onFrame(headCropped) }
        assertFalse(gate.isScoringBlocked)
        assertEquals(SetupVerdict.HeadOutOfFrame.coachingMessage, gate.message)
    }

    @Test
    fun a_wrong_view_blocks_scoring_only_after_fifteen_frames() {
        val gate = SetupGate(squat)
        repeat(10) { gate.onFrame(sideOn) }
        repeat(14) { gate.onFrame(frontOn) }
        assertFalse(gate.isScoringBlocked, "a turn of under half a second does not block")
        gate.onFrame(frontOn)
        assertTrue(gate.isScoringBlocked)
        repeat(9) { gate.onFrame(sideOn) }
        assertTrue(gate.isScoringBlocked, "turning back takes ten frames to reopen")
        gate.onFrame(sideOn)
        assertFalse(gate.isScoringBlocked)
    }

    @Test
    fun nobody_in_shot_blocks_scoring_after_fifteen_frames() {
        val gate = SetupGate(squat)
        repeat(10) { gate.onFrame(sideOn) }
        repeat(14) { gate.onNothingSeen() }
        assertFalse(gate.isScoringBlocked)
        gate.onNothingSeen()
        assertTrue(gate.isScoringBlocked)
    }

    /** The screen hears a message only once a reason has held for ten frames - no flicker. */
    @Test
    fun the_message_changes_only_after_a_reason_has_held_for_ten_frames() {
        val gate = SetupGate(squat)
        assertEquals(SetupVerdict.NoPerson.coachingMessage, gate.message)
        repeat(9) { gate.onFrame(sideOn) }
        assertEquals(SetupVerdict.NoPerson.coachingMessage, gate.message)
        gate.onFrame(sideOn)
        assertNull(gate.message, "set up correctly: nothing to say")

        // A reason that comes and goes every other frame never reaches the screen.
        repeat(30) { gate.onFrame(if (it % 2 == 0) headCropped else sideOn) }
        assertNull(gate.message)

        repeat(10) { gate.onFrame(frontOn) }
        assertTrue(gate.message!!.contains("paused"))
    }

    @Test
    fun reset_blocks_again_and_says_nobody_is_in_shot() {
        val gate = SetupGate(squat)
        repeat(10) { gate.onFrame(sideOn) }
        gate.reset()
        assertTrue(gate.isScoringBlocked)
        assertEquals(SetupVerdict.NoPerson.coachingMessage, gate.message)
        assertEquals(SetupVerdict.NoPerson, gate.latestVerdict)
    }
}
