package com.kinesis.camera

import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import java.nio.ByteBuffer

/**
 * A 640x480-style RGBA_8888 frame shaped as CameraX delivers one for
 * `OUTPUT_IMAGE_FORMAT_RGBA_8888`: one plane, four bytes a pixel, rows tightly
 * packed, opaque. `toBitmap` is CameraX's own default method, so its real
 * conversion runs on it. [seed] varies the content, so two frames differ.
 */
internal class SyntheticRgbaFrame(
    private val width: Int,
    private val height: Int,
    seed: Int = 0,
) : ImageProxy {

    // Not called `buffer`: inside the plane below, that name is the plane's own
    // getBuffer(), and a first version recursed until the stack ran out.
    private val pixels = ByteBuffer.allocateDirect(width * height * 4).apply {
        for (i in 0 until width * height) {
            put(((i + seed) % 251).toByte())
            put(((i * 3 + seed) % 241).toByte())
            put(((i * 7 + seed) % 239).toByte())
            put(0xFF.toByte())
        }
        rewind()
    }

    private val plane = object : ImageProxy.PlaneProxy {
        override fun getRowStride(): Int = width * 4
        override fun getPixelStride(): Int = 4
        override fun getBuffer(): ByteBuffer = pixels.apply { rewind() }
    }

    private val info = object : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
        override fun getTimestamp(): Long = 0L
        override fun getRotationDegrees(): Int = 0
        override fun populateExifData(exifBuilder: ExifData.Builder) = Unit
    }

    private var crop = Rect(0, 0, width, height)

    override fun close() = Unit
    override fun getCropRect(): Rect = crop
    override fun setCropRect(rect: Rect?) {
        crop = rect ?: Rect(0, 0, width, height)
    }
    override fun getFormat(): Int = PixelFormat.RGBA_8888
    override fun getHeight(): Int = height
    override fun getWidth(): Int = width
    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(plane)
    override fun getImageInfo(): ImageInfo = info
    override fun getImage(): Image? = null
}
