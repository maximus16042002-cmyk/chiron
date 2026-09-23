package com.kinesis.core.analysis

import com.kinesis.core.analysis.landmark.LandmarkNormalizer
import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.analysis.temporal.DoubleRingBuffer
import com.kinesis.core.analysis.temporal.RealTimeSignalPipeline
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import java.lang.management.ManagementFactory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Permanent guard test - CLAUDE.md sections 6 and 18.
 *
 * RealTimeBudgetTest proves the real-time path does no work proportional to the
 * session. This one measures the allocation side of the same contract, in bytes,
 * using the JVM's own per-thread allocation counter. JVM only, because that
 * counter exists only there - but what it measures, the shape of the code, is
 * identical on every target.
 *
 * The assertion is that per-frame allocation is **constant**, not zero. Carrying
 * left and right as a BilateralSignal costs one small object per joint per frame
 * and CLAUDE.md section 3 requires that type. A growing per-frame figure is the
 * defect; a small steady one is the design.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class PerFrameAllocationTest {

    private val config = TemporalConfig.PROVISIONAL

    @Test
    fun a_ring_buffer_push_allocates_nothing_at_all() {
        val buffer = DoubleRingBuffer(capacity = 64)
        // The buffer owns two preallocated DoubleArrays and writes into them by
        // index. Nothing about a push should reach the allocator.
        val bytes = allocatedBytesDuring(iterations = SAMPLE_FRAMES) { frame ->
            buffer.push(timestampMs = frame * 33.0, value = frame.toDouble())
        } ?: return

        assertEquals(
            0L,
            bytes,
            "a ring buffer push allocated " + bytes + " bytes over " + SAMPLE_FRAMES + " frames",
        )
    }

    @Test
    fun per_frame_allocation_does_not_grow_as_the_session_runs() {
        val pipeline = RealTimeSignalPipeline(config, bufferCapacity = 64)

        // Warm up so JIT compilation and first-call costs land outside the
        // measurement, then measure two equal windows far apart in the session.
        repeat(100_000) { frame -> pipeline.push(frame * 33.0, signalAt(frame)) }

        val early = allocatedBytesDuring(iterations = SAMPLE_FRAMES) { step ->
            pipeline.push((100_000 + step) * 33.0, signalAt(100_000 + step))
        } ?: return

        repeat(100_000) { frame -> pipeline.push((200_000 + frame) * 33.0, signalAt(200_000 + frame)) }

        val late = allocatedBytesDuring(iterations = SAMPLE_FRAMES) { step ->
            pipeline.push((400_000 + step) * 33.0, signalAt(400_000 + step))
        } ?: return

        assertTrue(
            late <= early + TOLERANCE_BYTES,
            "per-frame allocation grew over the session: " + early + " bytes early, " +
                late + " bytes late, over " + SAMPLE_FRAMES + " frames each. " +
                "Something in the real-time path is accumulating.",
        )
    }

    @Test
    fun the_real_time_pipeline_stays_within_a_fixed_per_frame_budget() {
        val pipeline = RealTimeSignalPipeline(config, bufferCapacity = 64)
        repeat(100_000) { frame -> pipeline.push(frame * 33.0, signalAt(frame)) }

        val bytes = allocatedBytesDuring(iterations = SAMPLE_FRAMES) { step ->
            pipeline.push((100_000 + step) * 33.0, signalAt(100_000 + step))
        } ?: return

        val perFrame = bytes.toDouble() / SAMPLE_FRAMES
        assertTrue(
            perFrame <= MAX_BYTES_PER_FRAME,
            "the real-time pipeline allocated " + perFrame + " bytes per frame, over a budget of " +
                MAX_BYTES_PER_FRAME + ". The outlier gate and the smoother are meant to work in " +
                "preallocated storage.",
        )
    }

    /**
     * The per-frame torso check reads eight numbers and returns a Boolean. It
     * replaced a call to `normalize` whose output was thrown away, and exists
     * only to allocate nothing - DECISIONS.md P3-16.
     */
    @Test
    fun the_torso_check_allocates_nothing_at_all() {
        val frame = squatFrameAt(0)
        var seen = 0
        val bytes = allocatedBytesDuring(iterations = SAMPLE_FRAMES) {
            if (LandmarkNormalizer.hasMeasurableTorso(frame)) seen++
        } ?: return

        assertTrue(seen > 0, "the synthetic frame has no torso, so the check proved nothing")
        assertEquals(0L, bytes, "the torso check allocated " + bytes + " bytes over " + SAMPLE_FRAMES + " frames")
    }

    /**
     * The live entry point - torso check, validity, both trackers - DECISIONS.md
     * P3-15. It allocates per frame (each side's angle is a `BilateralSignal`),
     * so the assertion is the same one as above: constant, not growing, however
     * long the set runs.
     */
    @Test
    fun live_repetition_analysis_allocation_does_not_grow_as_the_session_runs() {
        val analysis = LiveRepetitionAnalysis(ArchetypeProfiles.squat)
        val cycle = Array(SQUAT_CYCLE_FRAMES) { squatFrameAt(it) }
        // One frame counter for the whole run, so time only ever moves forward -
        // including across the harness's untimed and timed passes, which used to
        // replay the same frame numbers and send the clock back (P3-17).
        var frame = 0
        val feedNext = FrameBody {
            analysis.onFrame(frame * 33.0, cycle[frame % SQUAT_CYCLE_FRAMES])
            frame++
        }

        // Each point is the smallest of three consecutive windows. A single window
        // can read high when the JIT stops eliminating an allocation it was
        // eliminating before; that is noise, and it flaked this test once in a
        // full-suite run (DECISIONS.md P3-17). Growth - the defect - raises every
        // window, so the minimum still catches it, at the same tolerance.
        // A long warm-up, so both measured points see the JIT's settled state. Run
        // after other test classes in the same JVM, a shorter one measured the
        // early window before a recompilation stopped eliminating one ~16-byte
        // allocation per frame, and read that as growth; in isolation the same
        // test passed. A settled JIT is the fair comparison, and real growth still
        // shows as a later window allocating more (P3-17).
        repeat(600_000) { feedNext.run(it) }
        val early = smallestOfThreeWindows(feedNext) ?: return

        repeat(200_000) { feedNext.run(it) }
        val late = smallestOfThreeWindows(feedNext) ?: return

        assertTrue(analysis.repCount > 0, "the synthetic squat cycle never completed a repetition")
        assertTrue(
            late <= early + TOLERANCE_BYTES,
            "live analysis allocation grew over the session: " + early + " bytes early, " +
                late + " bytes late, over " + SAMPLE_FRAMES + " frames each.",
        )
    }

    // --- Measurement -----------------------------------------------------------

    /**
     * Bytes allocated over [SAMPLE_FRAMES] frames, taken as the smallest of three
     * consecutive windows. Every window, and its untimed pass, is a multiple of
     * [SQUAT_CYCLE_FRAMES] long, so every window sees the same cycle phases.
     */
    private fun smallestOfThreeWindows(body: FrameBody): Long? {
        var smallest = Long.MAX_VALUE
        repeat(3) {
            val bytes = allocatedBytesDuring(iterations = SAMPLE_FRAMES, body = body) ?: return null
            if (bytes < smallest) smallest = bytes
        }
        return smallest
    }

    private fun signalAt(frame: Int): Double = 90.0 + sin(frame * 0.05) * 30.0

    /** One squat per [SQUAT_CYCLE_FRAMES]: standing, down to 70 degrees, back up. */
    private fun squatFrameAt(index: Int): Landmarks {
        val phase = index.toDouble() / SQUAT_CYCLE_FRAMES
        val knee = 120.0 + 50.0 * cos(phase * 2.0 * PI)
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = 0.95
        }
        for ((side, x) in listOf(0 to 0.45, 1 to 0.55)) {
            put(if (side == 0) LandmarkId.LEFT_SHOULDER else LandmarkId.RIGHT_SHOULDER, x, 0.25)
            put(if (side == 0) LandmarkId.LEFT_HIP else LandmarkId.RIGHT_HIP, x, 0.55)
            put(if (side == 0) LandmarkId.LEFT_KNEE else LandmarkId.RIGHT_KNEE, x, 0.72)
            val radians = knee * PI / 180.0
            put(
                if (side == 0) LandmarkId.LEFT_ANKLE else LandmarkId.RIGHT_ANKLE,
                x + 0.15 * sin(radians),
                0.72 - 0.15 * cos(radians),
            )
        }
        return Landmarks.fromPacked(packed)
    }

    /**
     * The measured body.
     *
     * A `fun interface` taking a primitive `Int`, not a `(Int) -> Unit` lambda.
     * A Kotlin function type compiles to `Function1<Integer, Unit>`, so every
     * call boxes the loop counter - sixteen bytes per iteration of pure
     * measurement apparatus, which is more than the code under test allocates
     * and would make a zero-allocation assertion impossible to pass. The SAM
     * method here takes `int` and boxes nothing.
     */
    private fun interface FrameBody {
        fun run(step: Int)
    }

    /**
     * Bytes allocated by this thread while running [body] that many times, or
     * `null` if the JVM does not expose the counter - in which case the calling
     * test returns rather than asserting on a number it could not measure.
     */
    private fun allocatedBytesDuring(iterations: Int, body: FrameBody): Long? {
        val threadBean = ManagementFactory.getThreadMXBean()
        if (threadBean !is com.sun.management.ThreadMXBean) return null
        if (!threadBean.isThreadAllocatedMemorySupported) return null
        if (!threadBean.isThreadAllocatedMemoryEnabled) threadBean.isThreadAllocatedMemoryEnabled = true

        val threadId = Thread.currentThread().threadId()

        // One untimed pass so nothing in the measured window is a first call.
        for (step in 0 until iterations) body.run(step)

        val before = threadBean.getThreadAllocatedBytes(threadId)
        for (step in 0 until iterations) body.run(step)
        val after = threadBean.getThreadAllocatedBytes(threadId)

        return (after - before).coerceAtLeast(0L)
    }

    private companion object {
        const val SAMPLE_FRAMES = 50_000

        /**
         * Generous, because the figure that matters is whether it grows, not its
         * exact value. A push carries a timestamp and a value through a fixed
         * window and two preallocated arrays; anything approaching this bound
         * means something is building objects per frame.
         */
        const val MAX_BYTES_PER_FRAME = 64.0

        /** Absorbs measurement noise from the counter itself. */
        const val TOLERANCE_BYTES = 64L * SAMPLE_FRAMES / 8

        /** Divides the measured windows' offsets, so both see the same cycle phases. */
        const val SQUAT_CYCLE_FRAMES = 100
    }
}
