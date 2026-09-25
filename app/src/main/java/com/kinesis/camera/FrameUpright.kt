package com.kinesis.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import kotlin.math.roundToInt

/**
 * Turns a camera frame upright for the display, and mirrors the front camera's -
 * DECISIONS.md P3-15, P3-19.
 *
 * `rotationDegrees` is CameraX's: the clockwise turn that makes the frame upright
 * for the target rotation [LiveCamera] sets from the display. On one camera it is
 * 180 degrees apart between upright and upside-down portrait, so the upside-down
 * frame is the upright one turned half a circle - exactly what undoes the phone
 * being inverted.
 *
 * The mirror is applied after the turn, left to right in the upright frame, so it
 * is the same selfie mirror whichever way up the phone is.
 */
object FrameUpright {

    /** The transform as a matrix, into [into], which is reset first and returned. */
    fun matrix(rotationDegrees: Int, mirror: Boolean, into: Matrix = Matrix()): Matrix = into.apply {
        reset()
        postRotate(rotationDegrees.toFloat())
        if (mirror) postScale(-1f, 1f)
    }

    /**
     * The upright frame, as a new bitmap. [raw] is recycled if a new bitmap was
     * made, so the caller must not use it afterwards.
     */
    fun upright(raw: Bitmap, rotationDegrees: Int, mirror: Boolean): Bitmap {
        val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix(rotationDegrees, mirror), false)
        if (upright !== raw) raw.recycle()
        return upright
    }

    /**
     * Where a point of the raw frame lands in the upright one, both normalised to
     * their own frame's width and height. The specification [upright] is tested
     * against on the device, and the one the JVM tests reason with.
     */
    fun uprightPoint(u: Double, v: Double, rotationDegrees: Int, mirror: Boolean): Pair<Double, Double> {
        val (x, y) = when (Math.floorMod(rotationDegrees, 360)) {
            0 -> u to v
            90 -> (1.0 - v) to u
            180 -> (1.0 - u) to (1.0 - v)
            270 -> v to (1.0 - u)
            else -> throw IllegalArgumentException("CameraX rotates in quarter turns, not $rotationDegrees degrees")
        }
        return (if (mirror) 1.0 - x else x) to y
    }
}

/**
 * [FrameUpright.upright] for a live camera, without allocating per frame -
 * DECISIONS.md P3-19.
 *
 * The allocating path made two 1.2 MB bitmaps a frame, CameraX's `toBitmap` and
 * the turned copy: about 72 MB a second of garbage at 30 frames a second. This
 * keeps one bitmap of each and draws into them, with the output pinned
 * pixel-for-pixel to the allocating path by `UprightFramesOnDeviceTest`.
 *
 * The bitmap returned is overwritten by the next call. That is safe for
 * MediaPipe, which copies a bitmap's pixels inside `detectAsync` - read from its
 * bytecode (`nativeCreateRgbaImage`) and pinned on the device by the same test.
 *
 * Analysis thread only.
 */
class UprightFrames {

    private var sensor: Bitmap? = null
    private var upright: Bitmap? = null
    private val transform = Matrix()
    private val placement = Matrix()
    private val bounds = RectF()
    private val canvas = Canvas()

    /**
     * `Bitmap.createBitmap(source, 0, 0, w, h, matrix, filter = false)` draws with
     * an unfiltered paint onto a fresh, transparent bitmap. Drawing with SRC onto
     * a kept one writes the same pixels whatever was there before.
     */
    private val paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }

    /** The upright frame, valid until the next call. */
    fun upright(image: ImageProxy, rotationDegrees: Int, mirror: Boolean): Bitmap {
        val source = sensorCopy(image)
        FrameUpright.matrix(rotationDegrees, mirror, transform)
        bounds.set(0f, 0f, source.width.toFloat(), source.height.toFloat())
        transform.mapRect(bounds)
        val width = bounds.width().roundToInt()
        val height = bounds.height().roundToInt()
        val target = upright?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                upright?.recycle()
                upright = it
            }
        placement.set(transform)
        placement.postTranslate(-bounds.left, -bounds.top)
        canvas.setBitmap(target)
        canvas.drawBitmap(source, placement, paint)
        canvas.setBitmap(null)
        return target
    }

    /**
     * The frame as CameraX delivered it, copied into the kept bitmap. An RGBA
     * frame whose rows are not tightly packed is left to CameraX's own
     * `toBitmap`, which handles any stride and allocates.
     */
    private fun sensorCopy(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        if (image.format != PixelFormat.RGBA_8888 || plane.pixelStride != 4 || plane.rowStride != image.width * 4) {
            return image.toBitmap()
        }
        val bitmap = sensor?.takeIf { it.width == image.width && it.height == image.height }
            ?: Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also {
                sensor?.recycle()
                sensor = it
            }
        val buffer = plane.buffer
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    /** Frees the kept bitmaps. Analysis thread, after the last frame. */
    fun release() {
        sensor?.recycle()
        upright?.recycle()
        sensor = null
        upright = null
    }
}

