package com.kinesis.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.diagnostics.PhaseBProbe
import com.kinesis.pose.CameraFacing
import com.kinesis.pose.PoseAdapter
import com.kinesis.pose.RawLandmarks
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turns camera frames into anatomical landmarks - DECISIONS.md P3-13.
 *
 * ### What this does and, more importantly, what it does not
 *
 * It runs MediaPipe and hands the result to [PoseAdapter], which is the P1 class
 * that converts camera coordinates into anatomical left and right. **Nothing
 * here touches mirroring or landmark layout.** Those were settled in P1, are
 * guarded by `MirroringRegressionTest`, and FINAL_ARCHITECTURE section 12-D
 * names front-camera mirroring as the risk that fails most quietly - so this
 * batch feeds that adapter rather than re-deriving what it does.
 *
 * The one thing this class must get right is the direction it declares:
 * [facing] is passed straight through to the adapter, and getting it wrong
 * swaps every left/right measurement in the system with no visible symptom.
 * Phase B's live mirroring check is the only thing that can confirm it.
 *
 * ### The visibility field
 *
 * MediaPipe's landmark carries both `visibility` and `presence`. Kinesis's
 * validity model (P1-4) is built on a single 0..1 confidence compared against
 * `VisibilityPolicy`, and `visibility` is the one that means "this landmark is
 * where I say it is" rather than "this landmark is in frame at all". Recorded
 * because the two are easy to swap and the failure would be a validity gate
 * that passes frames it should not.
 *
 * ### Failing safe - DECISIONS.md P3-18
 *
 * The first Phase B run crashed the app when the landmarker was created. Nothing
 * that goes wrong here may do that again. [open] reports a creation failure,
 * `Error`s included, instead of throwing; a failure in the result listener or a
 * MediaPipe error stops analysis, is logged under [LIVE_LOG_TAG], and is reported
 * once through [onFailure] so the screen can say so. Nothing is swallowed.
 */
