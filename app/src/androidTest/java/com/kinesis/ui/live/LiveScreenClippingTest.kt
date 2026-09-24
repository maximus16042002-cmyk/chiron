package com.kinesis.ui.live

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import androidx.activity.compose.setContent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kinesis.MainActivity
import com.kinesis.core.analysis.hold.HoldState
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.ui.screens.LiveAnalysisScreen
import com.kinesis.ui.screens.LiveHoldScreen
import com.kinesis.ui.theme.KinesisTheme
import com.kinesis.ui.theme.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Permanent guard - DECISIONS.md P3-19, from the P3-18 device session.
 *
 * On the phone, landmarks MediaPipe placed below the frame were drawn over the
 * rep counter and the END SET control: the camera box did not clip the overlay.
 * This renders each live screen on the device, gives the overlay a figure whose
 * shoulders are in the frame and whose hips and legs are below it, and compares
 * the window's pixels before and after. Something must change inside the camera
 * box - otherwise the test could not see the skeleton at all - and nothing may
 * change outside it.
 *
 * Captured with `PixelCopy` from the window's real, hardware-rendered surface:
 * the spill was seen on the device, so it is measured the way the device draws.
 */
@RunWith(AndroidJUnit4::class)
class LiveScreenClippingTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun the_repetition_screen_draws_nothing_outside_the_camera_area() = assertClipped { preview, overlay ->
        LiveAnalysisScreen(
            LiveUiState("squat", repCount = 0, MovementPhase.TOP, FrameValidity.VALID, lastRepValidity = null),
            preview, overlay, onEndSet = {}, switchCameraLabel = "BACK CAMERA", onSwitchCamera = {},
            diagnostics = "PHASE B  0.0 fps",
        )
    }

    @Test
    fun the_hold_screen_draws_nothing_outside_the_camera_area() = assertClipped { preview, overlay ->
        LiveHoldScreen(
            LiveHoldUiState("plank", heldSeconds = 0, HoldState.NOT_STARTED, FrameValidity.VALID),
            preview, overlay, onEndSet = {}, switchCameraLabel = "BACK CAMERA", onSwitchCamera = {},
            diagnostics = "PHASE B  0.0 fps",
        )
    }

    private fun assertClipped(screen: @Composable (PreviewView, SkeletonOverlayView) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            lateinit var overlay: SkeletonOverlayView
            lateinit var preview: PreviewView
            scenario.onActivity { launched ->
                activity = launched
                // The phone may be locked while tests run; draw over the lock screen.
                launched.setShowWhenLocked(true)
                launched.setTurnScreenOn(true)
                // The live screen runs in portrait; so does this, however the phone lies.
                launched.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                // As LiveHost builds it: a TextureView, never cropped.
                preview = PreviewView(launched).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
                overlay = SkeletonOverlayView(launched)
                // Hosted as MainActivity hosts it: theme, surface, the shell's
                // column under a nav-bar-high strip, and the debug readout on.
                launched.setContent {
                    KinesisTheme {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            Column(Modifier.fillMaxSize()) {
                                Spacer(Modifier.height(64.dp))
                                screen(preview, overlay)
                            }
                        }
                    }
                }
            }
            settle()
            settle()
            val before = capture(activity)

            overlay.submit(figureLeavingTheFrame(), OverlayState.TRACKED, VisibilityPolicy.PROVISIONAL)
            settle()
            val after = capture(activity)

            val location = IntArray(2)
            scenario.onActivity { overlay.getLocationInWindow(location) }
            val box = Rect(location[0], location[1], location[0] + overlay.width, location[1] + overlay.height)
            assertTrue("the camera box has no size: $box", box.width() > 0 && box.height() > 0)

            var inside = 0
            var outside = 0
            val guard = Rect(box).apply { inset(-EDGE_PX, -EDGE_PX) }
            for (y in 0 until after.height) {
                for (x in 0 until after.width) {
                    if (before.getPixel(x, y) == after.getPixel(x, y)) continue
                    when {
                        box.contains(x, y) -> inside++
                        !guard.contains(x, y) -> outside++
                    }
                }
            }
            assertTrue("nothing was drawn inside the camera box, so this test cannot see the skeleton", inside > 0)
            assertEquals("pixels drawn outside the camera box $box", 0, outside)
        }
    }

    /**
     * Shoulders and arms inside the frame; hips, knees and feet below its bottom
     * edge. The overlay measures y in frame *widths* (P3-16), so a portrait 4:3
     * frame ends at y = 4/3, not 1 - a first version of this test put the legs at
     * 1.3, still inside, and passed without the fix.
     */
    private fun figureLeavingTheFrame(): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        for (id in LandmarkId.entries) {
            val lower = LOWER_BODY.any { id.name.contains(it) }
            val left = id.name.startsWith("LEFT")
            packed[id.index * Landmarks.STRIDE] = if (left) 0.40 else 0.60
            packed[id.index * Landmarks.STRIDE + 1] = if (lower) LEGS_Y else 0.70
            packed[id.index * Landmarks.STRIDE + 3] = 0.95
        }
        return Landmarks.fromPacked(packed)
    }

    private fun settle() {
        instrumentation.waitForIdleSync()
        // Idle on the main thread is not "drawn": give the render thread its frames.
        Thread.sleep(SETTLE_MS)
        instrumentation.waitForIdleSync()
    }

    private fun capture(activity: MainActivity): Bitmap {
        val window = activity.window
        val decor = window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val thread = HandlerThread("pixel-copy").apply { start() }
        try {
            val done = CountDownLatch(1)
            var result = -1
            PixelCopy.request(window, bitmap, { code -> result = code; done.countDown() }, Handler(thread.looper))
            assertTrue("PixelCopy never answered", done.await(5, TimeUnit.SECONDS))
            assertEquals("PixelCopy failed", PixelCopy.SUCCESS, result)
        } finally {
            thread.quitSafely()
        }
        return bitmap
    }

    private companion object {
        /** Well below the frame's bottom edge at 4/3, and still on the screen. */
        const val LEGS_Y = 1.60

        val LOWER_BODY = listOf("HIP", "KNEE", "ANKLE", "HEEL", "FOOT")

        /** Anti-aliasing at the box's own edge is not a spill. */
        const val EDGE_PX = 2

        const val SETTLE_MS = 600L
    }
}
