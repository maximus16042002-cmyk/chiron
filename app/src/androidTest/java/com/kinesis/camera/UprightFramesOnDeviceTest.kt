package com.kinesis.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Permanent on-device guard - DECISIONS.md P3-19. [UprightFrames] replaced two
 * 1.2 MB allocations per frame on the promise of changing nothing MediaPipe sees.
 * These hold it to that promise.
 */
@RunWith(AndroidJUnit4::class)
class UprightFramesOnDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The kept buffers produce exactly the allocating path's pixels, at every
     * rotation, mirrored and not - and still do on the frame after, once the
     * buffers hold the previous frame.
     */
    @Test
    fun kept_buffers_give_exactly_the_allocating_paths_pixels() {
        val frames = UprightFrames()
        try {
            for (rotation in listOf(0, 90, 180, 270)) for (mirror in listOf(false, true)) {
                for (seed in listOf(1, 2)) {
                    val frame = SyntheticRgbaFrame(640, 480, seed = rotation + seed * 7 + (if (mirror) 3 else 0))
                    val expected = FrameUpright.upright(frame.toBitmap(), rotation, mirror)
                    val actual = frames.upright(frame, rotation, mirror)
                    assertEquals("size at $rotation/$mirror", expected.width to expected.height, actual.width to actual.height)
                    assertTrue("pixels differ at rotation $rotation, mirror $mirror, frame $seed", expected.sameAs(actual))
                    expected.recycle()
                }
            }
        } finally {
            frames.release()
        }
    }

    /**
     * MediaPipe copies a bitmap's pixels inside `detectAsync`, so overwriting the
     * bitmap as soon as the call returns - which the next frame does - cannot
     * change what it analyses. A frame with a person in it is submitted and blacked
     * out immediately; every result must still find the person. A blacked-out
     * frame submitted as itself must not, or the check would prove nothing.
     *
     * Needs `bench/person_480x640.png` in the app's external files - a local file,
     * never committed (it shows a person). Skipped without it.
     */
    @Test
    fun mediapipe_copies_the_bitmap_before_detectAsync_returns() {
        val file = File(File(context.getExternalFilesDir(null), "bench"), "person_480x640.png")
        assumeTrue("no $file on the device", file.isFile)
        val person = BitmapFactory.decodeFile(file.path).copy(Bitmap.Config.ARGB_8888, false)
        val reused = Bitmap.createBitmap(person.width, person.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(person.width * person.height)
        person.getPixels(pixels, 0, person.width, 0, 0, person.width, person.height)

        val answered = Semaphore(0)
        val found = AtomicInteger()
        val landmarker = PoseLandmarker.createFromOptions(
            context,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setResultListener { result, _ ->
                    if (result.landmarks().isNotEmpty()) found.incrementAndGet()
                    answered.release()
                }
                .setErrorListener { error -> throw AssertionError("MediaPipe error", error) }
                .build(),
        )
        try {
            val image = BitmapImageBuilder(reused).build()
            var timestamp = 0L

            // Control: a black frame, submitted as itself, holds nobody.
            reused.eraseColor(Color.BLACK)
            landmarker.detectAsync(image, ++timestamp * 33)
            assertTrue(answered.tryAcquire(10, TimeUnit.SECONDS))
            assertEquals("a black frame found a person - the check below would prove nothing", 0, found.get())

            val frames = 40
            repeat(frames) {
                reused.setPixels(pixels, 0, person.width, 0, 0, person.width, person.height)
                landmarker.detectAsync(image, ++timestamp * 33)
                reused.eraseColor(Color.BLACK) // the next frame overwriting the buffer
                assertTrue("no result", answered.tryAcquire(10, TimeUnit.SECONDS))
            }
            assertEquals("frames in which the person was still found after the bitmap was blacked out", frames, found.get())
        } finally {
            landmarker.close()
        }
    }
}
