package com.kinesis.storage

import com.kinesis.core.analysis.calibration.BaselineJson
import com.kinesis.core.domain.calibration.AsymmetryBaseline
import com.kinesis.core.domain.calibration.BilateralStatistic
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.PhaseTimingBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Baselines to Room rows and back - DECISIONS.md P3-22. Room's own SQL is tested
 * on the phone by `KinesisDatabaseMigrationTest`; this is the part that can be
 * wrong without a database.
 */
class BaselineMappingTest {

    private fun stat(median: Double, n: Int = 6) = RobustStatistic(median, 2.0, n)

    /** The near-side-only row: the far side never measured. */
    private val row = RepetitionBaseline(
        calibrationId = "row",
        estimatorVersion = "p6-1",
        createdAtEpochMs = 1_727_000_000_000.0,
        sourceSessionId = "session-7",
        rangeOfMotion = BilateralStatistic(RobustStatistic.NOT_MEASURED, stat(70.0)),
        peakValue = BilateralStatistic(RobustStatistic.NOT_MEASURED, stat(160.0)),
        troughValue = BilateralStatistic(RobustStatistic.NOT_MEASURED, stat(90.0)),
        descentMs = PhaseTimingBaseline(BilateralStatistic(RobustStatistic.NOT_MEASURED, stat(900.0)), RobustStatistic.NOT_MEASURED),
        ascentMs = PhaseTimingBaseline(BilateralStatistic(RobustStatistic.NOT_MEASURED, stat(700.0)), RobustStatistic.NOT_MEASURED),
        asymmetry = AsymmetryBaseline.NotMeasurable,
        meanCurveLeft = DoubleArray(64) { Double.NaN },
        meanCurveRight = DoubleArray(64) { 160.0 - it },
        curveSpread = BilateralStatistic(RobustStatistic.NOT_MEASURED, stat(1.5)),
    )

    private val plank = HoldBaseline("plank", "p6-1", 5.0, null, 180.0, 174.5, 6.0, 2.25, 12_000.0, 1, 0.95)

    @Test
    fun a_repetition_baseline_round_trips_with_its_unmeasured_side_still_unmeasured() {
        val entity = BaselineMapping.toEntity("uid-a", row)
        assertEquals("uid-a", entity.ownerUid)
        assertEquals("row", entity.calibrationId)
        assertEquals(BaselineMapping.KIND_REPETITION, entity.kind)
        assertEquals("session-7", entity.sourceSessionId)
        val back = BaselineMapping.toDomain(entity) as RepetitionBaseline
        assertEquals(row.rangeOfMotion, back.rangeOfMotion)
        assertEquals(row.descentMs, back.descentMs)
        assertEquals(AsymmetryBaseline.NotMeasurable, back.asymmetry)
        // Not measured reloads as not measured - never as a measurement of zero.
        assertTrue(back.rangeOfMotion.left.median.isNaN())
        assertTrue(back.meanCurveLeft.all { it.isNaN() })
        assertEquals(row.meanCurveRight.toList(), back.meanCurveRight.toList())
    }

    @Test
    fun a_hold_baseline_round_trips() {
        val entity = BaselineMapping.toEntity("uid-a", plank)
        assertEquals(BaselineMapping.KIND_HOLD, entity.kind)
        assertNull(entity.sourceSessionId)
        val back = BaselineMapping.toDomain(entity) as HoldBaseline
        assertEquals(174.5, back.meanHeldValue, 0.0)
        assertEquals(2.25, back.sway, 0.0)
        assertEquals(1, back.breakCount)
    }

    /** The payload is the core's own format: left and right never share a field. */
    @Test
    fun the_stored_payload_is_the_cores_baseline_json() {
        val entity = BaselineMapping.toEntity("uid-a", row)
        assertEquals(BaselineJson.write(row), entity.payloadJson)
        assertTrue(entity.payloadJson.contains("\"left\":{\"median\":null"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun a_row_whose_columns_disagree_with_its_payload_is_refused() {
        BaselineMapping.toDomain(BaselineMapping.toEntity("uid-a", row).copy(calibrationId = "squat"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun a_damaged_payload_is_refused_not_half_read() {
        BaselineMapping.toDomain(BaselineMapping.toEntity("uid-a", row).copy(payloadJson = "{\"kind\":\"repetition\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun a_baseline_without_a_user_is_refused() {
        BaselineMapping.toEntity("", row)
    }
}
