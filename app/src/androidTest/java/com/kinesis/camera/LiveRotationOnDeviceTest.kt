package com.kinesis.camera

import android.Manifest
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kinesis.MainActivity
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.diagnostics.PhaseBProbe
import com.kinesis.pose.CameraFacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Permanent on-device guard - DECISIONS.md P3-19, the upside-down phone.
 *
 * Two halves of one claim. The bitmap the pose model sees is turned exactly as
 * [FrameUpright.uprightPoint] specifies, for every quarter turn, mirrored and not.
 * And on this phone's real cameras, turning the display upside down changes each
 * camera's reported rotation by exactly 180 degrees, without rebinding - so the
 * frames that follow come out upright for the turned display.
 *
 * The camera half opens both cameras. It needs nobody in front of them: it reads
 * only the rotation each frame reports, and keeps no image.
 */
@RunWith(AndroidJUnit4::class)
class LiveRotationOnDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun the_bitmap_is_turned_and_mirrored_as_specified_at_every_rotation() {
        val u = 0.2
        val v = 0.25
        for (rotation in listOf(0, 90, 180, 270)) for (mirror in listOf(false, true)) {
            val raw = markedFrame(u, v)
            val out = FrameUpright.upright(raw, rotation, mirror)
            val quarter = rotation % 180 == 90
            assertEquals("width at $rotation", if (quarter) HEIGHT else WIDTH, out.width)
            assertEquals("height at $rotation", if (quarter) WIDTH else HEIGHT, out.height)

            val (x, y) = markerCentre(out)
            val (ex, ey) = FrameUpright.uprightPoint(u, v, rotation, mirror)
            val label = "rotation $rotation, mirror $mirror"
            assertEquals("$label: x", ex, x, 2.0 / out.width)
            assertEquals("$label: y", ey, y, 2.0 / out.height)
        }
    }

    @Test
    fun turning_the_display_upside_down_turns_each_cameras_frames_half_a_circle() {
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName, Manifest.permission.CAMERA,
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.setShowWhenLocked(true)
                it.setTurnScreenOn(true)
            }
            instrumentation.waitForIdleSync()
            val provider = ProcessCameraProvider.getInstance(instrumentation.targetContext).get(10, TimeUnit.SECONDS)
            val executor = Executors.newSingleThreadExecutor()
            try {
                for ((name, selector) in listOf(
                    "BACK" to CameraSelector.DEFAULT_BACK_CAMERA,
                    "FRONT" to CameraSelector.DEFAULT_FRONT_CAMERA,
                )) {
                    val seen = AtomicInteger(-1)
                    val analysis = ImageAnalysis.Builder()
                        .setTargetRotation(Surface.ROTATION_0)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { image ->
                        seen.set(image.imageInfo.rotationDegrees)
                        image.close()
                    }
                    scenario.onActivity { provider.bindToLifecycle(it, selector, analysis) }

                    val upright = awaitRotation(seen) { it >= 0 }
                    // The display turning upside down, as LiveCamera applies it: no rebinding.
                    scenario.onActivity { analysis.targetRotation = Surface.ROTATION_180 }
                    val upsideDown = awaitRotation(seen) { it >= 0 && it != upright }
                    scenario.onActivity { provider.unbindAll() }

                    Log.i(TAG, "$name camera: rotationDegrees $upright upright, $upsideDown upside down")
                    assertEquals(
                        "$name camera: upright $upright, upside down $upsideDown",
                        180, Math.floorMod(upsideDown - upright, 360),
                    )
                }
            } finally {
                scenario.onActivity { provider.unbindAll() }
                executor.shutdown()
            }
        }
    }

    /**
     * The whole path, on a real display: the live camera bound, the activity asked
     * for reverse portrait - as the tilt listener asks when the phone is upside
     * down - and both of CameraX's target rotations must follow through the display
     * listener, then follow back. It also measures, on this phone, that an explicit
     * reverse-portrait request is honoured where sensor portrait is not.
     */
    @Test
    fun the_live_camera_follows_the_display_upside_down_and_back() {
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName, Manifest.permission.CAMERA,
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var camera: LiveCamera
            var failure: String? = null
            // The P3-19 readout's hooks, counted: they must fire on a real camera.
            val cameraFrames = AtomicInteger()
            val submitted = AtomicInteger()
            val answered = AtomicInteger()
            val probe = object : PhaseBProbe {
                override fun onFrame(timestampMs: Long, landmarks: Landmarks, leftAngle: Double, rightAngle: Double) = Unit
                override fun onNoPose(timestampMs: Long) = Unit
                override fun onCameraFrame(timestampMs: Long) { cameraFrames.incrementAndGet() }
                override fun onSubmitted(timestampMs: Long, convertNanos: Long, handOverNanos: Long) {
                    if (convertNanos > 0 && handOverNanos > 0) submitted.incrementAndGet()
                }
                override fun onAnswered(latencyNanos: Long) {
                    if (latencyNanos > 0) answered.incrementAndGet()
                }
            }
            scenario.onActivity { activity ->
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val preview = PreviewView(activity)
                activity.setContentView(preview)
                val model = PoseModelAsset.locate(activity) as PoseModelState.Present
                val opening = PoseLandmarkerSource.open(
                    activity, model.assetPath, CameraFacing.BACK,
                    onLandmarks = { _, _ -> }, onNoPose = { }, onFailure = { failure = it }, probe = probe,
                )
                camera = LiveCamera(activity, (opening as PoseSourceOpening.Opened).source, probe) { failure = it }
                camera.bind(activity, preview, activity.display!!.rotation)
            }
            try {
                awaitOnMain(scenario, "the camera bound upright") { camera.targetRotations == UPRIGHT }
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT }
                awaitOnMain(scenario, "both use cases turned upside down") {
                    camera.targetRotations == UPSIDE_DOWN && it.display!!.rotation == Surface.ROTATION_180
                }
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                awaitOnMain(scenario, "both use cases turned back upright") { camera.targetRotations == UPRIGHT }
                assertEquals("the live camera reported a failure", null, failure)
                // Frames take a moment to start flowing after binding; wait for an answer.
                awaitOnMain(scenario, "a camera frame answered by MediaPipe") { answered.get() > 0 }
                assertTrue("no camera frame reached the probe", cameraFrames.get() > 0)
                assertTrue("no frame was reported sent", submitted.get() > 0)
                assertTrue("no MediaPipe answer was timed", answered.get() > 0)
                Log.i(TAG, "live camera: ${cameraFrames.get()} camera frames, ${submitted.get()} sent, ${answered.get()} answered")
            } finally {
                scenario.onActivity { camera.close() }
            }
        }
    }

    private fun awaitOnMain(
        scenario: ActivityScenario<MainActivity>,
        what: String,
        condition: (MainActivity) -> Boolean,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            var met = false
            scenario.onActivity { met = condition(it) }
            if (met) return
            Thread.sleep(50)
        }
        throw AssertionError("not within 10 s: $what")
    }

    private fun awaitRotation(seen: AtomicInteger, accept: (Int) -> Boolean): Int {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val value = seen.get()
            if (accept(value)) return value
            Thread.sleep(20)
        }
        throw AssertionError("no camera frame with the expected rotation within 10 s (last seen ${seen.get()})")
    }

    /** A black 640x480 frame with a red square centred at (u, v). */
    private fun markedFrame(u: Double, v: Double): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val cx = (u * WIDTH).toFloat()
        val cy = (v * HEIGHT).toFloat()
        canvas.drawRect(cx - 16f, cy - 16f, cx + 16f, cy + 16f, Paint().apply { color = Color.RED })
        return bitmap
    }

    private fun markerCentre(bitmap: Bitmap): Pair<Double, Double> {
        var sx = 0.0
        var sy = 0.0
        var n = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val p = bitmap.getPixel(x, y)
            if (Color.red(p) > 200 && Color.green(p) < 60 && Color.blue(p) < 60) {
                sx += x + 0.5
                sy += y + 0.5
                n++
            }
        }
        assertTrue("the marker vanished", n > 0)
        return sx / n / bitmap.width to sy / n / bitmap.height
    }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 480
        const val TAG = "KinesisRotation"
        val UPRIGHT = Surface.ROTATION_0 to Surface.ROTATION_0
        val UPSIDE_DOWN = Surface.ROTATION_180 to Surface.ROTATION_180
    }
}