class PoseLandmarkerSource private constructor(
    context: Context,
    private val modelAssetPath: String,
    /**
     * The camera these frames come from. Public so `LiveCamera` takes its facing
     * from here rather than being told separately: the camera it opens, whether
     * it mirrors the frame, and what the adapter is told can then never disagree.
     * DECISIONS.md P3-17.
     */
    val facing: CameraFacing,
    private val onLandmarks: (Landmarks, Long) -> Unit,
    /**
     * A frame in which no complete pose was found. Required, not defaulted: a
     * caller that ignores it keeps drawing the last skeleton after the user has
     * left the frame, in the colour that means "tracked". DECISIONS.md P3-15.
     */
    private val onNoPose: (Long) -> Unit,
    /**
     * Analysis has stopped, and why, in words a screen can show. Called at most
     * once, on whichever thread failed - post to the main thread before touching UI.
     */
    private val onFailure: (String) -> Unit,
    /** Debug and profiling builds' Phase B probe; `null` in release. DECISIONS.md P3-19. */
    private val probe: PhaseBProbe?,
) : AutoCloseable {

    private val adapter = PoseAdapter()

    /** When recent frames were handed over, for the probe's latency. DECISIONS.md P3-19. */
    private val handedOver = HandOverTimes()

    // Analysis thread only: the wrapper for the kept bitmap LiveCamera passes
    // every frame, rebuilt only if that bitmap changes.
    private var wrappedBitmap: Bitmap? = null
    private var wrapped: MPImage? = null

    /** Reused across frames. Allocating one per frame is what section 6 forbids. */
    private val packed = DoubleArray(Landmarks.PACKED_SIZE)

    /** Set once, from any thread; after it, nothing is analysed or delivered. */
    private val failure = AtomicBoolean(false)

    val hasFailed: Boolean get() = failure.get()

    private val landmarker: PoseLandmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetPath).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setResultListener { result, input -> deliver(result, input.width, input.height) }
            .setErrorListener { error -> fail("pose estimation reported an error", error) }
            .build(),
    )

    /**
     * One camera frame.
     *
     * `LIVE_STREAM` mode is asynchronous: this returns immediately and the
     * result arrives on the listener. That is what keeps the analysis off the
     * camera thread. Does nothing once analysis has failed.
     */
    fun analyse(bitmap: Bitmap, timestampMs: Long) {
        if (hasFailed) return
        val image = wrapped?.takeIf { wrappedBitmap === bitmap }
            ?: BitmapImageBuilder(bitmap).build().also {
                wrappedBitmap = bitmap
                wrapped = it
            }
        if (probe != null) handedOver.record(timestampMs, System.nanoTime())
        landmarker.detectAsync(image, timestampMs)
    }

    /**
     * Stops analysis and reports why, once. Public so the capture side can report
     * its own failures through the same path.
     */
    fun fail(what: String, error: Throwable) {
        if (!failure.compareAndSet(false, true)) return
        Log.e(LIVE_LOG_TAG, what, error)
        onFailure(what + " (" + error.javaClass.simpleName + ": " + error.message + ")")
    }

    /** The result listener's boundary: an exception here must not reach MediaPipe's thread. */
    private fun deliver(result: PoseLandmarkerResult, frameWidthPx: Int, frameHeightPx: Int) {
        if (hasFailed) return
        try {
            if (probe != null) {
                val at = handedOver.at(result.timestampMs())
                if (at != HandOverTimes.UNKNOWN) probe.onAnswered(System.nanoTime() - at)
            }
            onResult(result, frameWidthPx, frameHeightPx)
        } catch (error: Exception) {
            fail("analysing a pose result failed", error)
        }
    }

    /**
     * Stamped with the result's own timestamp, which MediaPipe carries through
     * from [analyse]. Not "the last frame submitted": `LIVE_STREAM` drops frames
     * while busy and delivers results after later frames have been submitted, so
     * that value belongs to a different frame, and repetition tempo is measured
     * from these timestamps. DECISIONS.md P3-15.
     */
    private fun onResult(result: PoseLandmarkerResult, frameWidthPx: Int, frameHeightPx: Int) {
        val poses = result.landmarks()
        if (poses.isEmpty() || poses[0].size != com.kinesis.core.domain.landmark.LandmarkId.COUNT) {
            onNoPose(result.timestampMs())
            return
        }
        val landmarks = poses[0]

        for (index in landmarks.indices) {
            val landmark = landmarks[index]
            val base = index * Landmarks.STRIDE
            packed[base] = landmark.x().toDouble()
            packed[base + 1] = landmark.y().toDouble()
            packed[base + 2] = landmark.z().toDouble()
            // visibility, not presence - see the note on this class.
            packed[base + 3] = landmark.visibility().orElse(0f).toDouble()
        }

        // The adapter is the only thing that decides what "left" means, and the
        // only thing that knows the frame had a shape. The size is the image
        // this result was computed from, as MediaPipe hands it back.
        onLandmarks(
            adapter.toAnatomical(RawLandmarks(packed), facing, frameWidthPx, frameHeightPx),
            result.timestampMs(),
        )
    }

    override fun close() = landmarker.close()

    companion object {

        /**
         * Creates the landmarker, or says why it could not.
         *
         * Catches `LinkageError` as well as exceptions: the P3-18 crash was a
         * `NoClassDefFoundError`, which is an `Error`, and a missing native
         * library is an `UnsatisfiedLinkError`. Both are ordinary conditions on
         * some device or some build, and both must produce a message, not a crash.
         */
        fun open(
            context: Context,
            modelAssetPath: String,
            facing: CameraFacing,
            onLandmarks: (Landmarks, Long) -> Unit,
            onNoPose: (Long) -> Unit,
            onFailure: (String) -> Unit,
            probe: PhaseBProbe? = null,
        ): PoseSourceOpening = try {
            PoseSourceOpening.Opened(
                PoseLandmarkerSource(context, modelAssetPath, facing, onLandmarks, onNoPose, onFailure, probe),
            )
        } catch (error: Exception) {
            Log.e(LIVE_LOG_TAG, "the pose landmarker could not be created from $modelAssetPath", error)
            PoseSourceOpening.Failed("the pose model could not be started (" + describe(error) + ")")
        } catch (error: LinkageError) {
            Log.e(LIVE_LOG_TAG, "the pose landmarker could not be created from $modelAssetPath", error)
            PoseSourceOpening.Failed("a library pose estimation needs is missing from this build (" + describe(error) + ")")
        }

        private fun describe(error: Throwable): String = error.javaClass.simpleName + ": " + error.message
    }
}

/** What [PoseLandmarkerSource.open] produced. */
sealed interface PoseSourceOpening {
    class Opened(val source: PoseLandmarkerSource) : PoseSourceOpening
    class Failed(val reason: String) : PoseSourceOpening
}

/** Every live-pipeline failure is logged under this tag: `adb logcat -s KinesisLive`. */
const val LIVE_LOG_TAG = "KinesisLive"
