package com.kinesis.core.analysis.zoom

/**
 * Display-only framing target in normalized camera coordinates [0.0, 1.0].
 *
 * Implements FEATURE_PLAN.md Batch 1 (Focus zoom / auto-framing).
 * Pure multiplatform domain model; zero allocations per frame.
 *
 * @property scale Viewport zoom factor, where 1.0 is full unzoomed camera frame, and
 * values up to [FocusZoomPolicy.maxZoom] (e.g. 1.8) magnify the active subject.
 * @property centerX Normalized X center point of the framed region in [0.0, 1.0].
 * @property centerY Normalized Y center point of the framed region in [0.0, 1.0].
 */
data class FocusZoomTarget(
    val scale: Double,
    val centerX: Double,
    val centerY: Double,
) {
    init {
        require(scale >= 1.0) { "scale must be >= 1.0, got $scale" }
        require(centerX in 0.0..1.0) { "centerX must be in [0, 1], got $centerX" }
        require(centerY in 0.0..1.0) { "centerY must be in [0, 1], got $centerY" }
    }

    /**
     * Maps a full-frame normalized coordinate [x] in [0, 1] to zoomed display coordinate.
     */
    fun mapX(x: Double): Double = (x - centerX) * scale + 0.5

    /**
     * Maps a full-frame normalized coordinate [y] in [0, 1] to zoomed display coordinate.
     */
    fun mapY(y: Double): Double = (y - centerY) * scale + 0.5

    /**
     * Maps a zoomed display coordinate [zx] back to full-frame normalized coordinate.
     */
    fun unmapX(zx: Double): Double = (zx - 0.5) / scale + centerX

    /**
     * Maps a zoomed display coordinate [zy] back to full-frame normalized coordinate.
     */
    fun unmapY(zy: Double): Double = (zy - 0.5) / scale + centerY

    companion object {
        /** The unzoomed neutral identity target (1.0x centered at 0.5, 0.5). */
        val FULL_FRAME: FocusZoomTarget = FocusZoomTarget(scale = 1.0, centerX = 0.5, centerY = 0.5)
    }
}
