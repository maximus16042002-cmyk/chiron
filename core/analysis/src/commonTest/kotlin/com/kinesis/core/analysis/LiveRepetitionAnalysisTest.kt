package com.kinesis.core.analysis

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live real-time path, end to end on synthetic frames - DECISIONS.md P3-15.
 *
 * Covers what the orchestrator itself decides: the frame's validity, what the
 * readout is told, and the two choices P3-15 records - that the degenerate-torso
 * check is part of validity, and that an invalid frame still reaches the tracker
 * so one occluded side cannot abandon the other.
 */
class LiveRepetitionAnalysisTest {

    private var clock = 0.0

    private fun LiveRepetitionAnalysis.feed(frame: Landmarks) {
        onFrame(clock, frame)
        clock += FRAME_MS
    }

    @Test
    fun before_any_frame_nothing_is_valid_counted_or_in_progress() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat)
        assertEquals(FrameValidity.INVALID, analysis.frameValidity)
        assertEquals(0, analysis.repCount)
        assertEquals(MovementPhase.IDLE, analysis.displayPhase)
        assertNull(analysis.lastRepValidity)
    }

    @Test
    fun a_clean_squat_set_is_counted_from_valid_frames() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat)
        squat(analysis, reps = 3)

        assertEquals(3, analysis.repCount)
        assertEquals(FrameValidity.VALID, analysis.frameValidity)
        assertEquals(RepValidity.VALID, analysis.lastRepValidity)
        assertEquals(MovementPhase.TOP, analysis.displayPhase)
    }

    @Test
    fun validity_is_judged_against_the_profiles_required_landmarks() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat)

        analysis.feed(body(visibilityOf = mapOf(LandmarkId.RIGHT_ANKLE to 0.55)))
        assertEquals(FrameValidity.DEGRADED, analysis.frameValidity)

        analysis.feed(body(visibilityOf = mapOf(LandmarkId.RIGHT_ANKLE to 0.10)))
        assertEquals(FrameValidity.INVALID, analysis.frameValidity)

        // The wrists are not a squat's concern.
        analysis.feed(body(visibilityOf = mapOf(LandmarkId.LEFT_WRIST to 0.0)))
        assertEquals(FrameValidity.VALID, analysis.frameValidity)
    }

    /**
     * A collapsed estimate can report every landmark as clearly visible while
     * placing them all on one point. Visibility alone would call that VALID; the
     * normaliser's torso check is what catches it.
     */
    @Test
    fun a_collapsed_torso_is_invalid_however_visible_its_landmarks_claim_to_be() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat)
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        for (index in 0 until LandmarkId.COUNT) {
            packed[index * Landmarks.STRIDE] = 0.5
            packed[index * Landmarks.STRIDE + 1] = 0.5
            packed[index * Landmarks.STRIDE + 3] = 0.99
        }
        analysis.feed(Landmarks.fromPacked(packed))
        assertEquals(FrameValidity.INVALID, analysis.frameValidity)
    }

    /**
     * Why an INVALID frame still reaches the tracker. The right wrist is hidden
     * for the whole set, so every frame is INVALID for a bicep curl - and the
     * left arm's curls are still measured and counted, because the tracker
     * gates each side on its own landmarks. Withholding the frame would have
     * thrown the left arm's work away with the right arm's absence.
     */
    @Test
    fun an_occluded_right_arm_does_not_stop_the_left_arm_being_counted() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.bicepCurl)
        val hidden = mapOf(LandmarkId.RIGHT_WRIST to 0.05)

        repeat(10) { analysis.feed(body(visibilityOf = hidden)) }
        repeat(3) {
            for (step in 1..20) analysis.feed(body(leftElbow = 170.0 - 130.0 * step / 20.0, visibilityOf = hidden))
            for (step in 1..20) analysis.feed(body(leftElbow = 40.0 + 130.0 * step / 20.0, visibilityOf = hidden))
        }
        repeat(10) { analysis.feed(body(visibilityOf = hidden)) }

        assertEquals(FrameValidity.INVALID, analysis.frameValidity)
        assertEquals(3, analysis.repCount, "the measured left arm's curls were discarded")
    }

    @Test
    fun stepping_out_of_shot_mid_repetition_ends_it_as_insufficient_data() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat)
        squat(analysis, reps = 1)
        for (step in 1..15) analysis.feed(body(knees = 170.0 - 100.0 * step / 30.0))
        assertEquals(MovementPhase.DESCENDING, analysis.displayPhase)

        repeat(5) {
            analysis.onNothingSeen(clock)
            clock += FRAME_MS
        }

        assertEquals(FrameValidity.INVALID, analysis.frameValidity)
        assertEquals(RepValidity.INSUFFICIENT_DATA, analysis.lastRepValidity)
        assertEquals(1, analysis.repCount, "a repetition nobody saw finish was counted")
        assertEquals(MovementPhase.IDLE, analysis.displayPhase)
    }

    @Test
    fun the_last_repetition_validity_follows_the_most_recent_record() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat)
        repeat(10) { analysis.feed(body()) }

        // Down to 130 degrees and back up: never reached depth.
        for (step in 1..15) analysis.feed(body(knees = 170.0 - 40.0 * step / 15.0))
        for (step in 1..15) analysis.feed(body(knees = 130.0 + 40.0 * step / 15.0))
        repeat(10) { analysis.feed(body()) }
        assertEquals(RepValidity.ABORTED, analysis.lastRepValidity)
        assertEquals(0, analysis.repCount)

        squat(analysis, reps = 1)
        assertEquals(RepValidity.VALID, analysis.lastRepValidity)
        assertEquals(1, analysis.repCount)
    }

    /**
     * A set ends while a repetition is under way. The finished ones and the
     * unfinished one all reach the recorder, the last marked INCOMPLETE rather
     * than dropped or counted - DECISIONS.md P3-16.
     */
    @Test
    fun finishing_mid_repetition_records_every_repetition_and_an_incomplete_one() {
        val records = ArrayList<RepRecord>()
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat, onRepetition = { records += it })
        squat(analysis, reps = 2)
        for (step in 1..15) analysis.feed(body(knees = 170.0 - 100.0 * step / 30.0))

        analysis.finish(clock)

        assertEquals(3, records.size)
        assertEquals(listOf(RepValidity.VALID, RepValidity.VALID, RepValidity.INCOMPLETE), records.map { it.validity })
        assertEquals(2, analysis.repCount, "the unfinished repetition was counted")
        assertEquals(RepValidity.INCOMPLETE, analysis.lastRepValidity)
    }

    @Test
    fun finishing_between_repetitions_adds_nothing() {
        val records = ArrayList<RepRecord>()
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat, onRepetition = { records += it })
        squat(analysis, reps = 2)

        analysis.finish(clock)

        assertEquals(2, records.size)
    }

    /**
     * The raw angle is the measurement before any filtering - what the outlier
     * gate judges, and what the Phase B readout reports. DECISIONS.md P3-17.
     */
    @Test
    fun the_raw_angle_is_this_frames_measurement_before_filtering() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat)
        repeat(10) { analysis.feed(body(knees = 170.0)) }
        analysis.feed(body(knees = 140.0))

        assertEquals(140.0, analysis.rawAngleLeft, 1e-9)
        assertEquals(140.0, analysis.rawAngleRight, 1e-9)

        analysis.onNothingSeen(clock)
        assertTrue(analysis.rawAngleLeft.isNaN() && analysis.rawAngleRight.isNaN())
    }

    // --- Synthetic frames -----------------------------------------------------------------

    private fun squat(analysis: LiveRepetitionAnalysis, reps: Int) {
        repeat(10) { analysis.feed(body()) }
        repeat(reps) {
            for (step in 1..30) analysis.feed(body(knees = 170.0 - 100.0 * step / 30.0))
            for (step in 1..30) analysis.feed(body(knees = 70.0 + 100.0 * step / 30.0))
            repeat(10) { analysis.feed(body()) }
        }
    }

    /**
     * A whole standing figure with a real torso, so normalisation succeeds.
     * [knees] sets both knee angles; [leftElbow] and [rightElbow] the elbows.
     */
    private fun body(
        knees: Double = 170.0,
        leftElbow: Double = 170.0,
        rightElbow: Double = 170.0,
        visibilityOf: Map<LandmarkId, Double> = emptyMap(),
    ): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 3] = visibilityOf[id] ?: 0.95
        }
        fun chain(a: LandmarkId, b: LandmarkId, c: LandmarkId, x: Double, ay: Double, by: Double, deg: Double) {
            put(a, x, ay)
            put(b, x, by)
            val radians = deg * PI / 180.0
            put(c, x + 0.15 * sin(radians), by - 0.15 * cos(radians))
        }
        chain(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST, 0.42, 0.25, 0.40, leftElbow)
        chain(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST, 0.58, 0.25, 0.40, rightElbow)
        chain(LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE, 0.45, 0.55, 0.72, knees)
        chain(LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE, 0.55, 0.55, 0.72, knees)
        return Landmarks.fromPacked(packed)
    }

    private companion object {
        const val FRAME_MS = 1000.0 / 30.0
    }
}
