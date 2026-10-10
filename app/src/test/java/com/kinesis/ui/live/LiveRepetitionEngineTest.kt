package com.kinesis.ui.live

import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.ui.theme.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pose callback's wiring, on the JVM with the real core analysis and the
 * real bridge - DECISIONS.md P3-15. What it cannot cover is anything a camera,
 * a model or a device is needed for; that is Phase B.
 */
class LiveRepetitionEngineTest {

    private class RecordingSkeleton : SkeletonSink {
        val states = ArrayList<OverlayState>()
        var clears = 0
        override fun submit(landmarks: Landmarks, state: OverlayState, policy: VisibilityPolicy) {
            states += state
        }
        override fun clear() {
            clears++
        }
    }

    private val skeleton = RecordingSkeleton()
    private val published = ArrayList<LiveUiState>()
    private val bridge = LiveAnalysisBridge("squat") { published += it }
    private val engine = LiveRepetitionEngine(ArchetypeProfiles.squat, bridge, skeleton)
    private var clockMs = 0L

    private fun feed(knees: Double, halfSpan: Double = SIDE_ON) {
        engine.onLandmarks(body(knees, halfSpan), clockMs)
        clockMs += 33
    }

    @Test
    fun every_frame_reaches_the_skeleton_and_compose_hears_only_changes() {
        val frames = squatSet(reps = 3)

        assertEquals(frames, skeleton.states.size)
        assertEquals(3, published.last().repCount)
        assertTrue(
            "published " + bridge.publishCount + " times for " + frames + " frames",
            bridge.publishCount < 30,
        )
    }

    @Test
    fun the_skeleton_is_drawn_in_the_state_the_readout_shows() {
        val readoutStates = ArrayList<OverlayState>()
        repeat(10) {
            feed(170.0)
            readoutStates += bridge.state.overlayState
        }
        // A squat that turns back before depth: aborted, so attention.
        for (step in 1..15) {
            feed(170.0 - 40.0 * step / 15.0)
            readoutStates += bridge.state.overlayState
        }
        for (step in 1..15) {
            feed(130.0 + 40.0 * step / 15.0)
            readoutStates += bridge.state.overlayState
        }

        assertEquals(readoutStates, skeleton.states)
        assertEquals(OverlayState.ATTENTION, skeleton.states.last())
    }

    @Test
    fun a_frame_with_no_pose_clears_the_skeleton_and_the_readout_says_so() {
        repeat(10) { feed(170.0) }
        assertEquals(OverlayState.TRACKED, published.last().overlayState)

        engine.onNoPose(clockMs)

        assertEquals(1, skeleton.clears)
        assertEquals(FrameValidity.INVALID, published.last().frameValidity)
        assertEquals(OverlayState.UNTRACKED, published.last().overlayState)
        // DECISIONS.md P3-28: one lost frame at the starting position no longer
        // resets the phase - it is bridged, as a mid-repetition gap is - so the
        // phase holds at TOP. This line asserted IDLE until then.
        assertEquals(MovementPhase.TOP, published.last().phase)

        // Past the bridge (500 ms), the start position is lost as before.
        repeat(20) {
            clockMs += 33
            engine.onNoPose(clockMs)
        }
        assertEquals(MovementPhase.IDLE, published.last().phase)
    }

    /**
     * DECISIONS.md P3-68, replacing P3-59's blocking: the app reports a wrong camera view and
     * keeps counting. The screen says which view the exercise wants, and never that counting is
     * paused while it counts. A squat must be filmed side-on.
     */
    @Test
    fun a_squat_filmed_front_on_is_counted_and_the_screen_names_the_view() {
        squatSet(reps = 3, halfSpan = FRONT_ON)

        assertEquals(3, published.last().repCount)
        val message = published.last().setupMessage!!
        assertTrue(message, message.contains("side-on"))
        assertFalse(message, message.contains("paused"))
    }

    /**
     * A user set up correctly sways a little on every frame. P3-55's verdict carried a confidence
     * that moved with it, and the screen recomposed on every frame; the message does not move.
     */
    @Test
    fun a_correctly_set_up_user_swaying_does_not_recompose_the_screen_per_frame() {
        var frames = 0
        repeat(10) { feed(170.0); frames++ }
        repeat(3) {
            for (step in 1..30) {
                feed(170.0 - 100.0 * step / 30.0, halfSpan = 0.05 + 0.002 * (step % 5))
                frames++
            }
            for (step in 1..30) {
                feed(70.0 + 100.0 * step / 30.0, halfSpan = 0.05 + 0.002 * (step % 5))
                frames++
            }
            repeat(10) { feed(170.0); frames++ }
        }

        assertEquals(3, published.last().repCount)
        assertTrue("published " + bridge.publishCount + " times for " + frames + " frames", bridge.publishCount < 30)
    }

    // --- Synthetic frames ---------------------------------------------------------------

    private fun squatSet(reps: Int, halfSpan: Double = SIDE_ON): Int {
        var frames = 0
        fun emit(knees: Double) {
            feed(knees, halfSpan)
            frames++
        }
        repeat(10) { emit(170.0) }
        repeat(reps) {
            for (step in 1..30) emit(170.0 - 100.0 * step / 30.0)
            for (step in 1..30) emit(70.0 + 100.0 * step / 30.0)
            repeat(10) { emit(170.0) }
        }
        return frames
    }

    /** [halfSpan]: half the left-right gap across the frame; 0.05 over a 0.30 torso reads side-on. */
    private fun body(knees: Double, halfSpan: Double = SIDE_ON): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = 0.95
        }
        val radians = knees * PI / 180.0
        val left = 0.50 - halfSpan
        val right = 0.50 + halfSpan
        put(LandmarkId.LEFT_SHOULDER, left, 0.25)
        put(LandmarkId.RIGHT_SHOULDER, right, 0.25)
        put(LandmarkId.LEFT_HIP, left, 0.55)
        put(LandmarkId.RIGHT_HIP, right, 0.55)
        put(LandmarkId.LEFT_KNEE, left, 0.72)
        put(LandmarkId.RIGHT_KNEE, right, 0.72)
        put(LandmarkId.LEFT_ANKLE, left + 0.15 * sin(radians), 0.72 - 0.15 * cos(radians))
        put(LandmarkId.RIGHT_ANKLE, right + 0.15 * sin(radians), 0.72 - 0.15 * cos(radians))
        return Landmarks.fromPacked(packed)
    }

    private companion object {
        /** As drawn before P3-59: a 0.10 gap over a 0.30 torso, which reads side-on. */
        const val SIDE_ON = 0.05
        const val FRONT_ON = 0.12
    }
}
