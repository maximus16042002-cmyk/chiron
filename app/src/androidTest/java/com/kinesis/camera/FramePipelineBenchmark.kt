package com.kinesis.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The frame-rate investigation's bench half - DECISIONS.md P3-19.
 *
 * On the phone, live analysis ran at 4-16 results a second against a target of
 * about 30. This times, on the device and without the camera, the two costs every
 * frame pays before a result: converting CameraX's frame to an upright bitmap,
 * and MediaPipe's pose landmarker. It measures; it asserts nothing about speed.
 * Results go to Logcat under `KinesisBench`.
 *
 * Opt-in, so the ordinary connected suite stays quick:
 * `-Pandroid.testInstrumentationRunnerArguments.kinesisBenchmark=true`. Run it on
 * the debug build and on the non-debuggable profiling build
 * (`-Pkinesis.testBuildType=profiling`), because a debuggable build's timings are
 * not the product's.
 *
 * Two optional inputs are read from the app's external files, `bench/`, and are
 * never committed: `person_480x640.png`, a frame with a person in it (without one
 * the landmarker runs only its detector, which understates the cost), and
 * `pose_landmarker_full.task`, Google's full model, for comparison with the
 * bundled lite one. Each case is skipped when its input is absent.
 */
@RunWith(AndroidJUnit4::class)
class FramePipelineBenchmark {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val benchDir: File get() = File(context.getExternalFilesDir(null), "bench")

    @Before
    fun onlyWhenAsked() {
        val asked = InstrumentationRegistry.getArguments().getString("kinesisBenchmark") == "true"
        assumeTrue("benchmark not requested", asked)
        val thermal = context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus
        log(
            "build: " + (if (isDebuggable()) "debuggable" else "not debuggable") + ", " + Build.MODEL +
                ", Android " + Build.VERSION.RELEASE + ", thermal status " + thermal + " (0 none, 1 light, 2 moderate)",
        )
    }

    // --- Conversion: CameraX's frame to the upright bitmap MediaPipe is given ------------

    @Test
    fun conversion_of_a_640x480_rgba_frame() {
        val frame = SyntheticRgbaFrame(WIDTH, HEIGHT)
        time("toBitmap only", CONVERSION_WARMUP, CONVERSION_RUNS) {
            frame.toBitmap().recycle()
        }
        for ((camera, rotation, mirror) in listOf(
            Triple("back camera (90, no mirror)", 90, false),
            Triple("front camera (270, mirrored)", 270, true),
        )) {
            // The allocating path and the kept buffers, interleaved frame by frame
            // so both see the same heat and CPU state - separate runs on a warm
            // phone drifted by a millisecond between otherwise identical cases.
            val kept = UprightFrames()
            repeat(CONVERSION_WARMUP) {
                FrameUpright.upright(frame.toBitmap(), rotation, mirror).recycle()
                kept.upright(frame, rotation, mirror)
            }
            val allocating = LongArray(CONVERSION_RUNS)
            val keptTimes = LongArray(CONVERSION_RUNS)
            for (i in 0 until CONVERSION_RUNS) {
                var start = System.nanoTime()
                FrameUpright.upright(frame.toBitmap(), rotation, mirror).recycle()
                allocating[i] = System.nanoTime() - start
                start = System.nanoTime()
                kept.upright(frame, rotation, mirror)
                keptTimes[i] = System.nanoTime() - start
            }
            report("allocating (toBitmap + upright), $camera", allocating)
            report("kept buffers, $camera", keptTimes)

            // What each leaves for the garbage collector, measured separately.
            gcDuring("allocating, $camera") { FrameUpright.upright(frame.toBitmap(), rotation, mirror) }
            gcDuring("kept buffers, $camera") { kept.upright(frame, rotation, mirror) }
            kept.release()
        }
    }

    /** Bytes allocated and collections run over [CONVERSION_RUNS] frames, from ART's own counters. */
    private fun gcDuring(label: String, body: () -> Unit) {
        Runtime.getRuntime().gc()
        val bytes0 = android.os.Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
        val gcs0 = android.os.Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull()
        repeat(CONVERSION_RUNS) { body() }
        val bytes1 = android.os.Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
        val gcs1 = android.os.Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull()
        if (bytes0 == null || bytes1 == null || gcs0 == null || gcs1 == null) {
            log("$label: ART's allocation counters are unavailable on this build")
            return
        }
        log(
            String.format(
                Locale.ROOT, "%s: %.0f KB allocated per frame, %d collections over %d frames",
                label, (bytes1 - bytes0) / 1024.0 / CONVERSION_RUNS, gcs1 - gcs0, CONVERSION_RUNS,
            ),
        )
    }

    // --- MediaPipe ----------------------------------------------------------------------

    @Test
    fun lite_model_on_an_empty_frame() = landmarkerLatency(
        "lite, empty frame", BaseOptions.builder().setModelAssetPath(LITE_ASSET).build(), blankFrame(),
    )

    @Test
    fun lite_model_on_a_person() = landmarkerLatency(
        "lite, person", BaseOptions.builder().setModelAssetPath(LITE_ASSET).build(), personFrame(),
    )

