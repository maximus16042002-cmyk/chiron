package com.kinesis.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.kinesis.diagnostics.PhaseBProbe
import com.kinesis.pose.CameraFacing
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Camera capture for the live screen: a preview for the user and an analysis
 * stream for [PoseLandmarkerSource] - DECISIONS.md P3-15 and P3-17.
 *
 * Which camera is taken from [PoseLandmarkerSource.facing], never passed
 * separately, so the camera opened, whether its frames are mirrored and what the
 * adapter is told are one decision and cannot disagree.
 *
 * ### Mirroring is made true here, not assumed
 *
 * `PoseAdapter` assumes front-camera frames reach the pose model mirrored
 * (DECISIONS.md P1-5). CameraX's analysis stream is the raw sensor image, which
 * is not mirrored - only the front preview is. So a front-camera frame is
 * mirrored here before MediaPipe sees it, which makes P1-5's premise hold by
 * construction rather than by CameraX configuration. A back-camera frame is not
 * touched, and the adapter undoes nothing for it: that path has no mirroring
 * anywhere. Whether CameraX really delivers unmirrored analysis frames on this
 * device is exactly the check Phase B owes, once per camera; if it does not,
 * [mirrorsAnalysisFrame] is what changes, not the adapter.
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
    /** Debug and profiling builds' Phase B probe; `null` in release. DECISIONS.md P3-19. */
    private val probe: PhaseBProbe? = null,
    private val onUnavailable: (String) -> Unit,
) : AutoCloseable {

    private val analysisThread: ExecutorService = Executors.newSingleThreadExecutor()

    // Main thread only.
    private var provider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private val displays = context.getSystemService(DisplayManager::class.java)
    private var closed = false

    // Analysis thread only.
    private var lastTimestampMs = Long.MIN_VALUE
    private val frames = UprightFrames()

    /**
     * Binds to [owner]'s lifecycle, so backgrounding the app stops the camera.
     *
     * @param displayRotation the display's `Surface.ROTATION_*` when binding -
     *   upright or upside-down portrait. Passed explicitly rather than left to
     *   CameraX's default, which is read when the use case is built and would be
     *   wrong if that happened mid-rotation. Later turns are followed by a display
     *   listener - DECISIONS.md P3-19.
     */
    fun bind(owner: LifecycleOwner, previewView: PreviewView, displayRotation: Int) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (closed) return@addListener
            // The binding boundary - DECISIONS.md P3-18. This runs on the main
            // thread, where anything uncaught kills the app.
            try {
                bindNow(future.get(), owner, previewView, displayRotation)
            } catch (error: ExecutionException) {
                unavailable("the camera service is unavailable", error.cause ?: error)
            } catch (error: IllegalArgumentException) {
                unavailable("no usable " + source.facing.name.lowercase() + " camera with a 4:3 frame", error)
            } catch (error: Exception) {
                unavailable("the camera could not be started", error)
            } catch (error: LinkageError) {
                unavailable("a camera library is missing from this build", error)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun unavailable(what: String, error: Throwable?) {
        Log.e(LIVE_LOG_TAG, what, error)
        onUnavailable(if (error == null) what else what + " (" + error.javaClass.simpleName + ": " + error.message + ")")
    }

    private fun bindNow(
        cameraProvider: ProcessCameraProvider,
        owner: LifecycleOwner,
        previewView: PreviewView,
        displayRotation: Int,
    ) {
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

        val selector = when (source.facing) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        cameraProvider.bindToLifecycle(owner, selector, preview, analysis)
        provider = cameraProvider
        analysisUseCase = analysis
        previewUseCase = preview

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == previewView.display?.displayId) followDisplay(previewView)
            }
        }
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        displayListener = listener
        // The display may have turned between the host reading it and this bind.
        followDisplay(previewView)

        val previewSize = preview.resolutionInfo?.resolution
        val analysisSize = analysis.resolutionInfo?.resolution
        if (!isFourByThree(previewSize) || !isFourByThree(analysisSize)) {
            close()
            unavailable(
                "the camera bound a frame that is not 4:3 (preview " + previewSize +
                    ", analysis " + analysisSize + "), so the skeleton would not line up",
                null,
            )
        }
    }

    private fun isFourByThree(size: Size?): Boolean =
        size != null && isFourByThree(size.width, size.height)

    /**
     * The analyzer's boundary - DECISIONS.md P3-18. Anything thrown here is on
     * CameraX's thread, where an uncaught throwable kills the app, so it is
     * caught, logged, and stops analysis through the source's single failure
     * path. It is not dropped as "one bad frame": a frame that fails to convert
     * or submit is a fault to see, not to hide.
     */
    private fun analyse(image: ImageProxy) {
        try {
            if (source.hasFailed) return
            val timestampMs = image.imageInfo.timestamp / NANOS_PER_MILLI
            probe?.onCameraFrame(timestampMs)
            if (timestampMs <= lastTimestampMs) return
            lastTimestampMs = timestampMs
            val start = System.nanoTime()
            val upright = upright(image)
            val converted = System.nanoTime()
            source.analyse(upright, timestampMs)
            probe?.onSubmitted(timestampMs, converted - start, System.nanoTime() - converted)
        } catch (error: Exception) {
            source.fail("a camera frame could not be analysed", error)
        } catch (error: LinkageError) {
            source.fail("a library pose estimation needs is missing from this build", error)
        } finally {
            image.close()
        }
    }

    /**
     * Rotated upright, and mirrored left-to-right for the front camera only. See
     * the class note. Into kept buffers, so a frame allocates no bitmaps; the one
     * returned is overwritten by the next frame, which is safe because MediaPipe
     * copies it inside `detectAsync` - DECISIONS.md P3-19.
     */
    private fun upright(image: ImageProxy): Bitmap =
        frames.upright(image, image.imageInfo.rotationDegrees, mirrorsAnalysisFrame(source.facing))

    /**
     * The target rotations CameraX was last given, preview then analysis; `null`
     * before binding. Main thread. For `LiveRotationOnDeviceTest`, which turns the
     * display and checks both follow.
     */
    internal val targetRotations: Pair<Int, Int>?
        get() {
            val preview = previewUseCase ?: return null
            val analysis = analysisUseCase ?: return null
            return preview.targetRotation to analysis.targetRotation
        }

    /**
     * The display turned - DECISIONS.md P3-19. Between upright and upside-down
     * portrait the configuration does not change, so nothing recomposes; this is
     * the only notice. The new rotation goes to both use cases without rebinding:
     * CameraX applies it to the frames that follow, and `PreviewView` redraws
     * itself on the same display change. Landscape is ignored - the live screen
     * never asks for it.
     */
    private fun followDisplay(previewView: PreviewView) {
        val display = previewView.display ?: return
        val rotation = display.rotation
        if (!LiveOrientation.isSupported(rotation)) return
        previewUseCase?.targetRotation = rotation
        analysisUseCase?.targetRotation = rotation
    }

    /** Main thread. Idempotent. */
    override fun close() {
        if (closed) return
        closed = true
        displayListener?.let { displays.unregisterDisplayListener(it) }
        displayListener = null
        // No frame is dispatched after clearAnalyzer, so the close queued below
        // runs after every frame that ever will.
        analysisUseCase?.clearAnalyzer()
        val bound = listOfNotNull(previewUseCase, analysisUseCase)
        if (bound.isNotEmpty()) provider?.unbind(*bound.toTypedArray())
        analysisThread.execute {
            source.close()
            frames.release()
        }
        analysisThread.shutdown()
    }

    companion object {
        /**
         * Width over height of the upright analysis frame on a portrait screen:
         * a 4:3 sensor frame turned on its side. The live screen's camera area
         * is sized to this, and [bind] refuses a frame of any other shape.
         */
        const val PORTRAIT_FRAME_ASPECT: Float = 3f / 4f

        /**
         * Whether a frame from [facing] is mirrored before the pose model sees
         * it: the front camera's is, so that `PoseAdapter`'s P1-5 premise holds;
         * the back camera's is not, and the adapter undoes nothing for it.
         * Our decision, pinned by `CameraFacingParityTest`.
         */
        fun mirrorsAnalysisFrame(facing: CameraFacing): Boolean = facing == CameraFacing.FRONT

        /**
         * Whether `PreviewView` shows [facing]'s preview mirrored - CameraX does
         * for the front camera and not for the back. Not a choice made here but
         * a statement of CameraX's behaviour, which the overlay has to follow;
         * Phase B confirms it on the device.
         */
        fun previewIsMirrored(facing: CameraFacing): Boolean = facing == CameraFacing.FRONT

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
