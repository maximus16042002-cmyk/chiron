package com.kinesis.core.analysis.zoom

/**
 * Configuration policy for display-only focus zoom auto-framing.
 *
 * Implements FEATURE_PLAN.md Batch 1 (Focus zoom).
 *
 * @property maxZoom Maximum permitted zoom magnification (default 1.8x).
 * @property marginRatio Safety margin added around landmark bounding box (relative to box size).
 * @property headroomRatio Extra margin added above the head for overhead exercises.
 * @property enterHysteresisFrames Consecutive frames of reliable visibility required to zoom in (default 15, ~500ms).
 * @property exitHysteresisFrames Consecutive frames of lost/degraded visibility before zooming out (default 15, ~500ms).
 * @property smoothingAlpha Exponential moving average factor per frame (default 0.12 for smooth ~250ms easing).
 * @property isEnabled Whether focus zoom is enabled (can be disabled via settings or reduce motion).
 */
data class FocusZoomPolicy(
    val maxZoom: Double = 1.8,
    val marginRatio: Double = 0.20,
    val headroomRatio: Double = 0.25,
    val enterHysteresisFrames: Int = 15,
    val exitHysteresisFrames: Int = 15,
    val smoothingAlpha: Double = 0.12,
    val isEnabled: Boolean = true,
) {
    init {
        require(maxZoom >= 1.0) { "maxZoom must be >= 1.0, got $maxZoom" }
        require(marginRatio >= 0.0) { "marginRatio must be >= 0.0, got $marginRatio" }
        require(headroomRatio >= 0.0) { "headroomRatio must be >= 0.0, got $headroomRatio" }
        require(enterHysteresisFrames > 0) { "enterHysteresisFrames must be > 0" }
        require(exitHysteresisFrames > 0) { "exitHysteresisFrames must be > 0" }
        require(smoothingAlpha in 0.0..1.0) { "smoothingAlpha must be in [0, 1], got $smoothingAlpha" }
    }

    companion object {
        val DEFAULT: FocusZoomPolicy = FocusZoomPolicy()
        val DISABLED: FocusZoomPolicy = FocusZoomPolicy(isEnabled = false)
    }
}
