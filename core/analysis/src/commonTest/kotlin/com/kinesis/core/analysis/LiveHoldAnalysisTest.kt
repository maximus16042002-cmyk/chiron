package com.kinesis.core.analysis

import com.kinesis.core.analysis.hold.HoldState
import com.kinesis.core.analysis.live.LiveHoldAnalysis
import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.rep.HoldValidity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The live hold path, end to end on synthetic frames - DECISIONS.md P3-16.
 *
 * The same shape as `LiveRepetitionAnalysisTest`: torso check, validity, a
 * per-side filtered angle, one named reduction, then `HoldAnalyser`. Plank is an
 * archetype and must be ready for Phase B, so it is the profile exercised most.
 */
class LiveHoldAnalysisTest {

    private var clock = 0.0

    private fun LiveHoldAnalysis.feed(frame: Landmarks, frames: Int = 1) {
        repeat(frames) {
            onFrame(clock, frame)
            clock += FRAME_MS
        }
    }

    @Test
    fun before_any_frame_nothing_is_valid_or_held() {
        val analysis = LiveHoldAnalysis(ArchetypeProfiles.plank)
        assertEquals(FrameValidity.INVALID, analysis.frameValidity)
        assertEquals(HoldState.NOT_STARTED, analysis.holdState)
        assertEquals(0.0, analysis.heldMs)
    }

    @Test
    fun a_straight_plank_held_for_five_seconds_is_a_valid_hold_of_about_five_seconds() {
        val analysis = LiveHoldAnalysis(ArchetypeProfiles.plank)
        analysis.feed(plank(hipDeg = 178.0), frames = 150)

        assertEquals(HoldState.HOLDING, analysis.holdState)
        assertEquals(FrameValidity.VALID, analysis.frameValidity)
        assertTrue(analysis.heldMs in 4_800.0..5_000.0, "held " + analysis.heldMs + " ms")

        val record = analysis.finish()
        assertEquals(HoldValidity.VALID, record.validity)
        assertEquals(0, record.breakCount)
    }

    /**
     * Side-on, the far side is usually hidden. The near side still measures the
     * body line, so the hold is still measured - it is not thrown away as unseen.
     */
    @Test
    fun a_plank_seen_from_one_side_only_is_still_measured() {
        val analysis = LiveHoldAnalysis(ArchetypeProfiles.plank)
        analysis.feed(plank(hipDeg = 178.0, farSideVisibility = 0.05), frames = 150)

        assertEquals(FrameValidity.INVALID, analysis.frameValidity, "the far side is required and unseen")
        assertEquals(HoldState.HOLDING, analysis.holdState)
        assertEquals(HoldValidity.VALID, analysis.finish().validity)
    }

    /** Both sides seen, one sagging out of tolerance: not held. */
    @Test
    fun a_hold_is_credited_only_while_every_measured_side_is_within_tolerance() {
        val analysis = LiveHoldAnalysis(ArchetypeProfiles.plank)
        analysis.feed(plank(hipDeg = 178.0), frames = 120)
        // A sag that develops over about a second.
        for (step in 1..28) analysis.feed(plank(hipDeg = 178.0, rightHipDeg = 178.0 - step))
        analysis.feed(plank(hipDeg = 178.0, rightHipDeg = 150.0), frames = 60)

        assertEquals(HoldState.DEVIATING, analysis.holdState)
        assertEquals(1, analysis.finish().breakCount)
    }

    /**
     * The case P3-16 found: a 28-degree sag in a single frame, sustained. The
     * outlier gate used to reject it on every frame for the rest of the set, so
     * the break was never seen. It is now re-admitted after a short consistent
     * run - DECISIONS.md P3-17.
     */
    @Test
    fun a_sudden_sag_that_stays_is_seen_as_a_break_after_a_short_run() {
        val analysis = LiveHoldAnalysis(ArchetypeProfiles.plank)
        analysis.feed(plank(hipDeg = 178.0), frames = 120)
        analysis.feed(plank(hipDeg = 178.0, rightHipDeg = 150.0), frames = 60)

        assertEquals(HoldState.DEVIATING, analysis.holdState)
        assertEquals(1, analysis.finish().breakCount)
    }

    @Test
    fun nobody_in_shot_neither_extends_the_hold_nor_breaks_it() {
        val analysis = LiveHoldAnalysis(ArchetypeProfiles.plank)
        analysis.feed(plank(hipDeg = 178.0), frames = 120)
        val heldBefore = analysis.heldMs

        repeat(30) {
            analysis.onNothingSeen(clock)
            clock += FRAME_MS
        }

        assertEquals(FrameValidity.INVALID, analysis.frameValidity)
        assertEquals(heldBefore, analysis.heldMs, "unseen frames extended the hold")
        val record = analysis.finish()
        assertEquals(0, record.breakCount, "unseen frames counted as a break")
        assertTrue(record.coverage < 1.0)
    }

    @Test
    fun every_hold_profile_in_the_library_can_be_driven_live() {
        for (profile in listOf(ArchetypeProfiles.plank, ExerciseLibrary.wallSit, ExerciseLibrary.sidePlank)) {
            val analysis = LiveHoldAnalysis(profile)
            analysis.feed(plank(hipDeg = 178.0), frames = 5)
            analysis.finish()
        }
    }

    // --- Synthetic frames -----------------------------------------------------------------

    /**
     * A side-on plank: shoulder, hip and knee on each side, with the hip angle
     * set exactly. [rightHipDeg] lets one side sag on its own.
     */
    private fun plank(
        hipDeg: Double,
        rightHipDeg: Double = hipDeg,
        farSideVisibility: Double = 0.95,
    ): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double, v: Double) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = v
        }
        fun side(shoulder: LandmarkId, hip: LandmarkId, knee: LandmarkId, ankle: LandmarkId, deg: Double, v: Double) {
            put(shoulder, 0.20, 0.60, v)
            put(hip, 0.50, 0.60, v)
            // Hip -> shoulder points along -x; rotate by deg for hip -> knee.
            val r = deg * PI / 180.0
            val kx = 0.50 - 0.25 * cos(r)
            val ky = 0.60 + 0.25 * sin(r)
            put(knee, kx, ky, v)
            put(ankle, kx + (kx - 0.50), ky + (ky - 0.60), v)
        }
        side(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE, hipDeg, 0.95)
        side(
            LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE,
            rightHipDeg, farSideVisibility,
        )
        return Landmarks.fromPacked(packed)
    }

    private companion object {
        const val FRAME_MS = 1000.0 / 30.0
    }
}
