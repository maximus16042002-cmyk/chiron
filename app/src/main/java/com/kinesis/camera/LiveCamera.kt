package com.kinesis.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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

    /** Binds to [owner]'s lifecycle, so backgrounding the app stops the camera. */
    fun bind(owner: LifecycleOwner, previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (closed) return@addListener
            val cameraProvider = try {
                future.get()
            } catch (error: ExecutionException) {
                onUnavailable("camera service unavailable: " + error.cause?.message)
                return@addListener
            }

            // Preview and analysis share one aspect ratio so the overlay, drawn in
            // analysis-frame coordinates, lines up with what the user sees.
            val resolution = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
            val preview = Preview.Builder().setResolutionSelector(resolution).build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(analysisThread, ::analyse) }

            try {
                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (error: IllegalArgumentException) {
                onUnavailable("no usable front camera: " + error.message)
                return@addListener
            } catch (error: IllegalStateException) {
                onUnavailable("camera could not be bound: " + error.message)
                return@addListener
            }
            provider = cameraProvider
            analysisUseCase = analysis
            previewUseCase = preview
        }, ContextCompat.getMainExecutor(context))
    }

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

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
