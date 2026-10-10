package com.kinesis.core.analysis.rep

import com.kinesis.core.analysis.PairedKneeFixture
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Permanent guard - DECISIONS.md P3-19, from the squat set the P3-18 device run
 * saved: twelve records, every one numbered 0, one spanning 93.5 seconds.
 *
 * Two defects in how `BilateralRepTracker` turns per-side cycles into records,
 * each reproduced here before it was fixed:
 *
 * - `repIndex` was the count of *valid* repetitions so far, so every record
 *   before the first valid one was 0 and a rejected record repeated the number of
 *   the valid one before it. `RepRecord` documents it as the position in the set,
 *   counting from 1.
 * - A side's finished cycle waited for the other side's next cycle however late
 *   it came, and the two became one repetition spanning both. Two one-sided
 *   cycles a minute apart made one record a minute long - and, if both were
 *   valid on their own, one *counted* repetition.
 */
class RepRecordIntegrityTest {

    // The paired squat as it was until DECISIONS.md P3-68: this guard is on the paired path, which
    // the shipped squat no longer takes and the lateral raise, glute bridge and others still do.
    private val squat = PairedKneeFixture.instance
    private val frameMs = 33.0

    private class Track {
        val frames = ArrayList<Pair<Double, Landmarks>>()
        var index = 0
    }

    private fun Track.frame(leftKnee: Double, rightKnee: Double, frameMs: Double) {
        frames += (index * frameMs) to legFrame(leftKnee, rightKnee)
        index++
    }

    /** One knee's angle; [Double.NaN] makes that side unseen for the frame. */
    private fun Track.hold(frames: Int, left: Double, right: Double) = repeat(frames) { frame(left, right, frameMs) }

    /** Down to [bottom] and back to standing over 60 frames (about 2 s), each side or `NaN`. */
    private fun Track.squatTo(bottom: Double, leftSeen: Boolean = true, rightSeen: Boolean = true) {
        for (step in 1..30) {
            val angle = STANDING - (STANDING - bottom) * step / 30.0
            frame(if (leftSeen) angle else Double.NaN, if (rightSeen) angle else Double.NaN, frameMs)
        }
        for (step in 1..30) {
            val angle = bottom + (STANDING - bottom) * step / 30.0
            frame(if (leftSeen) angle else Double.NaN, if (rightSeen) angle else Double.NaN, frameMs)
        }
    }

    private fun run(track: Track, finishAtEnd: Boolean = false): Pair<List<RepRecord>, BilateralRepTracker> {
        val tracker = BilateralRepTracker(squat)
        val records = ArrayList<RepRecord>()
        val sink = RepSink { records += it }
        for ((t, frame) in track.frames) tracker.onFrame(t, frame, VisibilityPolicy.PROVISIONAL, sink)
        if (finishAtEnd) tracker.finish(track.frames.last().first + frameMs, sink)
        return records to tracker
    }

    @Test
    fun every_record_is_numbered_by_its_position_in_the_set_counting_from_1() {
        val track = Track()
        track.hold(15, STANDING, STANDING)
        track.squatTo(120.0) // turns back above depth: ABORTED
        track.hold(15, STANDING, STANDING)
        track.squatTo(70.0) // VALID
        track.hold(15, STANDING, STANDING)
        track.squatTo(70.0) // VALID
        track.hold(15, STANDING, STANDING)
        repeat(20) { track.frame(STANDING - 3.0 * it, STANDING - 3.0 * it, frameMs) } // under way when the set ends

        val (records, tracker) = run(track, finishAtEnd = true)

        assertEquals(
            listOf(RepValidity.ABORTED, RepValidity.VALID, RepValidity.VALID, RepValidity.INCOMPLETE),
            records.map { it.validity },
        )
        assertEquals(listOf(1, 2, 3, 4), records.map { it.repIndex }, "repIndex is the position in the set")
        assertEquals(2, tracker.repetitionCount, "only valid repetitions are counted")
    }

