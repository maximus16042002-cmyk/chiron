package com.kinesis.ui.live

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.ui.theme.OverlayState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * The angle arc and the rep flash, drawn by the real overlay on the device - DECISIONS.md P3-76.
 *
 * The JVM tests pin the arc's geometry and the flash's curve; this checks the View actually draws
 * them, and that a side the core did not measure draws nothing extra. It also writes each picture
 * to the app's external files (`p3-76/`) so they can be looked at; nothing else is touched.
 *
 * Run with `adb shell am instrument`, never `connectedDebugAndroidTest`, which uninstalls the app
 * and its data afterwards (P3-23).
 */
@RunWith(AndroidJUnit4::class)
class SkeletonOverlayRenderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val policy = VisibilityPolicy.PROVISIONAL

    @Test
    fun the_measured_side_gets_an_arc_and_an_unmeasured_side_gets_nothing() {
        val curl = curlFrontOn(leftElbow = 70.0, rightElbow = 150.0)

        val plain = render(JointAngleId.ELBOW) { submit(curl, OverlayState.TRACKED, policy) }
        val unmeasured = render(JointAngleId.ELBOW) { submit(curl, OverlayState.TRACKED, policy, Double.NaN, Double.NaN) }
        val oneSide = render(JointAngleId.ELBOW) { submit(curl, OverlayState.TRACKED, policy, 70.0, Double.NaN) }
        val bothSides = render(JointAngleId.ELBOW) { submit(curl, OverlayState.TRACKED, policy, 70.0, 150.0) }

        assertTrue("an unmeasured frame drew something extra", plain.sameAs(unmeasured))
        assertFalse("a measured side drew no arc", plain.sameAs(oneSide))
        assertFalse("the second measured side drew no arc", oneSide.sameAs(bothSides))
        save("curl-front-on.png", bothSides)
        save("curl-plain.png", plain)

        val squat = squatSideOn(nearKnee = 95.0)
        save("squat-side-on.png", render(JointAngleId.KNEE) { submit(squat, OverlayState.TRACKED, policy, 95.0, Double.NaN) })
    }

    @Test
    fun a_counted_repetition_flashes_the_figure() {
        val curl = curlFrontOn(leftElbow = 40.0, rightElbow = 160.0)
        val still = render(JointAngleId.ELBOW) { submit(curl, OverlayState.TRACKED, policy, 40.0, 160.0) }
        val flashing = render(JointAngleId.ELBOW) {
            submit(curl, OverlayState.TRACKED, policy, 40.0, 160.0)
            repetitionCounted()
        }

        assertFalse("a counted repetition changed nothing", still.sameAs(flashing))
        save("curl-flash.png", flashing)
    }

    // --- Rendering -------------------------------------------------------------------------

    private fun render(signal: JointAngleId, frame: SkeletonOverlayView.() -> Unit): Bitmap {
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            val view = SkeletonOverlayView(context)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, WIDTH, HEIGHT)
            view.measuring(signal)
            view.frame()
            bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(46, 52, 58)) // something like a dim room behind the figure
            view.draw(canvas)
        }
        return bitmap
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File(context.getExternalFilesDir(null), "p3-76").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // --- Synthetic figures, in frame widths on both axes (P3-16) -----------------------------

    private class Figure {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)

        fun put(id: LandmarkId, x: Double, y: Double, visibility: Double = 0.95) {
            packed[id.index * Landmarks.STRIDE] = x
            packed[id.index * Landmarks.STRIDE + 1] = y
            packed[id.index * Landmarks.STRIDE + 3] = visibility
        }

        /** [distal] placed [length] from [vertex], [degrees] round from the bone toward [proximal]. */
        fun bend(proximal: LandmarkId, vertex: LandmarkId, distal: LandmarkId, degrees: Double, length: Double, clockwise: Boolean, visibility: Double = 0.95) {
            val px = packed[proximal.index * Landmarks.STRIDE]
            val py = packed[proximal.index * Landmarks.STRIDE + 1]
            val vx = packed[vertex.index * Landmarks.STRIDE]
            val vy = packed[vertex.index * Landmarks.STRIDE + 1]
            val base = Math.atan2(py - vy, px - vx)
            val turn = Math.toRadians(degrees) * if (clockwise) 1 else -1
            put(distal, vx + length * cos(base + turn), vy + length * sin(base + turn), visibility)
        }

        fun landmarks(): Landmarks = Landmarks.fromPacked(packed)
    }

    private fun curlFrontOn(leftElbow: Double, rightElbow: Double): Landmarks = Figure().apply {
        put(LandmarkId.NOSE, 0.50, 0.30)
        put(LandmarkId.LEFT_SHOULDER, 0.62, 0.45)
        put(LandmarkId.RIGHT_SHOULDER, 0.38, 0.45)
        put(LandmarkId.LEFT_ELBOW, 0.65, 0.64)
        put(LandmarkId.RIGHT_ELBOW, 0.35, 0.64)
        bend(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST, leftElbow, 0.17, clockwise = true)
        bend(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST, rightElbow, 0.17, clockwise = false)
        put(LandmarkId.LEFT_HIP, 0.58, 0.82)
        put(LandmarkId.RIGHT_HIP, 0.42, 0.82)
        put(LandmarkId.LEFT_KNEE, 0.59, 1.04)
        put(LandmarkId.RIGHT_KNEE, 0.41, 1.04)
        put(LandmarkId.LEFT_ANKLE, 0.59, 1.25)
        put(LandmarkId.RIGHT_ANKLE, 0.41, 1.25)
    }.landmarks()

    /** Facing right, left side nearest the camera; the far leg barely seen. */
    private fun squatSideOn(nearKnee: Double): Landmarks = Figure().apply {
        put(LandmarkId.NOSE, 0.50, 0.42)
        put(LandmarkId.LEFT_SHOULDER, 0.46, 0.55)
        put(LandmarkId.RIGHT_SHOULDER, 0.47, 0.55, visibility = 0.4)
        // The far arm, hidden behind the body: MediaPipe still places every landmark somewhere.
        put(LandmarkId.RIGHT_ELBOW, 0.55, 0.67, visibility = 0.05)
        put(LandmarkId.RIGHT_WRIST, 0.60, 0.80, visibility = 0.05)
        put(LandmarkId.LEFT_ELBOW, 0.56, 0.66)
        bend(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST, 160.0, 0.14, clockwise = false)
        put(LandmarkId.LEFT_HIP, 0.36, 0.86)
        put(LandmarkId.RIGHT_HIP, 0.37, 0.86, visibility = 0.4)
        put(LandmarkId.LEFT_KNEE, 0.58, 0.94)
        bend(LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE, nearKnee, 0.26, clockwise = false)
        put(LandmarkId.RIGHT_KNEE, 0.58, 0.95, visibility = 0.05)
        put(LandmarkId.RIGHT_ANKLE, 0.50, 1.20, visibility = 0.05)
    }.landmarks()

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1440
    }
}
