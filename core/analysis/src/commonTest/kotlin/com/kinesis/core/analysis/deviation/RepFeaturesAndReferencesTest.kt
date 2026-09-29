package com.kinesis.core.analysis.deviation

import com.kinesis.core.analysis.calibration.CalibrationFixtures.oneSided
import com.kinesis.core.analysis.calibration.CalibrationFixtures.paired
import com.kinesis.core.analysis.profile.Contraction
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.RobustStatistic
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Layer 1's features and its two references - DECISIONS.md P3-30.
 */
class RepFeaturesAndReferencesTest {

    // --- Which phase is the lift ------------------------------------------------------

    /**
     * Pinned for every repetition profile. Moving away from the start is the lowering
     * for the squat, push-up and lunge (the start is the top of a lowering movement),
     * and the lift for the other nine. Variants inherit their parent's.
     */
    @Test
    fun every_repetition_profile_declares_which_phase_is_the_lift() {
        val lowering = setOf("squat", "pushup", "lunge")
        val profiles = ExerciseLibrary.all.filterIsInstance<RepetitionExercise>()
        assertEquals(14, profiles.size, "12 repetition profiles and 2 variants")
        for (profile in profiles) {
            val parentId = (profile as? ExerciseVariant)?.parent?.id ?: profile.id
            val expected = if (parentId in lowering) Contraction.ECCENTRIC else Contraction.CONCENTRIC
            assertEquals(expected, profile.awayFromStart, profile.id)
        }
    }

    @Test
    fun a_curl_lifts_away_from_the_start_and_a_squat_lowers() {
        val curl = RepFeatures.of(ExerciseLibrary.bicepCurl, oneSided(1, Side.LEFT, 100.0, descentMs = 900.0, ascentMs = 700.0))
        assertEquals(900.0, curl.liftingMs)
        assertEquals(700.0, curl.loweringMs)
        val squat = RepFeatures.of(ExerciseLibrary.squat, paired(1, 100.0, 90.0, descentMs = 1_000.0, ascentMs = 800.0))
        assertEquals(800.0, squat.liftingMs)
        assertEquals(1_000.0, squat.loweringMs)
    }

    @Test
    fun timing_is_attributed_as_the_record_attributes_it() {
        assertEquals(Side.RIGHT, RepFeatures.of(ExerciseLibrary.bicepCurl, oneSided(1, Side.RIGHT, 100.0)).timingSide)
        assertNull(RepFeatures.of(ExerciseLibrary.squat, paired(1, 100.0, 90.0)).timingSide)
    }

    @Test
    fun asymmetry_exists_only_where_both_sides_were_measured_in_the_repetition() {
        assertEquals(10.0, RepFeatures.of(ExerciseLibrary.squat, paired(1, 100.0, 90.0)).leftMinusRight)
        assertTrue(RepFeatures.of(ExerciseLibrary.bicepCurl, oneSided(1, Side.LEFT, 100.0)).leftMinusRight.isNaN())
    }

    @Test
    fun range_of_motion_stays_per_side() {
        val features = RepFeatures.of(ExerciseLibrary.squat, paired(1, 100.0, 90.0))
        assertEquals(100.0, features.rangeOfMotion.left)
        assertEquals(90.0, features.rangeOfMotion.right)
    }

    // --- The scaled deviation ---------------------------------------------------------

    @Test
    fun a_deviation_is_scaled_by_the_spread_or_the_floor_whichever_is_larger() {
        assertEquals(1.5, scaledDeviation(10.0, 4.0, 4.0, 3.0))
        assertEquals(2.0, scaledDeviation(10.0, 4.0, 2.0, 3.0), "the floor, not the spread")
        assertEquals(-2.0, scaledDeviation(-2.0, 4.0, 0.0, 3.0), "no spread at all: the floor")
    }

    @Test
    fun not_measured_is_never_a_deviation() {
        assertTrue(scaledDeviation(Double.NaN, 4.0, 2.0, 3.0).isNaN())
        assertTrue(scaledDeviation(10.0, Double.NaN, 2.0, 3.0).isNaN())
        assertTrue(PersonalReference.of(RobustStatistic.NOT_MEASURED).scaled(10.0, 3.0).isNaN())
    }

    @Test
    fun a_personal_reference_is_the_median_and_the_iqr_as_a_robust_sd() {
        val reference = PersonalReference.of(RobustStatistic(100.0, 13.49, 8))
        assertEquals(100.0, reference.centre)
        assertEquals(10.0, reference.spread, 1e-12)
        assertEquals(1.349, IQR_PER_SD)
    }

    @Test
    fun a_population_reference_combines_between_and_within_person_spread() {
        val reference = PopulationReference.fromSubjects(
            medians = doubleArrayOf(10.0, 20.0, 30.0),
            withinSpreads = doubleArrayOf(3.0, 4.0, 5.0),
            provenance = "test",
        )
        assertEquals(20.0, reference.centre, 1e-12)
        // Between: the sample SD of 10, 20, 30 is 10. Within: the root mean square of 3, 4, 5.
        assertEquals(sqrt(100.0 + (9.0 + 16.0 + 25.0) / 3.0), reference.spread, 1e-12)
        assertEquals("test", reference.provenance)
    }

    @Test
    fun one_persons_population_has_no_between_person_spread() {
        val reference = PopulationReference.fromSubjects(doubleArrayOf(42.0), doubleArrayOf(3.0), "one")
        assertEquals(42.0, reference.centre)
        assertEquals(3.0, reference.spread, 1e-12)
    }
}
