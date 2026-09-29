package com.kinesis.core.analysis.deviation

import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepSignalSpec
import com.kinesis.core.analysis.rep.RepFsm
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * The push-up's and sit-up's personal start zone - the P3-29 proposal, DECISIONS.md
 * P3-30: from the user's calibration tops, never stricter than the profile, never
 * looser than a floor from development data.
 */
class PersonalStartZoneTest {

    private val pushUp = ExerciseLibrary.pushUp

    /** A near-side push-up calibration whose tops (start-position angles) are [tops]. */
    private fun calibratedWithTops(vararg tops: Double): RepetitionBaseline {
        val reps = tops.mapIndexed { i, top -> oneSided(i + 1, Side.RIGHT, 60.0, attributed = null, top = top) }
        return assertIs<RepetitionBaseline>(
            assertIs<CalibrationOutcome.Calibrated>(Calibration.fromRepetitions(pushUp, reps, 0.0, null)).baseline,
        )
    }

    @Test
    fun the_floors_are_the_values_derived_from_the_development_subjects() {
        // Depth edge + half the smallest development subject's median sweep (P3-30):
        // push-up 95 + 31.0, sit-up 83 + 31.4.
        assertEquals(126.0, PersonalStartZone.floorEdge(pushUp))
        assertEquals(114.4, PersonalStartZone.floorEdge(ExerciseLibrary.situp))
    }

    @Test
    fun a_user_whose_tops_read_low_gets_a_looser_zone_of_their_own() {
        // Median 135, IQR 1: the margin is the hysteresis, 5. Edge 130: looser than 155, above the floor.
        val zone = PersonalStartZone.of(pushUp, calibratedWithTops(133.0, 134.0, 135.0, 135.0, 135.0, 136.0, 137.0))
        assertEquals(StartZoneSource.PERSONAL, zone.source)
        assertEquals(130.0, zone.edge, 1e-12)
    }

    @Test
    fun a_shallow_calibration_cannot_loosen_the_zone_below_the_floor() {
        val zone = PersonalStartZone.of(pushUp, calibratedWithTops(120.0, 120.0, 120.0, 120.0, 120.0))
        assertEquals(StartZoneSource.FLOOR, zone.source)
        assertEquals(126.0, zone.edge)
    }

    @Test
    fun a_personal_zone_is_never_stricter_than_the_profiles() {
        val zone = PersonalStartZone.of(pushUp, calibratedWithTops(170.0, 170.0, 170.0, 170.0, 170.0))
        assertEquals(StartZoneSource.PROFILE, zone.source)
        assertEquals(155.0, zone.edge)
    }

    @Test
    fun without_a_calibration_the_profiles_own_zone_stands() {
        val zone = PersonalStartZone.of(pushUp, null)
        assertEquals(StartZoneSource.PROFILE, zone.source)
        assertEquals(155.0, zone.edge)
    }

    @Test
    fun only_the_push_up_and_the_sit_up_have_one() {
        assertEquals(setOf("pushup", "situp"), PersonalStartZone.PROFILE_IDS)
        assertFailsWith<IllegalArgumentException> { PersonalStartZone.of(ExerciseLibrary.squat, null) }
        assertFailsWith<IllegalArgumentException> { PersonalStartZone.floorEdge(ExerciseLibrary.squat) }
    }

    @Test
    fun the_personalised_profile_changes_the_start_zone_and_nothing_else() {
        val personal = PersonalStartZone.profileWith(pushUp, StartZone(130.0, StartZoneSource.PERSONAL))
        assertEquals(135.0, personal.repSignal.topThreshold, "edge 130 + hysteresis 5")
        assertEquals(pushUp.repSignal.copy(topThreshold = 135.0), personal.repSignal)
        assertEquals(pushUp.id, personal.id)
        assertEquals(pushUp.tier, personal.tier)
        assertEquals(pushUp.analysedSides, personal.analysedSides)
        assertEquals(pushUp.awayFromStart, personal.awayFromStart)
        assertEquals(pushUp.requiredView, personal.requiredView)
    }

    @Test
    fun a_calibration_set_counts_with_the_floor_zone() {
        assertEquals(126.0 + 5.0, PersonalStartZone.forCalibration(pushUp).repSignal.topThreshold, 1e-12)
        assertEquals(114.4 + 8.0, PersonalStartZone.forCalibration(ExerciseLibrary.situp).repSignal.topThreshold, 1e-12)
    }

    // --- What it changes in counting ----------------------------------------------------

    /** Valid repetitions a state machine counts over [cycles] smooth cycles between [top] and [bottom], 2 s each at 30 fps. */
    private fun counted(spec: RepSignalSpec, top: Double, bottom: Double, cycles: Int = 5): Int {
        val fsm = RepFsm(spec, bufferCapacity = 256)
        var valid = 0
        val frames = cycles * 60 + 30
        for (frame in 0..frames) {
            val phase = if (frame < 15) 0.0 else ((frame - 15) % 60) / 60.0
            val value = bottom + (top - bottom) * (1 + cos(2 * PI * phase)) / 2
            if (fsm.step(frame * 1_000.0 / 30, value)?.validity == RepValidity.VALID) valid++
        }
        return valid
    }

    @Test
    fun a_low_topped_push_up_counts_with_the_personal_zone_and_not_the_profiles() {
        // Arms return to 133 each time: never 155, always 130.
        assertEquals(0, counted(pushUp.repSignal, top = 133.0, bottom = 85.0))
        val personal = PersonalStartZone.profileWith(pushUp, StartZone(130.0, StartZoneSource.PERSONAL))
        assertEquals(5, counted(personal.repSignal, top = 133.0, bottom = 85.0))
    }

    @Test
    fun a_wobble_at_the_bottom_never_counts_even_with_the_loosest_zone() {
        // 115 to 88: a 27° sweep, short of the floor's 31°.
        assertEquals(0, counted(PersonalStartZone.forCalibration(pushUp).repSignal, top = 115.0, bottom = 88.0))
    }
}
