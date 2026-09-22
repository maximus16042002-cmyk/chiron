package com.kinesis.ui.live

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.ui.theme.OverlayState
import org.junit.Assert.assertEquals
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
class LiveSessionEngineTest {

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
    private val engine = LiveSessionEngine(LiveRepetitionAnalysis(ArchetypeProfiles.squat), bridge, skeleton)
    private var clockMs = 0L

    private fun feed(knees: Double) {
        engine.onLandmarks(body(knees), clockMs)
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
        assertEquals(MovementPhase.IDLE, published.last().phase)
    }

    // --- Synthetic frames ---------------------------------------------------------------

    private fun squatSet(reps: Int): Int {
        var frames = 0
        fun emit(knees: Double) {
            feed(knees)
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

    private fun body(knees: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = 0.95
        }
        val radians = knees * PI / 180.0
        put(LandmarkId.LEFT_SHOULDER, 0.45, 0.25)
        put(LandmarkId.RIGHT_SHOULDER, 0.55, 0.25)
        put(LandmarkId.LEFT_HIP, 0.45, 0.55)
        put(LandmarkId.RIGHT_HIP, 0.55, 0.55)
        put(LandmarkId.LEFT_KNEE, 0.45, 0.72)
        put(LandmarkId.RIGHT_KNEE, 0.55, 0.72)
        put(LandmarkId.LEFT_ANKLE, 0.45 + 0.15 * sin(radians), 0.72 - 0.15 * cos(radians))
        put(LandmarkId.RIGHT_ANKLE, 0.55 + 0.15 * sin(radians), 0.72 - 0.15 * cos(radians))
        return Landmarks.fromPacked(packed)
    }
}
