package com.kinesis.core.analysis.history

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.core.domain.session.SetRecord

/** Small histories for the records, goal and achievement tests - DECISIONS.md P3-49. */
internal object HistoryFixtures {

    val today = LocalDay(20_000)

    private var next = 0

    fun noon(offsetDays: Int): Double = (today.dayNumber + offsetDays) * LocalDay.MILLIS_PER_DAY + 12 * 3_600_000.0

    fun session(offsetDays: Int, vararg sets: SetRecord): SessionRecord {
        val at = noon(offsetDays)
        return SessionRecord(sessionId = "s" + (next++), startedAtEpochMs = at, utcOffsetMinutes = 0, sets = sets.toList())
    }

    /** [valid] counted repetitions, each with the given range on each side, then [aborted] turned back. */
    fun reps(
        exerciseId: String,
        valid: Int,
        aborted: Int = 0,
        left: Double = 90.0,
        right: Double = 90.0,
        side: Side? = null,
    ): RepetitionSetRecord = RepetitionSetRecord(
        exerciseId = exerciseId,
        startedAtEpochMs = 0.0,
        reps = List(valid) { rep(it + 1, RepValidity.VALID, left, right, side) } +
            List(aborted) { rep(valid + it + 1, RepValidity.ABORTED, left, right, side) },
    )

    /** One hold per entry of [heldMs], each with the given validity. */
    fun holds(exerciseId: String, heldMs: List<Double>, validity: HoldValidity = HoldValidity.VALID): HoldSetRecord =
        HoldSetRecord(exerciseId = exerciseId, startedAtEpochMs = 0.0, holds = heldMs.map { hold(it, validity) })

    private fun rep(index: Int, validity: RepValidity, left: Double, right: Double, side: Side?): RepRecord = RepRecord(
        repIndex = index,
        attributedSide = side,
        startMs = 0.0,
        endMs = 2_000.0,
        validity = validity,
        rangeOfMotion = BilateralSignal(left, right),
        peakValue = BilateralSignal(170.0, 170.0),
        troughValue = BilateralSignal(80.0, 80.0),
        descentMs = 1_000.0,
        ascentMs = 1_000.0,
        resampledLeft = DoubleArray(64),
        resampledRight = DoubleArray(64),
    )

    private fun hold(ms: Double, validity: HoldValidity): HoldRecord = HoldRecord(
        startMs = 0.0,
        endMs = ms,
        validity = validity,
        totalHoldMs = ms,
        longestUnbrokenMs = ms,
        breakCount = 0,
        meanAbsDeviation = 3.0,
        maxAbsDeviation = 8.0,
        coverage = 1.0,
        meanHeldValue = Double.NaN,
        heldValueSd = Double.NaN,
    )
}
