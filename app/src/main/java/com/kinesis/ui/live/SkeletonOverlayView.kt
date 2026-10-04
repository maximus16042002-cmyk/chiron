package com.kinesis.ui.live

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.kinesis.core.analysis.zoom.FocusZoomTarget
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.ui.theme.OverlayState

/**
 * Draws the skeleton - DECISIONS.md P3-13.
 *
 * ### Why this is a `View` and not a composable
 *
 * CLAUDE.md section 6: *"MUST NOT: drive Compose recomposition from pose frames;
 * skeleton rendering goes to a dedicated Canvas/SurfaceView, separated from
 * low-frequency UI state."* FINAL_ARCHITECTURE section 8 says the same in budget
 * terms.
 *
 * So the pose callback calls [submit] directly and this invalidates itself.
 * Compose never sees a frame; it only sees what `LiveAnalysisBridge` or
 * `LiveHoldBridge` publishes, a few times a second at most. The two paths meet
 * nowhere.
 *
 * ### Allocation
 *
 * Nothing is allocated per frame. The frame is held by reference - the adapter
 * already hands over a fresh [Landmarks] that owns its buffer - the paints are
 * built once, and [onDraw] walks a fixed connection table. A `Paint` allocated
 * per frame would be thirty per second feeding the garbage collector during
 * exactly the window where a pause shows.
 */
class SkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), SkeletonSink {

    /**
     * Draw x reflected, for a mirrored preview - DECISIONS.md P3-15 and P3-17.
     *
     * Landmarks arrive anatomical, with x in unmirrored image coordinates
     * whichever camera took them. The front-camera preview is shown mirrored and
     * the back camera's is not, so this follows `LiveCamera.previewIsMirrored`.
     * Drawing only: no landmark and no measurement changes.
     */
    var displayMirrored: Boolean = false

    /**
     * The latest frame, held by reference rather than copied.
     *
     * `PoseAdapter.toAnatomical` already returns a fresh [Landmarks] per frame
     * that owns its own buffer, so retaining it costs nothing and copying it
     * again would add an allocation per frame for no gain.
     */
    private var frame: Landmarks? = null
    private var policy = VisibilityPolicy.PROVISIONAL

    /**
     * The state the whole skeleton is drawn in when it is not per-joint - the
     * attention colour applies to the figure, because an abandoned repetition is
     * a fact about the movement rather than about one joint.
     */
    private var overallState: OverlayState = OverlayState.TRACKED

