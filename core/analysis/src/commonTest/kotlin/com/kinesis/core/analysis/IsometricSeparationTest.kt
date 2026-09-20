package com.kinesis.core.analysis

import com.kinesis.core.analysis.hold.HoldAnalyser
import com.kinesis.core.analysis.hold.HoldState
import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.profile.AnalysisMode
import com.kinesis.core.domain.rep.HoldValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Permanent guard test - CLAUDE.md sections 11 and 18.
 *
 * "MUST NOT: force an isometric exercise through the repetition FSM."
 *
 * The failure this prevents is quiet and absurd: a plank pushed through
 * rep-counting logic does not crash, it reports a rep count. Small wobbles cross
 * and re-cross a threshold, and the user is told they performed fourteen
 * repetitions of holding still. Worse, the number looks like a measurement.
 *
 * Enforcement is structural rather than conventional. A repetition profile and
 * an isometric profile are different types, and each analyser accepts only its
 * own: [com.kinesis.core.analysis.rep.BilateralRepTracker] takes a
 * [RepetitionExercise], [HoldAnalyser] takes an [IsometricExercise]. Passing a
 * plank to the rep tracker is a compile error, not a runtime check that could be
 * removed.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class IsometricSeparationTest {

    private val frameMs = 1000.0 / 30.0

    // --- The structural separation ----------------------------------------------

    /**
     * The suppression is the point. The compiler reports these checks as always
     * true or always false because it can prove the separation statically -
     * which is the guarantee, delivered at compile time. They are kept as
     * runtime assertions so that if a refactor ever makes either reachable, the
     * warning disappears and the assertion starts doing real work.
     */
    @Suppress("USELESS_IS_CHECK")
    @Test
    fun an_isometric_profile_is_not_a_repetition_profile() {
        // Swept across every hold in the library rather than the plank alone, so
        // the Wall Sit and Side Plank added in P3-9 - and anything added later -
        // are covered without this test needing to name them.
        val holds = ExerciseLibrary.all.filterIsInstance<IsometricExercise>()
        assertEquals(
            listOf("plank", "side_plank", "wall_sit"),
            holds.map { it.id }.sorted(),
            "an isometric hold exists that this sweep does not cover",
        )

        for (hold in holds) {
            assertTrue(hold is IsometricExercise)
            assertFalse(
                hold is RepetitionExercise,
                hold.id + " can be handed to the repetition tracker",
            )
            assertEquals(AnalysisMode.ISOMETRIC_HOLD, hold.mode)
        }

        val plank = ArchetypeProfiles.plank
        assertEquals(AnalysisMode.ISOMETRIC_HOLD, plank.mode)

        // The following does not compile, and must never be made to:
        //
        //     BilateralRepTracker(ArchetypeProfiles.plank, ...)
        //         // type mismatch: required RepetitionExercise
        //
        // That is the enforcement. The assertions above only notice if someone
        // widens the types so that it would start compiling.
    }

    @Suppress("USELESS_IS_CHECK")
    @Test
    fun a_repetition_profile_is_not_an_isometric_profile() {
        for (profile in listOf(ArchetypeProfiles.squat, ArchetypeProfiles.bicepCurl)) {
            assertTrue(profile is RepetitionExercise)
            assertFalse(
                profile is IsometricExercise,
                profile.id + " can be handed to the hold analyser",
            )
            assertEquals(AnalysisMode.REPETITION, profile.mode)
        }

        // And the reverse does not compile either:
        //
        //     HoldAnalyser(ArchetypeProfiles.squat, ...)
        //         // type mismatch: required IsometricExercise
    }

    /**
     * The one hole the type system leaves is a class implementing both marker
     * interfaces at once, which would be routable either way. Nothing does, and
     * this sweep is what notices if something starts to.
     */
    @Test
    fun no_profile_declares_itself_both_repetition_and_isometric() {
        for (profile in ExerciseLibrary.all) {
            val isRep = profile is RepetitionExercise
            val isHold = profile is IsometricExercise
            assertFalse(
                isRep && isHold,
                profile.id + " implements both RepetitionExercise and IsometricExercise",
            )
            assertTrue(
                isRep || isHold,
                profile.id + " implements neither, so nothing can analyse it",
            )
            assertEquals(
                if (isRep) AnalysisMode.REPETITION else AnalysisMode.ISOMETRIC_HOLD,
                profile.mode,
                profile.id + " declares a mode that disagrees with its marker interface",
            )
        }
    }

    /**
     * A hold produces no repetition count anywhere - not zero, not null, absent.
     * A field that could hold a rep count is a field something will eventually
     * populate.
     */
    @Test
    fun a_hold_record_has_no_repetition_count_to_report() {
        val analyser = HoldAnalyser(ArchetypeProfiles.plank)
        for ((timestamp, value) in holdTrack(steadySeconds = 10.0)) {
            analyser.step(timestamp, value)
        }
        val record = analyser.finish()

        // HoldRecord exposes duration, breaks, deviation and coverage. It has no
        // repetition count, and the following does not compile:
        //
        //     record.repCount        // unresolved reference
        //     record.completedReps   // unresolved reference
        assertTrue(record.totalHoldMs > 0.0)
        assertEquals(0, record.breakCount)
    }

    // --- Hold analysis behaviour --------------------------------------------------

    @Test
    fun a_steady_hold_is_measured_as_one_unbroken_period() {
        val analyser = HoldAnalyser(ArchetypeProfiles.plank)
        for ((timestamp, value) in holdTrack(steadySeconds = 12.0)) {
            analyser.step(timestamp, value)
        }
        val record = analyser.finish()

        assertEquals(HoldValidity.VALID, record.validity)
        assertEquals(0, record.breakCount)
        assertEquals(
            record.totalHoldMs,
            record.longestUnbrokenMs,
            absoluteTolerance = 1e-9,
            message = "an unbroken hold should have its longest period equal its total",
        )
        assertTrue(record.totalHoldMs > 11_000.0, "measured only " + record.totalHoldMs + " ms")
    }

    /**
     * Wobble is what makes an isometric exercise dangerous to rep-count. Here the
     * hips drop and recover twice; that is two breaks in one hold, and it must
     * never read as two repetitions.
     */
    @Test
    fun a_hold_that_sags_and_recovers_reports_breaks_not_repetitions() {
        val analyser = HoldAnalyser(ArchetypeProfiles.plank)
        val samples = ArrayList<Double>()
        samples += flat(175.0, 150)
        samples += flat(140.0, 45)
        samples += flat(175.0, 150)
        samples += flat(138.0, 45)
        samples += flat(175.0, 150)

        for ((timestamp, value) in track(samples)) analyser.step(timestamp, value)
        val record = analyser.finish()

        assertEquals(2, record.breakCount, "expected two sags")
        assertTrue(
            record.longestUnbrokenMs < record.totalHoldMs,
            "a broken hold cannot have its longest period equal its total",
        )
    }

    @Test
    fun a_hold_shorter_than_the_minimum_is_reported_as_too_short() {
        val analyser = HoldAnalyser(ArchetypeProfiles.plank)
        for ((timestamp, value) in holdTrack(steadySeconds = 0.5)) {
            analyser.step(timestamp, value)
        }
        assertEquals(HoldValidity.TOO_SHORT, analyser.finish().validity)
    }

    @Test
    fun a_hold_never_established_is_reported_rather_than_measured_as_zero() {
        val analyser = HoldAnalyser(ArchetypeProfiles.plank)
        // Never anywhere near the target body line.
        for ((timestamp, value) in track(flat(95.0, 300))) analyser.step(timestamp, value)

        // Checked before finishing: the position was never reached, so the
        // analyser never left its initial state.
        assertEquals(HoldState.NOT_STARTED, analyser.state)

        val record = analyser.finish()
        assertEquals(HoldValidity.TOO_SHORT, record.validity)
        assertEquals(0.0, record.totalHoldMs)
        assertEquals(HoldState.ENDED, analyser.state)
    }

    @Test
    fun losing_the_subject_during_a_hold_is_recorded_as_missing_not_as_holding() {
        val analyser = HoldAnalyser(ArchetypeProfiles.plank)
        val samples = flat(175.0, 150) + List(90) { Double.NaN } + flat(175.0, 150)
        for ((timestamp, value) in track(samples)) analyser.step(timestamp, value)
        val record = analyser.finish()

        assertTrue(record.coverage < 1.0, "coverage did not register the lost frames")
        assertTrue(
            record.totalHoldMs < 390 * frameMs,
            "time with no subject visible was counted as hold time",
        )
    }

    @Test
    fun the_deviation_reported_is_the_worst_the_hold_actually_reached() {
        val analyser = HoldAnalyser(ArchetypeProfiles.plank)
        val samples = flat(175.0, 100) + flat(168.0, 30) + flat(175.0, 100)
        for ((timestamp, value) in track(samples)) analyser.step(timestamp, value)
        val record = analyser.finish()

        // Target is 180; the worst excursion inside tolerance reached 168.
        assertEquals(12.0, record.maxAbsDeviation, absoluteTolerance = 1e-9)
        assertTrue(record.meanAbsDeviation < record.maxAbsDeviation)
    }

    // --- Track construction --------------------------------------------------------

    private fun holdTrack(steadySeconds: Double): List<Pair<Double, Double>> =
        track(flat(175.0, (steadySeconds * 30.0).toInt()))

    private fun flat(value: Double, steps: Int): List<Double> = List(steps) { value }

    private fun track(samples: List<Double>): List<Pair<Double, Double>> =
        samples.mapIndexed { index, value -> (index * frameMs) to value }
}
