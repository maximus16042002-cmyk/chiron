package com.kinesis.ui.live

/**
 * Where the skeleton overlay draws a landmark - DECISIONS.md P3-16 and P3-17.
 *
 * Pure, so it is tested on the JVM together with the capture decisions it has to
 * agree with (`CameraFacingParityTest`). Landmarks arrive with both axes in frame
 * widths (P3-16), so both scale by the view's width; the live screen sizes the
 * view to the frame's shape, which is what makes that land on the preview.
 */
object OverlayGeometry {

    /**
     * @param mirrored whether the preview beneath is shown mirrored - the front
     *   camera's is, the back camera's is not - so the skeleton must be too.
     */
    fun screenX(x: Double, mirrored: Boolean, viewWidth: Float): Float =
        ((if (mirrored) 1.0 - x else x) * viewWidth).toFloat()

    fun screenY(y: Double, viewWidth: Float): Float = (y * viewWidth).toFloat()
}
