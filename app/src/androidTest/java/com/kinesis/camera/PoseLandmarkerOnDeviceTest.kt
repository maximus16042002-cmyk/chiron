package com.kinesis.camera

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kinesis.MainActivity
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.pose.CameraFacing
import com.kinesis.ui.live.SkeletonOverlayView
import com.kinesis.ui.theme.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Permanent on-device guard - DECISIONS.md P3-18, the first Phase B finding.
 *
 * The first run on the Nothing Phone 2a crashed the moment the live screen
 * created MediaPipe's pose landmarker: `NoClassDefFoundError` for Google's
 * telemetry transport, which P3-13 excluded. That is a class-loading failure on
 * a real device and cannot be reproduced on the JVM, so this runs on the phone:
 * `gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class PoseLandmarkerOnDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun the_bundled_model_is_in_the_apk() {
        val model = PoseModelAsset.locate(context)
        assertTrue("expected the bundled pose model, found $model", model is PoseModelState.Present)
    }

    /** The exact path that crashed: create the landmarker, then run a frame through it. */
    @Test
    fun the_bundled_model_opens_and_runs_a_frame() {
        val model = PoseModelAsset.locate(context) as PoseModelState.Present
        val answered = CountDownLatch(1)
        val answeredAt = AtomicLong(-1L)
        val failure = AtomicReference<String?>(null)

        val opening = PoseLandmarkerSource.open(
            context, model.assetPath, CameraFacing.FRONT,
            onLandmarks = { _, timestampMs -> answeredAt.set(timestampMs); answered.countDown() },
            onNoPose = { timestampMs -> answeredAt.set(timestampMs); answered.countDown() },
            onFailure = { reason -> failure.set(reason); answered.countDown() },
        )
        val source = when (opening) {
            is PoseSourceOpening.Opened -> opening.source
            is PoseSourceOpening.Failed -> throw AssertionError("the landmarker did not open: ${opening.reason}")
        }

        try {
            // A portrait 3:4 frame with nobody in it: the model must still answer.
            val frame = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
            source.analyse(frame, 1_000L)
            assertTrue("MediaPipe never answered the frame", answered.await(20, TimeUnit.SECONDS))
            assertNull("analysis failed: ${failure.get()}", failure.get())
            assertEquals("the answer was stamped with another frame's time", 1_000L, answeredAt.get())
        } finally {
            source.close()
        }
    }

    /**
     * A model that is not there is a message, not a crash - and the message is
     * about the model. MediaPipe builds its logger before it loads the model, so
     * without the second check this would also pass with the P3-18 fault back.
     */
    @Test
    fun a_model_that_cannot_be_loaded_is_reported_not_thrown() {
        val opening = PoseLandmarkerSource.open(
            context, "no_such_model.task", CameraFacing.FRONT,
            onLandmarks = { _, _ -> }, onNoPose = { }, onFailure = { },
        )
        assertTrue("a missing model opened", opening is PoseSourceOpening.Failed)
        val reason = (opening as PoseSourceOpening.Failed).reason
        assertTrue("failed for another reason than the model: $reason", reason.startsWith("the pose model could not be started"))
    }

    /**
     * Results arrive on MediaPipe's thread and go straight to the overlay. The
     * overlay must accept them there without touching the view off the main
     * thread. The view is attached to a real window first: Android only checks
     * the calling thread (CalledFromWrongThreadException) through an attached
     * view's `ViewRootImpl`, so a detached one would pass for the wrong reason.
     *
     * What this can and cannot catch, measured on the Nothing Phone 2a (P3-18):
     * a `requestLayout()` added to `submit` fails it, and so would anything else
     * that reaches `ViewRootImpl.checkThread` - layout, visibility, size. A bare
     * `invalidate()` in place of `postInvalidateOnAnimation()` does **not**:
     * on a hardware-accelerated window Android does not check the thread for it.
     * That mutation is a race, not a crash, and this test cannot see it.
     */
    @Test
    fun the_skeleton_accepts_frames_from_a_background_thread() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var overlay: SkeletonOverlayView
            scenario.onActivity { activity ->
                overlay = SkeletonOverlayView(activity)
                activity.addContentView(
                    overlay,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
            instrumentation.waitForIdleSync()
            assertTrue("the overlay never attached to a window", overlay.isAttachedToWindow)

            val packed = DoubleArray(Landmarks.PACKED_SIZE)
            for (id in LandmarkId.entries) {
                packed[id.index * Landmarks.STRIDE] = 0.5
                packed[id.index * Landmarks.STRIDE + 1] = 0.5
                packed[id.index * Landmarks.STRIDE + 3] = 0.9
            }
            val frame = Landmarks.fromPacked(packed)

            val thrown = AtomicReference<Throwable?>(null)
            val worker = Thread {
                try {
                    repeat(60) { overlay.submit(frame, OverlayState.TRACKED, VisibilityPolicy.PROVISIONAL) }
                    overlay.clear()
                } catch (error: Throwable) {
                    thrown.set(error)
                }
            }
            worker.start()
            worker.join(10_000)
            instrumentation.waitForIdleSync()
            assertNull("the overlay rejected a background-thread frame: ${thrown.get()}", thrown.get())
        }
    }
}
