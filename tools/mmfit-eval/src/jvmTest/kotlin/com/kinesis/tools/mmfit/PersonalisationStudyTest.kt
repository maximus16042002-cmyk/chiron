package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The study runner's own pieces - DECISIONS.md P3-30. What it measures is the core's; these check that
 * the planted deviations are what the protocol says, and that sets.csv is read as written.
 */
class PersonalisationStudyTest {

    private val press = ExerciseLibrary.shoulderPress

    /** A shoulder press repetition: the start is the smaller angle, so the trough is above the peak. */
    private fun rep(index: Int, left: Double, right: Double) = RepRecord(
        repIndex = index, attributedSide = null, startMs = 0.0, endMs = 2_000.0, validity = RepValidity.VALID,
        rangeOfMotion = BilateralSignal(left, right),
        peakValue = BilateralSignal(90.0, 92.0),
        troughValue = BilateralSignal(90.0 + left, 92.0 + right),
        descentMs = 900.0, ascentMs = 1_100.0,
        resampledLeft = DoubleArray(64) { 90.0 }, resampledRight = DoubleArray(64) { 92.0 },
    )

    private val baseline by lazy {
        (Calibration.fromRepetitions(press, (1..8).map { rep(it, 80.0, 70.0) }, 0.0, null, StudyPolicy.P3_22) as CalibrationOutcome.Calibrated)
            .baseline as RepetitionBaseline
    }

    @Test
    fun a_range_cut_takes_that_share_of_the_subjects_own_range_from_every_measured_side() {
        val cut = Planting.cutRange(press, rep(9, 82.0, 71.0), baseline, 0.3, null)
        assertEquals(82.0 - 0.3 * 80.0, cut.rangeOfMotion.left, 1e-12)
        assertEquals(71.0 - 0.3 * 70.0, cut.rangeOfMotion.right, 1e-12)
        // The start is the smaller angle here, so the trough sits above the peak and comes down with the cut.
        assertEquals(90.0 + cut.rangeOfMotion.left, cut.troughValue.left, 1e-12)
    }

    @Test
    fun one_side_shortened_leaves_the_other_alone() {
        val cut = Planting.cutRange(press, rep(9, 82.0, 71.0), baseline, 0.4, Side.RIGHT)
        assertEquals(82.0, cut.rangeOfMotion.left)
        assertEquals(71.0 - 0.4 * 70.0, cut.rangeOfMotion.right, 1e-12)
    }

    @Test
    fun speeding_up_divides_both_phases() {
        val fast = Planting.speedUp(rep(9, 82.0, 71.0), 2.0)
        assertEquals(450.0, fast.descentMs)
        assertEquals(550.0, fast.ascentMs)
        assertTrue(fast.rangeOfMotion == BilateralSignal(82.0, 71.0))
    }

    @Test
    fun a_quoted_csv_field_keeps_its_commas() {
        assertEquals(listOf("a", "b, c", "d\"e", ""), StudyInput.csv("a,\"b, c\",\"d\"\"e\","))
    }
}
