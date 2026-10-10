package com.kinesis.core.analysis

import com.kinesis.core.analysis.hold.HoldAnalyser
import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.domain.rep.HoldRecord
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `HoldAnalyser.snapshot()` - DECISIONS.md P3-72: the record `finish()` would return now, without
 * ending the hold. It is what a hold's draft holds, so a killed app keeps the hold so far.
 */
class HoldSnapshotTest {

    private val plank = ArchetypeProfiles.plank // target 180, tolerance 20

    /** A plank: settles in, holds, sags out of the band, comes back, is briefly unseen. */
    private fun values(): List<Double> =
        List(30) { 140.0 + it } + List(150) { 178.0 } + List(25) { 150.0 } +
            List(40) { 176.0 } + List(6) { Double.NaN } + List(60) { 179.0 }

    @Test
    fun a_snapshot_then_finish_at_the_same_moment_give_the_same_record() {
        val values = values()
        for (cut in listOf(10, 100, 180, 200, 240, 248, values.size)) {
            val analyser = HoldAnalyser(plank)
            values.take(cut).forEachIndexed { i, v -> analyser.step(i * 33.0, v) }
            val snapshot = analyser.snapshot()
            assertSameRecord(analyser.finish(), snapshot, "after $cut frames")
        }
    }

    /** Taking snapshots must change nothing: the hold finishes exactly as if none was taken. */
    @Test
    fun snapshots_on_every_frame_leave_the_result_untouched() {
        val plain = HoldAnalyser(plank)
        val watched = HoldAnalyser(plank)
        values().forEachIndexed { i, v ->
            plain.step(i * 33.0, v)
            watched.step(i * 33.0, v)
            watched.snapshot()
        }
        assertSameRecord(plain.finish(), watched.finish(), "after every frame snapshotted")
        assertEquals(plain.state, watched.state)
    }

    /** Mid-stretch, the stretch under way counts as unbroken so far. */
    @Test
    fun a_snapshot_mid_stretch_counts_the_stretch_so_far() {
        val analyser = HoldAnalyser(plank)
        repeat(100) { analyser.step(it * 33.0, 178.0) } // one unbroken stretch, 99 intervals long

        val snapshot = analyser.snapshot()

        assertEquals(99 * 33.0, snapshot.longestUnbrokenMs, 1e-9)
        assertEquals(99 * 33.0, snapshot.totalHoldMs, 1e-9)
        assertEquals(0, snapshot.breakCount)
    }

    /** `HoldRecord` compares by identity, so every field is compared here. `NaN` equals `NaN`. */
    private fun assertSameRecord(expected: HoldRecord, actual: HoldRecord, where: String) {
        fun fields(r: HoldRecord): List<Any> = listOf(
            r.startMs, r.endMs, r.validity, r.totalHoldMs, r.longestUnbrokenMs, r.breakCount,
            r.meanAbsDeviation, r.maxAbsDeviation, r.coverage, r.meanHeldValue, r.heldValueSd,
        )
        assertEquals(fields(expected), fields(actual), where)
    }
}
