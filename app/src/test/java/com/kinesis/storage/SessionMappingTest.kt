package com.kinesis.storage

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent guard - CLAUDE.md sections 3 and 15, DECISIONS.md P3-12.
 *
 * Storage is the last place a measurement can be quietly corrupted, and the two
 * ways it can happen here are both invisible at the call site.
 *
 * **Losing a side.** CLAUDE.md section 3 says left and right stay separate
 * "everywhere". A schema with one range-of-motion column would force a reducer
 * at write time - the prototype's `(left + right) / 2f` (P2-2) made permanent,
 * because unlike a display bug there would be nothing left to recover from.
 *
 * **Losing a `NaN`.** SQLite cannot store one in a REAL column. `NaN` is the
 * sentinel that means "not measured" throughout Kinesis: the idle arm of an
 * alternating repetition, the invisible arm of the near-side-only row (P3-8).
 * A round trip that turns it into `0.0` writes a **false measurement into the
 * database** - not "we did not see this" but "we measured zero" - and every
 * dashboard average computed afterwards is wrong in a way no one can trace.
 *
 * These are unit tests on pure functions, so they need no device and no
 * database. What they cannot cover is Room's own SQL, which P3-12 flags for
 * instrumentation work alongside P4.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class SessionMappingTest {

    // --- Bilateral integrity, in storage ----------------------------------------------

    /**
     * The schema-level version of the bilateral rule. An asymmetric repetition
     * must still be asymmetric after a round trip; if both sides come back equal,
     * something reduced them.
     */
    @Test
    fun an_asymmetric_repetition_is_still_asymmetric_after_a_round_trip() {
        val original = session(
            RepetitionSetRecord(
                "squat", 0.0,
                listOf(rep(1, rangeOfMotion = BilateralSignal(104.0, 78.0))),
            ),
        )

        val restored = roundTrip(original)
        val rom = firstRep(restored).rangeOfMotion

        assertEquals("left range of motion", 104.0, rom.left, 1e-9)
        assertEquals("right range of motion", 78.0, rom.right, 1e-9)
        assertTrue(
            "the two sides came back equal, which means storage reduced them",
            rom.asymmetry() > 0.0,
        )
    }

    @Test
    fun every_bilateral_measurement_keeps_both_sides_separately() {
        val original = session(
            RepetitionSetRecord(
                "squat", 0.0,
                listOf(
                    rep(
                        1,
                        rangeOfMotion = BilateralSignal(100.0, 90.0),
                        peak = BilateralSignal(170.0, 168.0),
                        trough = BilateralSignal(70.0, 78.0),
                    ),
                ),
            ),
        )

        val restored = firstRep(roundTrip(original))
        assertEquals(100.0, restored.rangeOfMotion.left, 1e-9)
        assertEquals(90.0, restored.rangeOfMotion.right, 1e-9)
        assertEquals(170.0, restored.peakValue.left, 1e-9)
        assertEquals(168.0, restored.peakValue.right, 1e-9)
        assertEquals(70.0, restored.troughValue.left, 1e-9)
        assertEquals(78.0, restored.troughValue.right, 1e-9)
    }

    // --- NaN survives -------------------------------------------------------------------

    /**
     * The alternating case. A bicep curl records one arm and `NaN` on the other;
     * if that reloads as `0.0`, every per-side average in the dashboard is
     * computed over a zero that nobody ever measured.
     */
    @Test
    fun an_unmeasured_side_reloads_as_unmeasured_and_never_as_zero() {
        val original = session(
            RepetitionSetRecord(
                "bicep_curl", 0.0,
                listOf(
                    rep(
                        1,
                        side = Side.LEFT,
                        rangeOfMotion = BilateralSignal(92.0, Double.NaN),
                        peak = BilateralSignal(168.0, Double.NaN),
                        trough = BilateralSignal(76.0, Double.NaN),
                    ),
                ),
            ),
        )

        val restored = firstRep(roundTrip(original))
        assertEquals(92.0, restored.rangeOfMotion.left, 1e-9)
        assertTrue(
            "the idle arm reloaded as " + restored.rangeOfMotion.right +
                " rather than staying unmeasured",
            restored.rangeOfMotion.right.isNaN(),
        )
        assertTrue(restored.peakValue.right.isNaN())
        assertTrue(restored.troughValue.right.isNaN())
        assertEquals(Side.LEFT, restored.attributedSide)
    }

    @Test
    fun a_nan_becomes_null_in_the_column_rather_than_a_number() {
        val stored = SessionMapping.toEntities(
            session(
                RepetitionSetRecord(
                    "row", 0.0,
                    listOf(rep(1, rangeOfMotion = BilateralSignal(80.0, Double.NaN))),
                ),
            ),
        )

        val columns = stored.sets.single().reps.single().rangeOfMotion
        assertEquals(80.0, columns.left!!, 1e-9)
        assertNull(
            "an unmeasured side was written as a number, which SQLite will read back as real",
            columns.right,
        )
    }

    /**
     * The resampled arrays carry `NaN` too - the whole non-contributing side of
     * an alternating repetition is `NaN` by construction. They go through a blob
     * rather than REAL columns precisely because IEEE-754 survives a byte copy.
     */
    @Test
    fun nan_survives_inside_the_resampled_arrays() {
        val values = DoubleArray(64) { if (it % 2 == 0) it.toDouble() else Double.NaN }
        val restored = SessionMapping.decode(SessionMapping.encode(values))

        assertEquals(64, restored.size)
        for (index in values.indices) {
            if (values[index].isNaN()) {
                assertTrue("index " + index + " lost its NaN", restored[index].isNaN())
            } else {
                assertEquals(values[index], restored[index], 0.0)
            }
        }
    }

    @Test
    fun an_unmeasured_tempo_reloads_as_unmeasured() {
        val original = session(
            RepetitionSetRecord(
                "squat", 0.0,
                listOf(rep(1, descentMs = 900.0, ascentMs = Double.NaN)),
            ),
        )

        val restored = firstRep(roundTrip(original))
        assertEquals(900.0, restored.descentMs, 1e-9)
        assertTrue(restored.ascentMs.isNaN())
    }

    // --- Structure -----------------------------------------------------------------------

    @Test
    fun a_session_round_trips_with_its_sets_in_the_order_performed() {
        val original = SessionRecord(
            sessionId = "s1",
            startedAtEpochMs = 1_700_000_000_000.0,
            utcOffsetMinutes = 330,
            sets = listOf(
                RepetitionSetRecord("squat", 1.0, listOf(rep(1), rep(2))),
                HoldSetRecord("plank", 2.0, listOf(hold(30_000.0))),
                RepetitionSetRecord("pushup", 3.0, listOf(rep(1))),
            ),
        )

        val restored = roundTrip(original)

        assertEquals("s1", restored.sessionId)
        assertEquals(330, restored.utcOffsetMinutes)
        assertEquals(original.localDay, restored.localDay)
        assertEquals(
            listOf("squat", "plank", "pushup"),
            restored.sets.map { it.exerciseId },
        )
        // Two squats, one plank, one push-up.
        assertEquals(4, restored.completedUnits)
    }

    /**
     * A hold set reloads as a hold set. The sealed split exists so a set cannot
     * carry both (P3-10), and a mapping that lost the kind would silently turn
     * every plank into an empty repetition set - and with it, the streak day it
     * earned.
     */
    @Test
    fun a_hold_set_reloads_as_a_hold_set_and_not_as_an_empty_repetition_set() {
        val restored = roundTrip(session(HoldSetRecord("plank", 0.0, listOf(hold(28_000.0)))))

        val set = restored.sets.single()
        assertTrue(
            "a hold set reloaded as " + set::class.simpleName,
            set is HoldSetRecord,
        )
        assertEquals(1, (set as HoldSetRecord).validHolds.size)
        assertEquals(28_000.0, set.holds.single().totalHoldMs, 1e-9)
        assertEquals(1, restored.completedUnits)
    }

    @Test
    fun repetition_validity_survives_so_abandoned_work_stays_abandoned() {
        val original = session(
            RepetitionSetRecord(
                "squat", 0.0,
                listOf(
                    rep(1, validity = RepValidity.VALID),
                    rep(2, validity = RepValidity.ABORTED),
                    rep(3, validity = RepValidity.INCOMPLETE),
                ),
            ),
        )

        val restored = roundTrip(original)
        val set = restored.sets.single() as RepetitionSetRecord
        assertEquals(3, set.reps.size)
        assertEquals(
            "an aborted repetition reloaded as completed work, which would inflate the streak",
            1,
            set.validReps.size,
        )
        assertEquals(1, restored.completedUnits)
    }

    @Test
    fun an_empty_session_round_trips_without_inventing_anything() {
        val restored = roundTrip(SessionRecord("empty", 0.0, 0, emptyList()))
        assertTrue(restored.sets.isEmpty())
        assertEquals(0, restored.completedUnits)
    }

    // --- Round trip, without a database ----------------------------------------------------

    /**
     * Domain to rows and back. Room's own SQL is not exercised - what this covers
     * is the conversion either side of it, which is where the loss would be.
     * Identifiers are assigned as the DAO would assign them.
     */
    private fun roundTrip(session: SessionRecord): SessionRecord {
        val stored = SessionMapping.toEntities(session)
        var nextSetId = 1L
        val sets = stored.sets.map { storedSet ->
            val setId = nextSetId++
            SetWithChildren(
                set = storedSet.set.copy(setId = setId),
                reps = storedSet.reps.map { it.copy(setId = setId) },
                holds = storedSet.holds.map { it.copy(setId = setId) },
            )
        }
        return SessionMapping.toDomain(SessionWithSets(stored.session, sets))
    }

    private fun firstRep(session: SessionRecord): RepRecord =
        (session.sets.first() as RepetitionSetRecord).reps.first()

    private fun session(vararg sets: com.kinesis.core.domain.session.SetRecord): SessionRecord =
        SessionRecord("s1", 1_700_000_000_000.0, 0, sets.toList())

    private fun rep(
        index: Int,
        validity: RepValidity = RepValidity.VALID,
        side: Side? = null,
        rangeOfMotion: BilateralSignal = BilateralSignal(95.0, 93.0),
        peak: BilateralSignal = BilateralSignal(170.0, 169.0),
        trough: BilateralSignal = BilateralSignal(75.0, 76.0),
        descentMs: Double = 1_000.0,
        ascentMs: Double = 1_100.0,
    ): RepRecord = RepRecord(
        repIndex = index,
        attributedSide = side,
        startMs = 0.0,
        endMs = 2_100.0,
        validity = validity,
        rangeOfMotion = rangeOfMotion,
        peakValue = peak,
        troughValue = trough,
        descentMs = descentMs,
        ascentMs = ascentMs,
        resampledLeft = DoubleArray(64) { it.toDouble() },
        resampledRight = DoubleArray(64) { it.toDouble() },
    )

    private fun hold(totalHoldMs: Double): HoldRecord = HoldRecord(
        startMs = 0.0,
        endMs = totalHoldMs + 1_000.0,
        validity = HoldValidity.VALID,
        totalHoldMs = totalHoldMs,
        longestUnbrokenMs = totalHoldMs,
        breakCount = 0,
        meanAbsDeviation = 4.0,
        maxAbsDeviation = 11.0,
        coverage = 1.0,
    )
}
