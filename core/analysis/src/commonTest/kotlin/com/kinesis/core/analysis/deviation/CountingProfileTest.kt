package com.kinesis.core.analysis.deviation

import com.kinesis.core.analysis.calibration.CalibrationFixtures
import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.calibration.PostRepAnalysis
import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.technique.TechniqueRules
import com.kinesis.core.analysis.verdict.RepOutcome
import com.kinesis.core.analysis.verdict.RepVerdicts
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.finding.TechniqueFault
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Which profile a live set counts with - DECISIONS.md P3-82: the push-up's personal start zone,
 * passed on MM-Fit's development and held-out people by a protocol fixed first, used once the user
 * has calibrated push-ups; a push-up calibration set counts with the floor zone.
 */
class CountingProfileTest {

    private val pushUp = ExerciseLibrary.pushUp

    private fun calibratedWithTops(vararg tops: Double): RepetitionBaseline {
        val reps = tops.mapIndexed { i, top -> oneSided(i + 1, Side.RIGHT, 60.0, attributed = null, top = top) }
        return assertIs<RepetitionBaseline>(
            assertIs<CalibrationOutcome.Calibrated>(Calibration.fromRepetitions(pushUp, reps, 0.0, null, CalibrationFixtures.SMALL_SAMPLE_POLICY)).baseline,
        )
    }

    private fun startEdge(profile: RepetitionExercise) = profile.repSignal.topThreshold - profile.repSignal.hysteresis

    @Test
    fun a_push_up_calibration_set_counts_with_the_floor_zone() {
        val counting = assertIs<RepetitionExercise>(PersonalStartZone.countingProfile(pushUp, null, calibrating = true))
        assertEquals(126.0, startEdge(counting), 1e-12)
        assertEquals("pushup", counting.id)
    }

    @Test
    fun a_calibrated_push_up_counts_with_the_users_own_start_zone() {
        val baseline = calibratedWithTops(133.0, 134.0, 135.0, 135.0, 135.0, 136.0, 137.0)
        val counting = assertIs<RepetitionExercise>(PersonalStartZone.countingProfile(pushUp, baseline, calibrating = false))
        assertEquals(130.0, startEdge(counting), 1e-12)
        assertEquals(pushUp.id, counting.id)
        assertEquals(pushUp.repSignal.bottomThreshold, counting.repSignal.bottomThreshold)
        assertEquals(pushUp.tooFastBelowMs, counting.tooFastBelowMs)
    }

    @Test
    fun an_uncalibrated_push_up_and_every_other_exercise_count_as_they_are() {
        assertSame(pushUp, PersonalStartZone.countingProfile(pushUp, null, calibrating = false))
        val squat = ArchetypeProfiles.squat
        assertSame(squat, PersonalStartZone.countingProfile(squat, null, calibrating = true))
        assertSame(squat, PersonalStartZone.countingProfile(squat, null, calibrating = false))
    }

    /** A calibration whose tops are already at the profile's edge changes nothing. */
    @Test
    fun a_calibration_at_the_profiles_own_edge_keeps_the_profile() {
        val baseline = calibratedWithTops(170.0, 170.0, 170.0, 170.0, 170.0)
        assertSame(pushUp, PersonalStartZone.countingProfile(pushUp, baseline, calibrating = false))
    }

    /**
     * The personalised push-up is still the Tier A push-up: its cited rules and its verdicts judge
     * it, so the live screen and the set summary say the same thing.
     */
    @Test
    fun the_personalised_push_up_keeps_its_technique_rules() {
        val baseline = calibratedWithTops(133.0, 134.0, 135.0, 135.0, 135.0, 136.0, 137.0)
        val counting = assertIs<RepetitionExercise>(PersonalStartZone.countingProfile(pushUp, baseline, calibrating = false))
        val rep = oneSided(1, Side.RIGHT, 60.0, attributed = null, top = 135.0)
        assertSame(pushUp, PersonalStartZone.baseOf(counting))
        val verdict = RepVerdicts.judge(counting, rep, emptyList())
        assertEquals(RepOutcome.FORM_FAULT, verdict.outcome)
        assertEquals(TechniqueRules.PUSHUP_ARMS_NOT_EXTENDED, verdict.faultId)
        val findings = PostRepAnalysis.analyse(counting, rep, null).findings
        assertTrue(findings.any { it is TechniqueFault && it.faultId == TechniqueRules.PUSHUP_ARMS_NOT_EXTENDED })
    }
}
