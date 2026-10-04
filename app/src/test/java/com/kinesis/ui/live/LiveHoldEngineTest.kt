package com.kinesis.ui.live

import com.kinesis.core.analysis.hold.HoldState
import com.kinesis.core.analysis.profile.ArchetypeProfiles
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.ui.theme.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The hold path's wiring, on the JVM with the real core analysis and the real
 * bridge - DECISIONS.md P3-16. CLAUDE.md section 6 applies to a hold timer as
 * much as to a rep count: a clock that ticks every frame must not recompose
 * every frame.
 */
class LiveHoldEngineTest {

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
    private val published = ArrayList<LiveHoldUiState>()
    private val bridge = LiveHoldBridge("plank") { published += it }
    private val engine = LiveHoldEngine(ArchetypeProfiles.plank, bridge, skeleton)
    private var clockMs = 0L

    private fun feed(hipDeg: Double, frames: Int = 1) = repeat(frames) {
        engine.onLandmarks(plank(hipDeg), clockMs)
        clockMs += 33
    }

    @Test
    fun before_any_frame_the_readout_does_not_claim_tracking() {
        assertEquals(1, published.size)
        assertEquals(OverlayState.UNTRACKED, published.single().overlayState)
        assertEquals(0, published.single().heldSeconds)
    }

    /** Thirty seconds of plank: nine hundred frames, about thirty recompositions. */
    @Test
    fun a_thirty_second_hold_recomposes_about_once_a_second_and_not_per_frame() {
        feed(178.0, frames = 900)

        assertEquals(900, skeleton.states.size)
        assertEquals(29, published.last().heldSeconds)
        assertEquals(HoldState.HOLDING, published.last().holdState)
        assertTrue(
            "published " + bridge.publishCount + " times for 900 frames",
            bridge.publishCount in 30..40,
        )
    }

    @Test
    fun a_broken_hold_turns_the_skeleton_to_the_attention_state_while_it_lasts() {
        feed(178.0, frames = 90)
        for (step in 1..30) feed(178.0 - step)
        feed(148.0, frames = 30)

        assertEquals(HoldState.DEVIATING, published.last().holdState)
        assertEquals(OverlayState.ATTENTION, skeleton.states.last())

        for (step in 1..30) feed(148.0 + step)
        feed(178.0, frames = 30)
        assertEquals(HoldState.HOLDING, published.last().holdState)
        assertEquals(OverlayState.TRACKED, skeleton.states.last())
    }

    @Test
    fun a_frame_with_no_pose_clears_the_skeleton_and_the_readout_says_so() {
        feed(178.0, frames = 60)
        engine.onNoPose(clockMs)

        assertEquals(1, skeleton.clears)
        assertEquals(FrameValidity.INVALID, published.last().frameValidity)
        assertEquals(OverlayState.UNTRACKED, published.last().overlayState)
    }

    /**
     * DECISIONS.md P3-59: the app's hold engine blocks scoring on a wrong camera view too. A
     * plank filmed from the head end is not credited, and the screen says why.
     */
    @Test
    fun a_plank_filmed_from_the_wrong_side_is_not_held_and_the_screen_says_why() {
        repeat(150) {
            engine.onLandmarks(plank(178.0, spread = 0.15), clockMs)
            clockMs += 33
        }

        assertEquals(0, published.last().heldSeconds)
        assertTrue(published.last().setupMessage!!.contains("paused"))
    }

    /**
     * A side-on plank; [hipDeg] is the shoulder-hip-knee angle on both sides. [spread] moves the
     * left side left and the right side right, as a camera at the head end would see them.
     */
    private fun plank(hipDeg: Double, spread: Double = 0.0): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = 0.95
        }
        val r = hipDeg * PI / 180.0
        val kx = 0.50 - 0.25 * cos(r)
        val ky = 0.60 + 0.25 * sin(r)
        for ((ids, dx) in listOf(
            listOf(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE) to -spread,
            listOf(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE) to spread,
        )) {
            val (shoulder, hip, knee, ankle) = ids
            put(shoulder, 0.20 + dx, 0.60)
            put(hip, 0.50 + dx, 0.60)
            put(knee, kx + dx, ky)
            put(ankle, kx + (kx - 0.50) + dx, ky + (ky - 0.60))
        }
        return Landmarks.fromPacked(packed)
    }
}