    /** Measurement only - the app uses the default CPU delegate, and this does not change it. */
    @Test
    fun lite_model_on_a_person_gpu_delegate() = landmarkerLatency(
        "lite, person, GPU delegate",
        BaseOptions.builder().setModelAssetPath(LITE_ASSET).setDelegate(Delegate.GPU).build(),
        personFrame(),
    )

    @Test
    fun full_model_on_an_empty_frame() = landmarkerLatency("full, empty frame", fullModel(), blankFrame())

    @Test
    fun full_model_on_a_person() = landmarkerLatency("full, person", fullModel(), personFrame())

    /**
     * One frame in flight at a time, as the production options set it up
     * (`LIVE_STREAM`, one pose, the default CPU delegate). Two numbers per frame:
     * how long `detectAsync` holds the analysis thread, and how long until the
     * result arrives. The second bounds the achievable results per second.
     */
    private fun landmarkerLatency(label: String, base: BaseOptions, frame: Bitmap) {
        val answered = Semaphore(0)
        val submittedAt = AtomicLong()
        val latencies = LongArray(MEDIAPIPE_WARMUP + MEDIAPIPE_RUNS)
        val submits = LongArray(MEDIAPIPE_WARMUP + MEDIAPIPE_RUNS)
        val poses = AtomicLong()
        var index = 0
        val landmarker = PoseLandmarker.createFromOptions(
            context,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setResultListener { result, _ ->
                    latencies[index] = System.nanoTime() - submittedAt.get()
                    if (result.landmarks().isNotEmpty()) poses.incrementAndGet()
                    answered.release()
                }
                .setErrorListener { error -> throw AssertionError("MediaPipe error", error) }
                .build(),
        )
        try {
            val image = BitmapImageBuilder(frame).build()
            for (i in 0 until MEDIAPIPE_WARMUP + MEDIAPIPE_RUNS) {
                index = i
                val start = System.nanoTime()
                submittedAt.set(start)
                landmarker.detectAsync(image, (i + 1) * FRAME_INTERVAL_MS)
                submits[i] = System.nanoTime() - start
                check(answered.tryAcquire(10, TimeUnit.SECONDS)) { "no result for frame $i" }
            }
        } finally {
            landmarker.close()
        }
        report("$label: detectAsync call", submits.copyOfRange(MEDIAPIPE_WARMUP, submits.size))
        report("$label: submit to result", latencies.copyOfRange(MEDIAPIPE_WARMUP, latencies.size))
        log("$label: a pose was found in ${poses.get()} of ${MEDIAPIPE_WARMUP + MEDIAPIPE_RUNS} frames")
    }

    private fun fullModel(): BaseOptions {
        val file = File(benchDir, "pose_landmarker_full.task")
        assumeTrue("no $file on the device", file.isFile)
        val bytes = file.readBytes()
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        buffer.rewind()
        return BaseOptions.builder().setModelAssetBuffer(buffer).build()
    }

    private fun blankFrame(): Bitmap = Bitmap.createBitmap(HEIGHT, WIDTH, Bitmap.Config.ARGB_8888)

    private fun personFrame(): Bitmap {
        val file = File(benchDir, "person_480x640.png")
        assumeTrue("no $file on the device", file.isFile)
        return BitmapFactory.decodeFile(file.path).copy(Bitmap.Config.ARGB_8888, false)
    }

    // --- Timing and reporting ------------------------------------------------------------

    private fun time(label: String, warmup: Int, runs: Int, body: () -> Unit) {
        repeat(warmup) { body() }
        val samples = LongArray(runs)
        for (i in 0 until runs) {
            val start = System.nanoTime()
            body()
            samples[i] = System.nanoTime() - start
        }
        report(label, samples)
    }

    private fun report(label: String, nanos: LongArray) {
        val ms = nanos.map { it / 1e6 }.sorted()
        fun at(q: Double) = ms[((ms.size - 1) * q).toInt()]
        log(
            String.format(
                Locale.ROOT, "%s: median %.2f ms, p90 %.2f ms, mean %.2f ms, n=%d",
                label, at(0.5), at(0.9), ms.average(), ms.size,
            ),
        )
    }

    /**
     * To Logcat and to `bench/results.txt` in the app's external files: this
     * phone's Logcat keeps only seconds, and a first run lost lines to it.
     */
    private fun log(line: String) {
        Log.i(TAG, line)
        benchDir.mkdirs()
        File(benchDir, "results.txt").appendText(line + "\n")
    }

    private fun isDebuggable(): Boolean =
        context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    private companion object {
        const val TAG = "KinesisBench"
        const val WIDTH = 640
        const val HEIGHT = 480
        const val LITE_ASSET = "pose_landmarker_lite.task"
        const val CONVERSION_WARMUP = 30
        const val CONVERSION_RUNS = 300
        const val MEDIAPIPE_WARMUP = 20
        const val MEDIAPIPE_RUNS = 150
        const val FRAME_INTERVAL_MS = 33L
    }
}
