package com.kinesis.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.MediaPipeException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Front-camera capture for the live screen: a preview for the user and an
 * analysis stream for [PoseLandmarkerSource] - DECISIONS.md P3-15.
 *
 * ### Mirroring is made true here, not assumed
 *
 * `PoseAdapter` assumes front-camera frames reach the pose model mirrored
 * (DECISIONS.md P1-5). CameraX's analysis stream is the raw sensor image, which
 * is not mirrored - only the preview is. So every frame is mirrored here before
 * MediaPipe sees it, which makes P1-5's premise hold by construction rather than
 * by CameraX configuration. Whether CameraX really delivers unmirrored analysis
 * frames on this device is exactly the check Phase B owes; if it does not, this
 * is the line that changes, not the adapter.
 *
 * ### Timestamps
 *
 * The sensor timestamp, not the wall clock: it is when the frame was exposed,
 * so repetition tempo does not absorb processing jitter. MediaPipe requires
 * strictly increasing timestamps, so a frame that does not advance is dropped.
 *
 * ### Shutdown
 *
 * [close] unbinds the camera and then closes the [source] **on the analysis
 * thread**, behind any frame already in flight. Closing it from the main thread
 * could land between that frame's checks and its `detectAsync`, and an
 * exception on a background thread takes the whole app down.
 */
class LiveCamera(
    private val context: Context,
    private val source: PoseLandmarkerSource,
    private val onUnavailable: (String) -> Unit,
) : AutoCloseable {

    private val analysisThread: ExecutorService = Executors.newSingleThreadExecutor()

    // Main thread only.
    private var provider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var closed = false

    // Analysis thread only.
    private var lastTimestampMs = Long.MIN_VALUE

    /**
     * Binds to [owner]'s lifecycle, so backgrounding the app stops the camera.
     *
     * @param displayRotation the display's `Surface.ROTATION_*` while the live
     *   screen is locked to portrait. Passed explicitly rather than left to
     *   CameraX's default, which is read when the use case is built and would be
     *   wrong if that happened mid-rotation.
     */
    fun bind(owner: LifecycleOwner, previewView: PreviewView, displayRotation: Int) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (closed) return@addListener
            val cameraProvider = try {
                future.get()
            } catch (error: ExecutionException) {
                onUnavailable("camera service unavailable: " + error.cause?.message)
                return@addListener
            }

            // 4:3 on both use cases with no fallback - DECISIONS.md P3-16. The
            // live screen sizes the preview and the overlay to exactly this shape,
            // so a device that cannot provide it is told so below rather than
            // drawing a skeleton that drifts off the body.
            val resolution = ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_NONE),
                )
                .build()
            val preview = Preview.Builder()
                .setResolutionSelector(resolution)
                .setTargetRotation(displayRotation)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setTargetRotation(displayRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(analysisThread, ::analyse) }

            try {
                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (error: IllegalArgumentException) {
                onUnavailable("no usable front camera with a 4:3 frame: " + error.message)
                return@addListener
            } catch (error: IllegalStateException) {
                onUnavailable("camera could not be bound: " + error.message)
                return@addListener
            }
            provider = cameraProvider
            analysisUseCase = analysis
            previewUseCase = preview

            val previewSize = preview.resolutionInfo?.resolution
            val analysisSize = analysis.resolutionInfo?.resolution
            if (!isFourByThree(previewSize) || !isFourByThree(analysisSize)) {
                close()
                onUnavailable(
                    "the camera bound a frame that is not 4:3 (preview " + previewSize +
                        ", analysis " + analysisSize + "), so the skeleton would not line up",
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun isFourByThree(size: Size?): Boolean =
        size != null && isFourByThree(size.width, size.height)

    private fun analyse(image: ImageProxy) {
        try {
            val timestampMs = image.imageInfo.timestamp / NANOS_PER_MILLI
            if (timestampMs <= lastTimestampMs) return
            lastTimestampMs = timestampMs
            source.analyse(image, uprightAndMirrored(image), timestampMs)
        } catch (error: MediaPipeException) {
            // One dropped frame; the next may succeed. Not worth the screen.
        } finally {
            image.close()
        }
    }

    /** Rotated upright, then mirrored left-to-right. See the class note. */
    private fun uprightAndMirrored(image: ImageProxy): Bitmap {
        val raw = image.toBitmap()
        val transform = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
            postScale(-1f, 1f)
        }
        val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, transform, false)
        if (upright !== raw) raw.recycle()
        return upright
    }

    /** Main thread. Idempotent. */
    override fun close() {
        if (closed) return
        closed = true
        // No frame is dispatched after clearAnalyzer, so the close queued below
        // runs after every frame that ever will.
        analysisUseCase?.clearAnalyzer()
        val bound = listOfNotNull(previewUseCase, analysisUseCase)
        if (bound.isNotEmpty()) provider?.unbind(*bound.toTypedArray())
        analysisThread.execute { source.close() }
        analysisThread.shutdown()
    }

    companion object {
        /**
         * Width over height of the upright analysis frame on a portrait screen:
         * a 4:3 sensor frame turned on its side. The live screen's camera area
         * is sized to this, and [bind] refuses a frame of any other shape.
         */
        const val PORTRAIT_FRAME_ASPECT: Float = 3f / 4f

        /** Either orientation: the sensor reports landscape, the screen shows portrait. */
        fun isFourByThree(width: Int, height: Int): Boolean {
            if (width <= 0 || height <= 0) return false
            val long = maxOf(width, height).toDouble()
            val short = minOf(width, height).toDouble()
            return abs(long / short - 4.0 / 3.0) < ASPECT_TOLERANCE
        }

        private const val NANOS_PER_MILLI = 1_000_000L

        /** 640x480 and 1440x1080 are exact; this only absorbs rounding. */
        private const val ASPECT_TOLERANCE = 0.01
    }
}
