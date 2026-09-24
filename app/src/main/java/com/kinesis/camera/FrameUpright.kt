package com.kinesis.camera

import android.graphics.Bitmap
import android.graphics.Matrix

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
