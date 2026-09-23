package com.kinesis.ui.live

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
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
     * Draw x reflected, for a mirrored preview - DECISIONS.md P3-15.
     *
     * Landmarks arrive anatomical, with x back in unmirrored image coordinates
     * (`PoseAdapter` reflected it). The front-camera preview is shown mirrored,
     * so without this the skeleton would be drawn on the wrong side of the
     * user's body. Drawing only: no landmark and no measurement changes.
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

    /** Frames drawn. Instrumentation for Phase B's frame-rate check. */
    var framesSubmitted: Long = 0
        private set

    /** One frame, straight from the pose callback. */
    override fun submit(landmarks: Landmarks, state: OverlayState, policy: VisibilityPolicy) {
        this.frame = landmarks
        this.overallState = state
        this.policy = policy
        framesSubmitted++
        postInvalidateOnAnimation()
    }

    /** Clears the figure, for when tracking is lost entirely. */
    override fun clear() {
        frame = null
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT)
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

        for (id in DRAWN_JOINTS) {
            val state = jointState(landmarks, id)
            val radius = paints.getValue(state).strokeWidth * 0.8f
            canvas.drawCircle(
                screenX(landmarks, id, w),
                screenY(landmarks, id, w),
                radius,
                jointPaints.getValue(state),
            )
        }

        // Keep animating while the figure pulses, so the pulse does not freeze if
        // pose results stall. Stops by itself when the state changes.
        if (overallState.pulses) postInvalidateOnAnimation()
    }

    /*
     * Landmarks arrive with both axes in frame widths (DECISIONS.md P3-16), so
     * both scale by the view's width. The live screen sizes this view to the
     * analysis frame's shape, which is what makes that land on the preview.
     */
    private fun screenX(landmarks: Landmarks, id: LandmarkId, width: Float): Float {
        val x = landmarks.x(id).toFloat()
        return (if (displayMirrored) 1f - x else x) * width
    }

    private fun screenY(landmarks: Landmarks, id: LandmarkId, width: Float): Float =
        landmarks.y(id).toFloat() * width

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
