package com.kinesis.core.analysis

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.live.SetupEnforcement
import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.analysis.temporal.TemporalConfig
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

        analysis.feed(body(visibilityOf = mapOf(LandmarkId.RIGHT_HIP to 0.55)))
        assertEquals(FrameValidity.DEGRADED, analysis.frameValidity)

        analysis.feed(body(visibilityOf = mapOf(LandmarkId.RIGHT_HIP to 0.10)))
        assertEquals(FrameValidity.INVALID, analysis.frameValidity)

        // The wrists are not a squat's concern.
        analysis.feed(body(visibilityOf = mapOf(LandmarkId.LEFT_WRIST to 0.0)))
        assertEquals(FrameValidity.VALID, analysis.frameValidity)

        // DECISIONS.md P3-68: nor, since the squat counts its near knee, are the far knee and
        // ankle a side-on camera often cannot see.
        analysis.feed(body(visibilityOf = mapOf(LandmarkId.RIGHT_KNEE to 0.0, LandmarkId.RIGHT_ANKLE to 0.0)))
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

        // Out of shot for longer than a repetition may be bridged (DECISIONS.md
        // P3-23). It was five frames, which the bridge now crosses; leaving the
        // shot is still what ends a repetition.
        val framesAway = (TemporalConfig.PROVISIONAL.maxBridgedGapMs / FRAME_MS).toInt() + 2
        repeat(framesAway) {
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
     * than dropped or counted - DECISIONS.md P3-16. On [PairedKneeFixture], the paired path.
     */
    @Test
    fun finishing_mid_repetition_records_every_repetition_and_an_incomplete_one() {
        val records = ArrayList<RepRecord>()
        val analysis = LiveRepetitionAnalysis(PairedKneeFixture.instance, onRepetition = { records += it })
        squat(analysis, reps = 2)
        for (step in 1..15) analysis.feed(body(knees = 170.0 - 100.0 * step / 30.0))

        analysis.finish(clock)

        assertEquals(3, records.size)
        assertEquals(listOf(RepValidity.VALID, RepValidity.VALID, RepValidity.INCOMPLETE), records.map { it.validity })
        assertEquals(2, analysis.repCount, "the unfinished repetition was counted")
        assertEquals(RepValidity.INCOMPLETE, analysis.lastRepValidity)
    }

    /**
     * The near-side squat (DECISIONS.md P3-68) counts each squat once, from whichever knee
     * completes it. An unfinished squat blocks nothing (P3-29), so each knee still under way
     * reports its own INCOMPLETE record, as the push-up's do: two records, never a count.
     */
    @Test
    fun a_near_side_squat_finished_mid_repetition_counts_only_the_completed_ones() {
        val records = ArrayList<RepRecord>()
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat, onRepetition = { records += it })
        squat(analysis, reps = 2)
        for (step in 1..15) analysis.feed(body(knees = 170.0 - 100.0 * step / 30.0))

        analysis.finish(clock)

        assertEquals(2, analysis.repCount, "an unfinished squat was counted, or a finished one twice")
        assertEquals(listOf(RepValidity.VALID, RepValidity.VALID), records.take(2).map { it.validity })
        assertEquals(List(2) { RepValidity.INCOMPLETE }, records.drop(2).map { it.validity })
        assertTrue(records.all { it.attributedSide == null }, "a near-side squat belongs to no side")
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

    /**
     * DECISIONS.md P3-76: the angle the skeleton shows is the filtered one the repetition machine
     * steps on - not the raw one, which jitters, and not one recomputed by the screen.
     */
    @Test
    fun the_shown_angle_is_the_filtered_value_counting_is_decided_on() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat)
        repeat(20) { analysis.feed(body(knees = 170.0)) }
        assertEquals(170.0, analysis.angleLeft, 1e-9)
        assertEquals(170.0, analysis.angleRight, 1e-9)

        for (step in 1..3) analysis.feed(body(knees = 170.0 - 5.0 * step))

        // The filter lags the raw measurement on the way down: between it and where it came from.
        assertEquals(155.0, analysis.rawAngleLeft, 1e-9)
        assertTrue(analysis.angleLeft > 155.0 + 1e-6 && analysis.angleLeft < 170.0, "angle " + analysis.angleLeft)
        assertEquals(analysis.angleLeft, analysis.angleRight, 1e-9)

        analysis.onNothingSeen(clock)
        assertTrue(analysis.angleLeft.isNaN() && analysis.angleRight.isNaN())
    }

    /** Each side its own: a hidden right arm shows no right angle, and the left keeps its own. */
    @Test
    fun the_shown_angle_stays_per_side() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.bicepCurl)
        val hidden = mapOf(LandmarkId.RIGHT_WRIST to 0.05)
        repeat(20) { analysis.feed(body(leftElbow = 90.0, visibilityOf = hidden)) }

        assertEquals(90.0, analysis.angleLeft, 1e-9)
        assertTrue(analysis.angleRight.isNaN(), "right " + analysis.angleRight)

        val both = LiveRepetitionAnalysis(ArchetypeProfiles.bicepCurl)
        repeat(20) { both.feed(body(leftElbow = 90.0, rightElbow = 120.0)) }
        assertEquals(90.0, both.angleLeft, 1e-9)
        assertEquals(120.0, both.angleRight, 1e-9)
    }

    /**
     * DECISIONS.md P3-59: the app blocks scoring on a wrong camera view. This figure's shoulders
     * span about half its torso, which reads as angled; a squat must be filmed side-on.
     */
    @Test
    fun with_blocking_a_squat_filmed_from_the_wrong_side_counts_nothing_and_says_why() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat, setupEnforcement = SetupEnforcement.BLOCK_SCORING)
        squat(analysis, reps = 3)

        assertEquals(0, analysis.repCount)
        assertTrue(analysis.isScoringBlocked)
        assertTrue(assertNotNull(analysis.setupMessage).contains("paused"))
    }

    @Test
    fun with_blocking_a_squat_filmed_side_on_counts_as_before() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat, setupEnforcement = SetupEnforcement.BLOCK_SCORING)
        squat(analysis, reps = 3, lateral = SIDE_ON)

        assertEquals(3, analysis.repCount)
        assertFalse(analysis.isScoringBlocked)
    }

    /**
     * Report-only is what the MM-Fit harness and the JS facade use: MM-Fit films its curls
     * front-on, so blocking there would erase every published curl figure (P3-27 to P3-46).
     */
    @Test
    fun reporting_only_names_the_wrong_view_and_leaves_counting_untouched() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat)
        squat(analysis, reps = 3)

        assertEquals(3, analysis.repCount)
        assertFalse(analysis.isScoringBlocked)
        val message = assertNotNull(analysis.setupMessage)
        assertTrue(message.contains("side", ignoreCase = true))
        // DECISIONS.md P3-68: the app runs report-only, so the screen must not say counting is
        // paused while it counts.
        assertFalse(message.contains("paused", ignoreCase = true), message)
    }

    /** Turning away mid-repetition: the repetition in progress ends uncounted, as an occlusion would. */
    @Test
    fun with_blocking_turning_away_mid_repetition_ends_it_uncounted() {
        val records = ArrayList<RepRecord>()
        val analysis = LiveRepetitionAnalysis(
            ArchetypeProfiles.squat,
            setupEnforcement = SetupEnforcement.BLOCK_SCORING,
            onRepetition = { records += it },
        )
        squat(analysis, reps = 1, lateral = SIDE_ON)
        for (step in 1..15) analysis.feed(body(knees = 170.0 - 100.0 * step / 30.0, lateral = SIDE_ON))
        // Turned to face the camera half-way down, and finished the squat facing it.
        for (step in 16..30) analysis.feed(body(knees = 170.0 - 100.0 * step / 30.0))
        for (step in 1..30) analysis.feed(body(knees = 70.0 + 100.0 * step / 30.0))

        assertEquals(1, analysis.repCount)
        assertTrue(records.drop(1).none { it.validity == RepValidity.VALID }, "counted after turning away: " + records)
    }

    // --- Synthetic frames -----------------------------------------------------------------

    private fun squat(analysis: LiveRepetitionAnalysis, reps: Int, lateral: Double = 1.0) {
        repeat(10) { analysis.feed(body(lateral = lateral)) }
        repeat(reps) {
            for (step in 1..30) analysis.feed(body(knees = 170.0 - 100.0 * step / 30.0, lateral = lateral))
            for (step in 1..30) analysis.feed(body(knees = 70.0 + 100.0 * step / 30.0, lateral = lateral))
            repeat(10) { analysis.feed(body(lateral = lateral)) }
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
        /** How far apart left and right stand across the frame: 1.0 as drawn, [SIDE_ON] side-on. */
        lateral: Double = 1.0,
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
        fun across(x: Double) = 0.5 + (x - 0.5) * lateral
        chain(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST, across(0.42), 0.25, 0.40, leftElbow)
        chain(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST, across(0.58), 0.25, 0.40, rightElbow)
        chain(LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE, across(0.45), 0.55, 0.72, knees)
        chain(LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE, across(0.55), 0.55, 0.72, knees)
        return Landmarks.fromPacked(packed)
    }

    private companion object {
        const val FRAME_MS = 1000.0 / 30.0
        const val SIDE_ON = 0.1
    }
}
