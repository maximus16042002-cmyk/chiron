package com.kinesis.core.analysis.setup

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupGateTest {

    private val squat = ExerciseLibrary.squat

    private fun readyFrame(): Landmarks {
        val packed = DoubleArray(Landmarks.STRIDE * LandmarkId.COUNT)
        fun put(id: LandmarkId, x: Double, y: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = 0.95
        }
        put(LandmarkId.NOSE, 0.50, 0.15)
        put(LandmarkId.LEFT_SHOULDER, 0.48, 0.30)
        put(LandmarkId.RIGHT_SHOULDER, 0.52, 0.30)
        put(LandmarkId.LEFT_ELBOW, 0.48, 0.42)
        put(LandmarkId.RIGHT_ELBOW, 0.52, 0.42)
        put(LandmarkId.LEFT_WRIST, 0.48, 0.52)
        put(LandmarkId.RIGHT_WRIST, 0.52, 0.52)
        put(LandmarkId.LEFT_HIP, 0.485, 0.55)
        put(LandmarkId.RIGHT_HIP, 0.515, 0.55)
        put(LandmarkId.LEFT_KNEE, 0.485, 0.72)
        put(LandmarkId.RIGHT_KNEE, 0.515, 0.72)
        put(LandmarkId.LEFT_ANKLE, 0.485, 0.90)
        put(LandmarkId.RIGHT_ANKLE, 0.515, 0.90)
        return Landmarks.fromPacked(packed)
    }

    private fun invalidFrame(): Landmarks {
        return Landmarks.fromPacked(DoubleArray(Landmarks.PACKED_SIZE))
    }

    @Test
    fun gate_requires_ten_consecutive_ready_frames_before_becoming_ready() {
        val gate = SetupGate(squat)
        assertFalse(gate.isReady)

        val ready = readyFrame()
        for (i in 1..9) {
            gate.onFrame(ready)
            assertFalse(gate.isReady, "Should not be ready after $i frames")
        }

        gate.onFrame(ready)
        assertTrue(gate.isReady, "Should be ready on 10th consecutive frame")
        assertTrue(gate.latestVerdict is SetupVerdict.Ready)
    }

    @Test
    fun gate_ignores_momentary_glitch_due_to_hysteresis() {
        val gate = SetupGate(squat)
        val ready = readyFrame()
        val invalid = invalidFrame()

        repeat(10) { gate.onFrame(ready) }
        assertTrue(gate.isReady)

        // 1 invalid frame
        gate.onFrame(invalid)
        assertTrue(gate.isReady, "A single glitch must not drop readiness")

        // 10 more invalid frames (total 11)
        repeat(10) { gate.onFrame(invalid) }
        assertTrue(gate.isReady, "Less than 15 invalid frames must not drop readiness")

        // 4 more invalid frames (total 15)
        repeat(4) { gate.onFrame(invalid) }
        assertFalse(gate.isReady, "15 consecutive invalid frames must drop readiness")
    }

    @Test
    fun reset_clears_readiness_state() {
        val gate = SetupGate(squat)
        repeat(10) { gate.onFrame(readyFrame()) }
        assertTrue(gate.isReady)

        gate.reset()
        assertFalse(gate.isReady)
        assertEquals(SetupVerdict.NoPerson, gate.latestVerdict)
    }
}
