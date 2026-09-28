package com.kinesis.core.analysis.rep

import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.domain.rep.RepValidity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A repetition carries on across a short tracking gap - DECISIONS.md P3-23.
 *
 * On the phone, one unmeasurable frame - a landmark below the visibility floor,
 * a frame with nobody found, or one sample the outlier gate rejected - threw the
 * whole repetition away, and almost every repetition was lost that way. Now a
 * gap up to `maxBridgedGapMs` is crossed. What must not change: a gap never
 * counts anything by itself, and a long one still ends the repetition.
 */
class GapToleranceTest {

    private val squat = ArchetypeProfiles.squat.repSignal // top 160, bottom 90, band 5
    private val frameMs = 67.0 // about 15 a second, as measured on the phone

    /** Feeds [values] (NaN = unmeasured) one frame apart, returning every cycle completed. */
    private fun run(fsm: RepFsm, values: List<Double>): List<RepCycle> {
        val cycles = ArrayList<RepCycle>()
        values.forEachIndexed { i, v -> fsm.step(i * frameMs, v)?.let { cycles += it } }
        return cycles
    }

    private fun squatWith(gapAt: Int, gapFrames: Int): List<Double> {
        val down = (0 until 20).map { 170.0 - 4.5 * it } // 170 .. 84.5
        val up = (0 until 20).map { 80.0 + 4.5 * it }
        val movement = (List(5) { 170.0 } + down + up + List(5) { 170.0 }).toMutableList()
        for (i in gapAt until gapAt + gapFrames) movement[i] = Double.NaN
        return movement
    }

    @Test
    fun a_repetition_carries_on_across_a_short_gap() {
        val fsm = RepFsm(squat, 512, maxBridgedGapMs = 500.0)
        val cycles = run(fsm, squatWith(gapAt = 12, gapFrames = 5)) // 335 ms, mid-descent
        assertEquals(listOf(RepValidity.VALID), cycles.map { it.validity })
        assertEquals(1, fsm.completedReps)
    }

    @Test
    fun a_gap_longer_than_the_limit_still_abandons_it_ending_at_the_last_measured_frame() {
        val fsm = RepFsm(squat, 512, maxBridgedGapMs = 500.0)
        val cycles = run(fsm, squatWith(gapAt = 12, gapFrames = 10)) // 670 ms
        val first = cycles.first()
        assertEquals(RepValidity.INSUFFICIENT_DATA, first.validity)
        assertEquals(11 * frameMs, first.endMs, 1e-9)
        assertEquals(0, fsm.completedReps)
    }

    /** The bottom was never measured, so no repetition happened as far as anyone can say. */
    @Test
    fun a_gap_never_counts_a_repetition_by_itself() {
        val fsm = RepFsm(squat, 512, maxBridgedGapMs = 500.0)
        val values = List(5) { 170.0 } + (0 until 8).map { 165.0 - 5.0 * it } + List(6) { Double.NaN } + List(5) { 170.0 }
        val cycles = run(fsm, values)
        assertTrue(cycles.none { it.validity == RepValidity.VALID }, cycles.map { it.validity }.toString())
        assertEquals(0, fsm.completedReps)
    }

    @Test
    fun with_no_tolerance_the_first_unmeasured_frame_abandons_it_as_before() {
        val fsm = RepFsm(squat, 512, maxBridgedGapMs = 0.0)
        val cycles = run(fsm, squatWith(gapAt = 12, gapFrames = 1))
        assertEquals(RepValidity.INSUFFICIENT_DATA, cycles.first().validity)
    }

    /** The resampled curve crosses the gap, as it always has for gaps inside a cycle. */
    @Test
    fun a_bridged_repetitions_curve_has_no_hole() {
        val fsm = RepFsm(squat, 512, maxBridgedGapMs = 500.0)
        val cycle = assertNotNull(run(fsm, squatWith(gapAt = 12, gapFrames = 5)).firstOrNull())
        assertTrue(cycle.resampled.all { it.isFinite() })
    }

    /** At the tracker, with the app's own settings: one knee lost for a moment. */
    @Test
    fun a_squat_with_one_knee_briefly_unseen_counts_with_the_provisional_settings() {
        assertEquals(500.0, TemporalConfig.PROVISIONAL.maxBridgedGapMs)
        val tracker = BilateralRepTracker(ArchetypeProfiles.squat)
        val left = squatWith(gapAt = 12, gapFrames = 0)
        val right = squatWith(gapAt = 20, gapFrames = 4)
        val records = ArrayList<com.kinesis.core.domain.rep.RepRecord>()
        val sink = RepSink { records += it }
        for (i in left.indices) {
            tracker.onFrame(i * frameMs, kneeFrame(left[i], right[i]), com.kinesis.core.domain.landmark.VisibilityPolicy.PROVISIONAL, sink)
        }
        assertEquals(1, tracker.repetitionCount, records.map { it.validity }.toString())
        assertNull(records.firstOrNull { it.validity != RepValidity.VALID })
    }

    // --- At the start position - DECISIONS.md P3-28 --------------------------------------
    //
    // P7 found every fast curl in MM-Fit lost before it began: the outlier gate rejects the first
    // fast-moving samples - they are far from the median of the resting ones - the machine gets
    // NaN while still at TOP, and TOP used to drop to IDLE on the first unmeasured frame. IDLE then
    // waits for the arm to come back to the top, so the whole repetition vanished without a record.
    // The gap is now bridged at TOP as it already is mid-repetition. Distinct from the tests above:
    // those are a repetition already under way.

