package com.kinesis.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.pose.CameraFacing
import com.kinesis.pose.PoseAdapter
import com.kinesis.pose.RawLandmarks

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
 */
class PoseLandmarkerSource(
    context: Context,
    private val modelAssetPath: String,
    private val facing: CameraFacing,
    private val onLandmarks: (Landmarks, Long) -> Unit,
    /**
     * A frame in which no complete pose was found. Required, not defaulted: a
     * caller that ignores it keeps drawing the last skeleton after the user has
     * left the frame, in the colour that means "tracked". DECISIONS.md P3-15.
     */
    private val onNoPose: (Long) -> Unit,
) : AutoCloseable {

    private val adapter = PoseAdapter()

    /** Reused across frames. Allocating one per frame is what section 6 forbids. */
    private val packed = DoubleArray(Landmarks.PACKED_SIZE)

    private val landmarker: PoseLandmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetPath).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setResultListener { result, input -> onResult(result, input.width, input.height) }
            .setErrorListener { /* a dropped frame is not worth taking the screen down */ }
            .build(),
    )

    /**
     * One camera frame.
     *
     * `LIVE_STREAM` mode is asynchronous: this returns immediately and the
     * result arrives on the listener. That is what keeps the analysis off the
     * camera thread.
     */
    fun analyse(image: ImageProxy, bitmap: Bitmap, timestampMs: Long) {
        landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
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
}
