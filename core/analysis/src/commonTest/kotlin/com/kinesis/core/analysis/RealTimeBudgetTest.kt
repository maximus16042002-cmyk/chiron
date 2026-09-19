package com.kinesis.core.analysis

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.JointAngles
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.analysis.temporal.DoubleRingBuffer
import com.kinesis.core.analysis.temporal.RealTimeSignalPipeline
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Permanent guard test - CLAUDE.md sections 6 and 18, CONTRACTS.md section 7.
 *
 * The real-time path runs roughly thirty times a second for the length of a set,
 * and every one of its prohibitions fails silently. Nothing crashes when history
 * iteration creeps in; the app just gets slower as the set goes on, which reads
 * as a device problem rather than an architecture problem.
 *
 * The old prototype is the worked example. Its LiveRepDetector accumulated every
 * sample of the session in a list, copied the whole list on each frame, and
 * re-ran the entire filter chain and the entire rep FSM from the beginning -
 * with a comment explaining that this was fine. See DECISIONS.md P2-2. This test
 * is what stops that being rebuilt here.
 *
 * ### What "budget" means precisely
 *
 * Not "allocates nothing". Carrying left and right as a [BilateralSignal] costs
 * one small short-lived object per joint per frame, and CLAUDE.md section 3
 * requires that type, so a zero-allocation rule would contradict a stronger one.
 *
 * The invariant that actually matters is that per-frame cost is **constant** -
 * independent of how many frames have already been processed. A frame at minute
 * ten costs exactly what a frame at second one costs. That is what these tests
 * assert, and PerFrameAllocationTest in jvmTest measures the allocation side of
 * it in bytes.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class RealTimeBudgetTest {

    private val config = TemporalConfig.PROVISIONAL

    // --- The ring buffer does not grow -----------------------------------------

    @Test
    fun the_ring_buffer_never_exceeds_the_capacity_it_was_built_with() {
        val buffer = DoubleRingBuffer(capacity = 16)
        for (frame in 0 until 10_000) {
            buffer.push(timestampMs = frame * 33.0, value = frame.toDouble())
            assertTrue(
                buffer.size <= 16,
                "buffer grew to " + buffer.size + " after " + (frame + 1) + " pushes",
            )
        }
        assertEquals(16, buffer.size)
        assertEquals(16, buffer.capacity)
    }

    @Test
    fun the_ring_buffer_holds_the_newest_samples_and_discards_the_oldest() {
        val buffer = DoubleRingBuffer(capacity = 4)
        for (frame in 0 until 10) {
            buffer.push(timestampMs = frame * 33.0, value = frame.toDouble())
        }
        // After ten pushes into four slots, the buffer holds frames 6..9.
        assertEquals(4, buffer.size)
        assertEquals(6.0, buffer.valueAt(0))
        assertEquals(9.0, buffer.valueAt(3))
        assertEquals(9.0, buffer.newestValue())
        assertEquals(6.0, buffer.oldestValue())
        assertEquals(6 * 33.0, buffer.timestampAt(0))
    }

    /**
     * The cost of a push cannot depend on how many pushes came before it. This is
     * the direct negation of the prototype design, where the work per frame was
     * proportional to the session so far.
     *
     * The assertion is inside the loop, and that is deliberate. A path that walks
     * its own history is quadratic, so a version of this test that checked only
     * after fifty thousand frames would not fail - it would sit there for
     * minutes and then time out, which in CI reads as an infrastructure problem
     * rather than the architectural regression it is. Failing on the first bad
     * frame reports the frame number and finishes immediately.
     */
    @Test
    fun the_work_done_by_a_push_does_not_grow_with_the_number_of_pushes() {
        val pipeline = RealTimeSignalPipeline(config, bufferCapacity = 64)

        for (frame in 0 until 50_000) {
            pipeline.push(timestampMs = frame * 33.0, rawValue = 90.0 + sin(frame * 0.05) * 30.0)
            assertTrue(
                pipeline.lastPushSamplesVisited <= config.outlierWindowSamples,
                "on frame " + frame + " a push visited " + pipeline.lastPushSamplesVisited +
                    " samples; the outlier window is only " + config.outlierWindowSamples +
                    ", so something is walking the session history",
            )
        }
    }

    @Test
    fun the_pipeline_state_is_fixed_size_however_long_the_session_runs() {
        val pipeline = RealTimeSignalPipeline(config, bufferCapacity = 64)
        for (frame in 0 until 20_000) {
            pipeline.push(timestampMs = frame * 33.0, rawValue = 90.0)
        }
        assertEquals(64, pipeline.buffer.capacity)
        assertEquals(64, pipeline.buffer.size)
    }

    /**
     * Reset must return the pipeline to its initial state exactly. If any hidden
     * accumulation survives a reset, the two runs below diverge - which is how a
     * leaked growing structure announces itself.
     */
    @Test
    fun a_reset_pipeline_reproduces_its_first_run_exactly() {
        val pipeline = RealTimeSignalPipeline(config, bufferCapacity = 32)

        val firstRun = DoubleArray(200)
        for (frame in 0 until 200) {
            firstRun[frame] = pipeline.push(frame * 33.0, 90.0 + sin(frame * 0.07) * 25.0)
        }

        // A long, unrelated session in between.
        for (frame in 0 until 20_000) {
            pipeline.push(frame * 33.0, 40.0 + sin(frame * 0.3) * 60.0)
        }
        pipeline.reset()

        for (frame in 0 until 200) {
            val replayed = pipeline.push(frame * 33.0, 90.0 + sin(frame * 0.07) * 25.0)
            assertEquals(
                firstRun[frame],
                replayed,
                absoluteTolerance = 0.0,
                message = "frame " + frame + " differs after reset; state survived that should not",
            )
        }
    }

    /**
     * The same recent history must produce the same answer regardless of what
     * preceded it, once the filter state has settled. An implementation that
     * folded the whole session into its output would fail this.
     *
     * The tolerance is not zero because the EMA is deliberately history
     * dependent - that is what a smoother is. What it is not is *unboundedly*
     * history dependent: the influence of a sample decays geometrically, so after
     * a few hundred frames two differently-warmed pipelines agree to well within
     * measurement noise.
     */
    @Test
    fun a_settled_pipeline_forgets_how_it_was_warmed_up() {
        fun runAfterWarmUp(warmUpFrames: Int): Double {
            val pipeline = RealTimeSignalPipeline(config, bufferCapacity = 64)
            for (frame in 0 until warmUpFrames) {
                pipeline.push(frame * 33.0, if (frame % 2 == 0) 10.0 else 170.0)
            }
            var last = Double.NaN
            for (step in 0 until 400) {
                last = pipeline.push((warmUpFrames + step) * 33.0, 95.0)
            }
            return last
        }

        val shortWarmUp = runAfterWarmUp(10)
        val longWarmUp = runAfterWarmUp(10_000)
        assertEquals(shortWarmUp, longWarmUp, absoluteTolerance = 1e-9)
    }

    // --- Kinematics are per-frame only -----------------------------------------

    /**
     * Joint angles are computed from the current frame and nothing else. Passing
     * the same frame twice, at any point in a session, must give the same answer
     * - there is no state in the kinematics layer to drift.
     */
    @Test
    fun a_joint_angle_depends_only_on_the_frame_it_is_given() {
        val frame = standingFrame()
        val first = JointAngles.compute(
            frame,
            JointAngleId.KNEE,
            MeasurementPlane.IMAGE_2D,
            VisibilityPolicy.PROVISIONAL,
        )

        // Thousands of other frames in between.
        for (step in 0 until 5_000) {
            JointAngles.compute(
                squatFrame(step / 5_000.0),
                JointAngleId.KNEE,
                MeasurementPlane.IMAGE_2D,
                VisibilityPolicy.PROVISIONAL,
            )
        }

        val again = JointAngles.compute(
            frame,
            JointAngleId.KNEE,
            MeasurementPlane.IMAGE_2D,
            VisibilityPolicy.PROVISIONAL,
        )
        assertEquals(first, again)
    }

    // --- Frame builders --------------------------------------------------------

    private fun standingFrame(): Landmarks = squatFrame(0.0)

    /** [depth] 0.0 is standing, 1.0 is the bottom of a squat. */
    private fun squatFrame(depth: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = 0.95
        }
        val hipY = 0.55 + depth * 0.15
        val kneeForward = depth * 0.06
        put(LandmarkId.LEFT_SHOULDER, 0.40, 0.30)
        put(LandmarkId.RIGHT_SHOULDER, 0.60, 0.30)
        put(LandmarkId.LEFT_HIP, 0.44, hipY)
        put(LandmarkId.RIGHT_HIP, 0.56, hipY)
        put(LandmarkId.LEFT_KNEE, 0.44 + kneeForward, 0.75)
        put(LandmarkId.RIGHT_KNEE, 0.56 + kneeForward, 0.75)
        put(LandmarkId.LEFT_ANKLE, 0.44, 0.95)
        put(LandmarkId.RIGHT_ANKLE, 0.56, 0.95)
        return Landmarks.fromPacked(packed)
    }
}
