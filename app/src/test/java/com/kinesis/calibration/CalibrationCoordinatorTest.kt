package com.kinesis.calibration

import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.analysis.RepetitionAnalysisResult
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.storage.BaselineRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Finishing a calibration set in the app - DECISIONS.md P3-22: the baseline is
 * the core's, and this only decides when it is stored.
 */
class CalibrationCoordinatorTest {

    private class FakeBaselines : BaselineRepository {
        val stored = HashMap<Pair<String, String>, CalibrationBaseline>()
        var saves = 0
        override suspend fun baselinesFor(ownerUid: String): Map<String, CalibrationBaseline> =
            stored.filterKeys { it.first == ownerUid }.mapKeys { it.key.second }
        override suspend fun save(ownerUid: String, baseline: CalibrationBaseline) {
            saves++
            stored[ownerUid to baseline.calibrationId] = baseline
        }
    }

    private fun squat(index: Int, rom: Double, validity: RepValidity = RepValidity.VALID) = RepRecord(
        repIndex = index,
        attributedSide = null,
        startMs = index * 3_000.0,
        endMs = index * 3_000.0 + 1_800.0,
        validity = validity,
        rangeOfMotion = BilateralSignal(rom, rom - 5.0),
        peakValue = BilateralSignal(165.0, 164.0),
        troughValue = BilateralSignal(165.0 - rom, 169.0 - rom),
        descentMs = 1_000.0,
        ascentMs = 800.0,
        resampledLeft = DoubleArray(64) { 165.0 - rom * sin(PI * it / 63) },
        resampledRight = DoubleArray(64) { 164.0 - (rom - 5.0) * sin(PI * it / 63) },
    )

    private fun squatSession(id: String, startedAt: Double, reps: List<RepRecord>) =
        SessionRecord(id, startedAt, 60, listOf(RepetitionSetRecord("squat", startedAt + 500.0, reps)))

    private fun eightSquats(rom: Double) = (1..8).map { squat(it, rom) }

    @Test
    fun a_calibration_set_with_enough_valid_repetitions_saves_its_baseline() = runBlocking {
        val baselines = FakeBaselines()
        val outcome = CalibrationCoordinator(baselines)
            .finishCalibration("uid-a", ExerciseLibrary.squat, squatSession("s1", 1_000.0, eightSquats(100.0)))
        assertTrue(outcome is CalibrationOutcome.Calibrated)
        val saved = baselines.stored["uid-a" to "squat"] as RepetitionBaseline
        assertEquals(100.0, saved.rangeOfMotion.left.median, 0.0)
        assertEquals(95.0, saved.rangeOfMotion.right.median, 0.0)
        // Provenance: when the set was performed, and which session holds it.
        assertEquals(1_500.0, saved.createdAtEpochMs, 0.0)
        assertEquals("s1", saved.sourceSessionId)
    }

    @Test
    fun recalibrating_replaces_the_baseline() = runBlocking {
        val baselines = FakeBaselines()
        val coordinator = CalibrationCoordinator(baselines)
        coordinator.finishCalibration("uid-a", ExerciseLibrary.squat, squatSession("s1", 0.0, eightSquats(100.0)))
        coordinator.finishCalibration("uid-a", ExerciseLibrary.squat, squatSession("s2", 9.0, eightSquats(80.0)))
        assertEquals(1, baselines.stored.size)
        assertEquals("s2", baselines.stored.getValue("uid-a" to "squat").sourceSessionId)
    }

    /** A set that calibrates nothing must not cost the user the baseline they had. */
    @Test
    fun a_set_that_does_not_calibrate_leaves_the_earlier_baseline_alone() = runBlocking {
        val baselines = FakeBaselines()
        val coordinator = CalibrationCoordinator(baselines)
        coordinator.finishCalibration("uid-a", ExerciseLibrary.squat, squatSession("s1", 0.0, eightSquats(100.0)))
        val before = baselines.stored.getValue("uid-a" to "squat")
        val weak = (1..3).map { squat(it, 90.0) } + (4..8).map { squat(it, 40.0, RepValidity.ABORTED) }
        val outcome = coordinator.finishCalibration("uid-a", ExerciseLibrary.squat, squatSession("s2", 5.0, weak))
        assertTrue(outcome is CalibrationOutcome.TooFewRepetitions)
        assertTrue(outcome.displayText.startsWith("No baseline yet."))
        assertSame(before, baselines.stored.getValue("uid-a" to "squat"))
        assertEquals(1, baselines.saves)
    }

    @Test
    fun baselines_are_per_user() = runBlocking {
        val baselines = FakeBaselines()
        val coordinator = CalibrationCoordinator(baselines)
        coordinator.finishCalibration("uid-a", ExerciseLibrary.squat, squatSession("s1", 0.0, eightSquats(100.0)))
        assertNotNull(coordinator.baselineFor("uid-a", ExerciseLibrary.squat))
        assertNull(coordinator.baselineFor("uid-b", ExerciseLibrary.squat))
    }

    /** CONTRACTS.md section 3: a goblet squat reads the squat baseline. */
    @Test
    fun a_variant_reads_its_parents_baseline() = runBlocking {
        val baselines = FakeBaselines()
        val coordinator = CalibrationCoordinator(baselines)
        coordinator.finishCalibration("uid-a", ExerciseLibrary.squat, squatSession("s1", 0.0, eightSquats(100.0)))
        assertEquals("squat", coordinator.baselineFor("uid-a", ExerciseLibrary.gobletSquat)?.calibrationId)
    }

    @Test
    fun a_hold_calibrates_from_its_one_hold() = runBlocking {
        val baselines = FakeBaselines()
        val hold = HoldRecord(0.0, 16_000.0, HoldValidity.VALID, 15_000.0, 15_000.0, 0, 4.0, 9.0, 0.98, 176.0, 1.8)
        val session = SessionRecord("h1", 0.0, 0, listOf(HoldSetRecord("plank", 0.0, listOf(hold))))
        CalibrationCoordinator(baselines).finishCalibration("uid-a", ExerciseLibrary.plank, session)
        val saved = baselines.stored.getValue("uid-a" to "plank") as HoldBaseline
        assertEquals(176.0, saved.meanHeldValue, 0.0)
        assertEquals(1.8, saved.sway, 0.0)
    }

    @Test
    fun the_set_summary_puts_every_record_beside_the_baseline_or_beside_nothing() = runBlocking {
        val baselines = FakeBaselines()
        val coordinator = CalibrationCoordinator(baselines)
        coordinator.finishCalibration("uid-a", ExerciseLibrary.squat, squatSession("s1", 0.0, eightSquats(100.0)))
        val set = squatSession("s2", 0.0, listOf(squat(1, 92.0), squat(2, 50.0, RepValidity.ABORTED)))
        val baseline = coordinator.baselineFor("uid-a", ExerciseLibrary.squat)

        val withBaseline = coordinator.analyseSet(ExerciseLibrary.squat, set, baseline)
        assertEquals(2, withBaseline.size)
        val first = withBaseline[0] as RepetitionAnalysisResult
        assertEquals(92.0, first.comparison!!.rangeOfMotionLeft.measured, 0.0)
        assertEquals(100.0, first.comparison!!.rangeOfMotionLeft.baseline, 0.0)
        assertTrue(withBaseline.all { it.findings.isEmpty() })

        val without = coordinator.analyseSet(ExerciseLibrary.squat, set, null)
        assertTrue(without.all { (it as RepetitionAnalysisResult).comparison == null })
    }
}