    private val curl = ArchetypeProfiles.bicepCurl.repSignal // top 145, bottom 60, band 8

    /**
     * MM-Fit's case, through the engine's real pipeline at MM-Fit's 30 frames a second: a curl that
     * leaves the rest position fast enough that the gate rejects its first moving samples.
     */
    @Test
    fun a_fast_start_whose_first_moving_frames_are_gated_still_counts() {
        val frame = 1000.0 / 30.0
        val pipeline = com.kinesis.core.analysis.temporal.RealTimeSignalPipeline(TemporalConfig.PROVISIONAL, 1024)
        val fsm = RepFsm(curl, 1024, maxBridgedGapMs = TemporalConfig.PROVISIONAL.maxBridgedGapMs)
        val rest = List(15) { 176.0 }
        val lift = listOf(176.0, 150.0, 118.0, 85.0, 55.0, 38.0) // as w00 set 22: 178 to 118 at once
        val top = List(15) { 34.0 }
        val lower = listOf(60.0, 95.0, 130.0, 160.0)
        val values = rest + lift + top + lower + rest
        val cycles = ArrayList<RepCycle>()
        values.forEachIndexed { i, v -> fsm.step(i * frame, pipeline.push(i * frame, v))?.let { cycles += it } }

        assertTrue(pipeline.rejectedSampleCount > 0, "the gate rejected nothing, so this test proves nothing")
        assertEquals(listOf(RepValidity.VALID), cycles.map { it.validity })
        assertEquals(1, fsm.completedReps)
    }

    @Test
    fun a_short_gap_at_the_start_position_is_bridged_and_the_descent_after_it_counts() {
        val fsm = RepFsm(squat, 512, maxBridgedGapMs = 500.0)
        val values = List(5) { 170.0 } + List(3) { Double.NaN } + // 200 ms at the top, unmeasured
            (0 until 20).map { 150.0 - 3.5 * it } + (0 until 20).map { 80.0 + 4.5 * it } + List(5) { 170.0 }
        val cycles = run(fsm, values)
        assertEquals(listOf(RepValidity.VALID), cycles.map { it.validity })
    }

    /** Past the limit the start position is lost as before, and a descent seen only after it is not a repetition. */
    @Test
    fun a_gap_at_the_start_position_longer_than_the_limit_still_drops_to_idle() {
        val fsm = RepFsm(squat, 512, maxBridgedGapMs = 500.0)
        val values = List(5) { 170.0 } + List(10) { Double.NaN } + // 670 ms
            (0 until 20).map { 150.0 - 3.5 * it } + (0 until 20).map { 80.0 + 4.5 * it } + List(5) { 170.0 }
        val cycles = run(fsm, values)
        assertTrue(cycles.isEmpty(), cycles.map { it.validity }.toString())
        assertEquals(0, fsm.completedReps)
    }

    @Test
    fun a_gap_at_the_start_position_never_counts_anything_by_itself() {
        val fsm = RepFsm(squat, 512, maxBridgedGapMs = 500.0)
        val values = List(5) { 170.0 } + List(4) { Double.NaN } + List(5) { 170.0 }
        assertTrue(run(fsm, values).isEmpty())
        assertEquals(0, fsm.completedReps)
    }

    /** Knees at these angles; a NaN angle makes that leg invisible, as an occluded knee is. */
    private fun kneeFrame(leftKneeDeg: Double, rightKneeDeg: Double): com.kinesis.core.domain.landmark.Landmarks {
        val packed = DoubleArray(com.kinesis.core.domain.landmark.Landmarks.PACKED_SIZE)
        fun put(id: com.kinesis.core.domain.landmark.LandmarkId, x: Double, y: Double, seen: Boolean) {
            val base = id.index * com.kinesis.core.domain.landmark.Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 3] = if (seen) 0.95 else 0.0
        }
        fun leg(hip: com.kinesis.core.domain.landmark.LandmarkId, knee: com.kinesis.core.domain.landmark.LandmarkId, ankle: com.kinesis.core.domain.landmark.LandmarkId, x: Double, deg: Double) {
            val seen = !deg.isNaN()
            val radians = (if (seen) deg else 170.0) * kotlin.math.PI / 180.0
            put(hip, x, 0.50, seen)
            put(knee, x, 0.70, seen)
            put(ankle, x + 0.20 * kotlin.math.sin(radians), 0.70 - 0.20 * kotlin.math.cos(radians), seen)
        }
        leg(com.kinesis.core.domain.landmark.LandmarkId.LEFT_HIP, com.kinesis.core.domain.landmark.LandmarkId.LEFT_KNEE, com.kinesis.core.domain.landmark.LandmarkId.LEFT_ANKLE, 0.44, leftKneeDeg)
        leg(com.kinesis.core.domain.landmark.LandmarkId.RIGHT_HIP, com.kinesis.core.domain.landmark.LandmarkId.RIGHT_KNEE, com.kinesis.core.domain.landmark.LandmarkId.RIGHT_ANKLE, 0.56, rightKneeDeg)
        return com.kinesis.core.domain.landmark.Landmarks.fromPacked(packed)
    }
}