    @Test
    fun a_record_reported_for_one_side_alone_takes_the_next_position_too() {
        val track = Track()
        track.hold(15, STANDING, STANDING)
        track.squatTo(70.0, rightSeen = false) // the right knee unseen throughout
        track.hold(15, STANDING, STANDING)
        track.squatTo(70.0)
        track.hold(15, STANDING, STANDING)

        val (records, _) = run(track, finishAtEnd = true)

        assertEquals(listOf(RepValidity.INSUFFICIENT_DATA, RepValidity.VALID), records.map { it.validity })
        assertEquals(listOf(1, 2), records.map { it.repIndex })
    }

    /**
     * The phone's 93.5-second record, and worse. Each knee is seen for a different
     * repetition, a minute apart. Neither is a whole repetition; together they are
     * not one either.
     */
    @Test
    fun cycles_that_never_shared_a_moment_are_never_one_repetition() {
        val track = Track()
        track.hold(15, STANDING, STANDING)
        track.squatTo(70.0, rightSeen = false) // left only
        track.hold(1_800, STANDING, Double.NaN) // a minute; the right knee still unseen
        track.hold(15, STANDING, STANDING)
        track.squatTo(70.0, leftSeen = false) // right only
        track.hold(15, STANDING, STANDING)

        val (records, tracker) = run(track, finishAtEnd = true)

        for (record in records) {
            assertTrue(
                record.durationMs <= squat.repSignal.maxRepDurationMs,
                "a record spans " + record.durationMs + " ms, past the profile's " +
                    squat.repSignal.maxRepDurationMs + " ms limit: " + record,
            )
        }
        assertEquals(listOf(RepValidity.INSUFFICIENT_DATA, RepValidity.INSUFFICIENT_DATA), records.map { it.validity })
        assertEquals(0, tracker.repetitionCount, "two one-sided halves a minute apart were counted as a squat")
    }

    /** The ordinary case must still pair: the right knee a few frames behind the left. */
    @Test
    fun a_left_and_right_cycle_that_overlap_are_one_repetition() {
        val track = Track()
        track.hold(15, STANDING, STANDING)
        for (step in 1..30) {
            val l = STANDING - (STANDING - 70.0) * step / 30.0
            val r = STANDING - (STANDING - 70.0) * maxOf(0, step - 4) / 30.0
            track.frame(l, r, frameMs)
        }
        for (step in 1..34) {
            val l = if (step <= 30) 70.0 + (STANDING - 70.0) * step / 30.0 else STANDING
            val r = if (step <= 4) 70.0 + (STANDING - 70.0) * 0.0 else 70.0 + (STANDING - 70.0) * (step - 4) / 30.0
            track.frame(l, r, frameMs)
        }
        track.hold(15, STANDING, STANDING)

        val (records, tracker) = run(track)

        assertEquals(1, records.size, "one repetition, not " + records)
        assertEquals(RepValidity.VALID, records.single().validity)
        assertEquals(1, records.single().repIndex)
        assertEquals(1, tracker.repetitionCount)
    }

    /**
     * Places hip, knee and ankle so the knee angle is exactly the one asked for.
     * A `NaN` angle makes that leg invisible, which is how the tracker sees an
     * occluded knee.
     */
    private fun legFrame(leftKneeDeg: Double, rightKneeDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double, seen: Boolean) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = if (seen) 0.95 else 0.0
        }
        fun leg(hip: LandmarkId, knee: LandmarkId, ankle: LandmarkId, x: Double, deg: Double) {
            val seen = !deg.isNaN()
            val radians = (if (seen) deg else STANDING) * kotlin.math.PI / 180.0
            put(hip, x, 0.50, seen)
            put(knee, x, 0.70, seen)
            put(ankle, x + 0.20 * kotlin.math.sin(radians), 0.70 - 0.20 * kotlin.math.cos(radians), seen)
        }
        leg(LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE, 0.44, leftKneeDeg)
        leg(LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE, 0.56, rightKneeDeg)
        return Landmarks.fromPacked(packed)
    }

    private companion object {
        const val STANDING = 170.0
    }
}
