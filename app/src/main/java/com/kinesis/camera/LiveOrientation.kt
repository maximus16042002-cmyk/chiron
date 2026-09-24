package com.kinesis.camera

import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import android.view.Surface

/**
 * Which way up the live screen runs - DECISIONS.md P3-19, the owner's decision.
 *
 * Portrait either way up; never landscape. The owner's phone stands upside down
 * whenever a USB cable is in its bottom port. P3-18 found that with a plain
 * portrait lock the screen stayed put while the phone turned, so the preview and
 * every frame MediaPipe analysed showed the person upside down.
 *
 * ### Why not `SCREEN_ORIENTATION_SENSOR_PORTRAIT`
 *
 * It was the first choice, and on the owner's phone it would not work: the phone
 * reports `mAllowAllRotations=false`, and Android's rotation policy then refuses
 * the sensor's upside-down reading for every sensor-driven orientation except the
 * two "full" ones, which also allow landscape. So the live screen reads the tilt
 * itself and asks for fixed portrait or fixed reverse portrait, which the policy
 * honours regardless - see [requestFor]. Like sensor portrait, it ignores the
 * user's rotation lock while the live screen is open.
 *
 * Turning between the two does not restart the camera: [LiveCamera] hands the new
 * display rotation to CameraX, which applies it to the frames that follow, and
 * the preview redraws itself on the same display change. So a turn does not end
 * the set, unlike a camera switch.
 */
object LiveOrientation {

    /** Asked for when the live screen opens; the first tilt reading corrects it. */
    const val INITIAL: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    /**
     * How far from upright or upside down, in degrees, the phone may tilt and
     * still count as that. The two bands are 60 degrees apart at their nearest,
     * so the screen cannot flap between them; a tilt in the landscape bands
     * between keeps whatever was last asked for.
     */
    const val BAND_DEGREES: Int = 60

    /**
     * The orientation to ask for at [tiltDegrees], as `OrientationEventListener`
     * reports it (0 upright, 180 upside down, clockwise), or `null` to keep the
     * current one: the phone is in a landscape band, or flat and unknown.
     */
    fun requestFor(tiltDegrees: Int): Int? {
        if (tiltDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) return null
        val tilt = Math.floorMod(tiltDegrees, 360)
        return when {
            tilt <= BAND_DEGREES || tilt >= 360 - BAND_DEGREES -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            tilt in (180 - BAND_DEGREES)..(180 + BAND_DEGREES) -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            else -> null
        }
    }

    /**
     * Upright and upside down. Landscape is not supported: the camera area, the
     * frame shape and the overlay's geometry are all built for portrait.
     */
    fun isSupported(displayRotation: Int): Boolean =
        displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180
}