    private val paints: Map<OverlayState, Paint> = OverlayState.entries.associateWith { state ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = state.argb.toInt()
            style = Paint.Style.STROKE
            strokeWidth = state.strokeDp * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
        }
    }

    private val jointPaints: Map<OverlayState, Paint> = OverlayState.entries.associateWith { state ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = state.argb.toInt()
            style = Paint.Style.FILL
        }
    }

    private val jointHaloPaints: Map<OverlayState, Paint> = OverlayState.entries.associateWith { state ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val base = state.argb.toInt()
            color = Color.argb(55, Color.red(base), Color.green(base), Color.blue(base))
            style = Paint.Style.FILL
        }
    }

    private val framingGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    /** Frames drawn. Instrumentation for Phase B's frame-rate check. */
    var framesSubmitted: Long = 0
        private set

    /**
     * The positions actually drawn: MediaPipe's, steadied for the eye. The frame
     * itself - what the analysis measured - is never changed. See
     * [SkeletonSmoothing].
     */
    private val smoothing = SkeletonSmoothing()

    /**
     * Held while positions are written (pose thread) and while they are drawn
     * (main thread), so a draw never mixes two frames. Both are short and
     * allocate nothing.
     */
    private val lock = Any()

    private var zoomTarget: FocusZoomTarget = FocusZoomTarget.FULL_FRAME

    /** One frame, straight from the pose callback. */
    override fun submit(landmarks: Landmarks, state: OverlayState, policy: VisibilityPolicy) {
        submit(landmarks, state, policy, FocusZoomTarget.FULL_FRAME)
    }

    override fun submit(
        landmarks: Landmarks,
        state: OverlayState,
        policy: VisibilityPolicy,
        zoomTarget: FocusZoomTarget,
    ) {
        synchronized(lock) {
            smoothing.update(landmarks, SystemClock.uptimeMillis(), policy)
            this.frame = landmarks
            this.overallState = state
            this.policy = policy
            this.zoomTarget = zoomTarget
        }
        framesSubmitted++
        postInvalidateOnAnimation()
    }

    /** Clears the figure, for when tracking is lost entirely. */
    override fun clear() {
        synchronized(lock) {
            frame = null
            zoomTarget = FocusZoomTarget.FULL_FRAME
            smoothing.reset()
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT)
        val w = width.toFloat()
        val h = height.toFloat()
        if (overallState != OverlayState.TRACKED || zoomTarget.scale > 1.05) {
            drawFramingGuides(canvas, w, h)
        }
        synchronized(lock) { drawFigure(canvas) }
        // Keep animating while the figure pulses, so the pulse does not freeze if
        // pose results stall. Stops by itself when the state changes.
        if (overallState.pulses) postInvalidateOnAnimation()
    }

    private fun drawFramingGuides(canvas: Canvas, w: Float, h: Float) {
        val left = w * 0.08f
        val right = w * 0.92f
        val top = h * 0.05f
        val bottom = h * 0.95f
        val arm = w * 0.07f

        // Top-left
        canvas.drawLine(left, top, left + arm, top, framingGuidePaint)
        canvas.drawLine(left, top, left, top + arm, framingGuidePaint)
        // Top-right
        canvas.drawLine(right, top, right - arm, top, framingGuidePaint)
        canvas.drawLine(right, top, right, top + arm, framingGuidePaint)
        // Bottom-left
        canvas.drawLine(left, bottom, left + arm, bottom, framingGuidePaint)
        canvas.drawLine(left, bottom, left, bottom - arm, framingGuidePaint)
        // Bottom-right
        canvas.drawLine(right, bottom, right - arm, bottom, framingGuidePaint)
        canvas.drawLine(right, bottom, right, bottom - arm, framingGuidePaint)
    }

    private fun drawFigure(canvas: Canvas) {
        val landmarks = frame ?: return

        val w = width.toFloat()

        // The pulse - P3-12's third channel for the attention state, decided by
        // SkeletonColouring.strokeScale so it can be tested without a View. Every
        // state's width is set each draw; only a state that pulses ever changes.
        val now = SystemClock.uptimeMillis()
        val density = resources.displayMetrics.density
        for (state in STATES) {
            paints.getValue(state).strokeWidth =
                state.strokeDp * density * SkeletonColouring.strokeScale(state, now)
        }

        for (connection in CONNECTIONS) {
            val from = connection.first
            val to = connection.second
            val state = segmentState(landmarks, from, to)
            canvas.drawLine(
                screenX(landmarks, from, w), screenY(landmarks, from, w),
                screenX(landmarks, to, w), screenY(landmarks, to, w),
                paints.getValue(state),
            )
        }

        // Head indicator circle at nose position
        if (landmarks.visibility(LandmarkId.NOSE) >= policy.marginal) {
            val noseState = jointState(landmarks, LandmarkId.NOSE)
            val headRadius = paints.getValue(noseState).strokeWidth * 2.2f
            val noseX = screenX(landmarks, LandmarkId.NOSE, w)
            val noseY = screenY(landmarks, LandmarkId.NOSE, w)
            canvas.drawCircle(noseX, noseY, headRadius * 1.35f, jointHaloPaints.getValue(noseState))
            canvas.drawCircle(noseX, noseY, headRadius, paints.getValue(noseState))
        }

        for (id in DRAWN_JOINTS) {
            val state = jointState(landmarks, id)
            val strokeW = paints.getValue(state).strokeWidth
            val radius = strokeW * 0.85f
            val x = screenX(landmarks, id, w)
            val y = screenY(landmarks, id, w)
            canvas.drawCircle(x, y, radius * 2.2f, jointHaloPaints.getValue(state))
            canvas.drawCircle(x, y, radius, jointPaints.getValue(state))
        }
    }

    // Drawn from the steadied positions; which state a joint is drawn in still
    // comes from the raw frame's visibility.
    @Suppress("UNUSED_PARAMETER")
    private fun screenX(landmarks: Landmarks, id: LandmarkId, width: Float): Float =
        OverlayGeometry.screenX(smoothing.x[id.index], displayMirrored, width, zoomTarget)

    @Suppress("UNUSED_PARAMETER")
    private fun screenY(landmarks: Landmarks, id: LandmarkId, width: Float): Float =
        OverlayGeometry.screenY(smoothing.y[id.index], width, zoomTarget)

    // --- Which state a joint is drawn in ------------------------------------------

    private fun jointState(landmarks: Landmarks, id: LandmarkId): OverlayState =
        SkeletonColouring.jointState(landmarks.visibility(id), policy, overallState)

    private fun segmentState(
        landmarks: Landmarks,
        from: LandmarkId,
        to: LandmarkId,
    ): OverlayState = SkeletonColouring.segmentState(
        jointState(landmarks, from),
        jointState(landmarks, to),
    )

    private companion object {

        /** An array, so iterating it each draw allocates no iterator. */
        val STATES: Array<OverlayState> = OverlayState.entries.toTypedArray()

        /**
         * The figure, as landmark pairs. Torso, arms and legs - the parts every
         * profile in the library measures. Face landmarks are deliberately not
         * drawn: no profile uses them, and a skeleton with a face on it invites
         * the user to think the system is looking at more than it is.
         */
        val CONNECTIONS: Array<Pair<LandmarkId, LandmarkId>> = arrayOf(
            LandmarkId.LEFT_SHOULDER to LandmarkId.RIGHT_SHOULDER,
            LandmarkId.LEFT_SHOULDER to LandmarkId.LEFT_HIP,
            LandmarkId.RIGHT_SHOULDER to LandmarkId.RIGHT_HIP,
            LandmarkId.LEFT_HIP to LandmarkId.RIGHT_HIP,

            LandmarkId.LEFT_SHOULDER to LandmarkId.LEFT_ELBOW,
            LandmarkId.LEFT_ELBOW to LandmarkId.LEFT_WRIST,
            LandmarkId.RIGHT_SHOULDER to LandmarkId.RIGHT_ELBOW,
            LandmarkId.RIGHT_ELBOW to LandmarkId.RIGHT_WRIST,

            LandmarkId.LEFT_HIP to LandmarkId.LEFT_KNEE,
            LandmarkId.LEFT_KNEE to LandmarkId.LEFT_ANKLE,
            LandmarkId.RIGHT_HIP to LandmarkId.RIGHT_KNEE,
            LandmarkId.RIGHT_KNEE to LandmarkId.RIGHT_ANKLE,
        )

        /** The joints the figure above actually uses. */
        val DRAWN_JOINTS: Array<LandmarkId> = CONNECTIONS
            .flatMap { listOf(it.first, it.second) }
            .distinct()
            .toTypedArray()
    }
}
