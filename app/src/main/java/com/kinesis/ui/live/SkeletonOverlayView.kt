package com.kinesis.ui.live

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.kinesis.core.analysis.kinematics.DrivingSignal
import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.SegmentInclination
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
 *
 * ### The angle arc and the rep flash - DECISIONS.md P3-76
 *
 * At the joint the exercise measures, each measured side gets an arc between its two bones and a
 * label with the core's angle - the filtered value counting is decided on, never computed here and
 * never averaged across sides. A counted repetition makes the figure glow briefly and a "+1" rise
 * from the head. Still nothing allocated per frame: labels come from a table built once
 * ([AngleGauge.label]), each arc is written into one reused [GaugeArc], and the float overloads of
 * `drawArc` and `drawRoundRect` take no `RectF`.
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

    /** What the exercise measures - told once by the engine (P3-76). `null` draws no arc. */
    @Volatile
    private var signal: DrivingSignal? = null

    /** The core's angle on each side for the frame held, `NaN` where unmeasured (P3-76). */
    private var angleLeft = Double.NaN
    private var angleRight = Double.NaN

    /** When the last repetition was counted, on the [SystemClock.uptimeMillis] clock (P3-76). */
    @Volatile
    private var countedAtMs: Long = RepFlash.NEVER

    private val gauge = GaugeArc()

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

    // --- P3-76: the angle arc, its label, and the rep flash -------------------------------

    private val density = resources.displayMetrics.density

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    /** The wedge between the bones: the joint's own state colour, faint. */
    private val arcFillPaints: Map<OverlayState, Paint> = OverlayState.entries.associateWith { state ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val base = state.argb.toInt()
            color = Color.argb(70, Color.red(base), Color.green(base), Color.blue(base))
            style = Paint.Style.FILL
        }
    }

    private val arcStrokePaints: Map<OverlayState, Paint> = OverlayState.entries.associateWith { state ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = state.argb.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeCap = Paint.Cap.ROUND
        }
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(14f)
        isFakeBoldText = true
    }

    /** A dark pill behind the number, so it reads over any background. */
    private val labelBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val plusOnePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(26f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f * density, 0f, 0f, Color.argb(200, 0, 0, 0))
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

    /** One frame, straight from the pose callback. */
    override fun submit(landmarks: Landmarks, state: OverlayState, policy: VisibilityPolicy) =
        submit(landmarks, state, policy, Double.NaN, Double.NaN)

    /** One frame with the core's angle on each side - DECISIONS.md P3-76. */
    override fun submit(
        landmarks: Landmarks,
        state: OverlayState,
        policy: VisibilityPolicy,
        angleLeft: Double,
        angleRight: Double,
    ) {
        synchronized(lock) {
            smoothing.update(landmarks, SystemClock.uptimeMillis(), policy)
            this.frame = landmarks
            this.overallState = state
            this.policy = policy
            this.angleLeft = angleLeft
            this.angleRight = angleRight
        }
        framesSubmitted++
        postInvalidateOnAnimation()
    }

    override fun measuring(signal: DrivingSignal) {
        this.signal = signal
    }

    override fun repetitionCounted() {
        countedAtMs = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    /** Clears the figure, for when tracking is lost entirely. */
    override fun clear() {
        synchronized(lock) {
            frame = null
            smoothing.reset()
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT)
        val w = width.toFloat()
        val h = height.toFloat()
        if (overallState != OverlayState.TRACKED) {
            drawFramingGuides(canvas, w, h)
        }
        val flashing = RepFlash.intensity(SystemClock.uptimeMillis(), countedAtMs) > 0f
        synchronized(lock) { drawFigure(canvas) }
        // Keep animating while the figure pulses, so the pulse does not freeze if
        // pose results stall. Stops by itself when the state changes. Likewise
        // while a repetition's flash fades (P3-76).
        if (overallState.pulses || flashing) postInvalidateOnAnimation()
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
        for (state in STATES) {
            paints.getValue(state).strokeWidth =
                state.strokeDp * density * SkeletonColouring.strokeScale(state, now)
        }

        // P3-76: a counted repetition's glow, under the figure.
        val flash = RepFlash.intensity(now, countedAtMs)
        if (flash > 0f) {
            flashPaint.alpha = (190 * flash).toInt()
            for (connection in CONNECTIONS) {
                val from = connection.first
                val to = connection.second
                val state = segmentState(landmarks, from, to)
                if (state == OverlayState.UNTRACKED) continue
                flashPaint.strokeWidth = paints.getValue(state).strokeWidth * (1f + 2.5f * flash)
                canvas.drawLine(
                    screenX(landmarks, from, w), screenY(landmarks, from, w),
                    screenX(landmarks, to, w), screenY(landmarks, to, w),
                    flashPaint,
                )
            }
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

        drawGauge(canvas, landmarks, w, left = true)
        drawGauge(canvas, landmarks, w, left = false)
        drawPlusOne(canvas, landmarks, w, now)
    }

    /**
     * One side's angle arc and label - DECISIONS.md P3-76. Drawn only where the core measured that
     * side: the number is the core's, the arc is where it was measured.
     */
    private fun drawGauge(canvas: Canvas, landmarks: Landmarks, w: Float, left: Boolean) {
        val measured = signal ?: return
        val label = AngleGauge.label(if (left) angleLeft else angleRight) ?: return
        val maxRadius = 46f * density
        val minRadius = 16f * density
        val textWidth = labelPaint.measureText(label)
        val halfW = textWidth / 2f + 7f * density
        // Past the arc by the label's own half-width, so the pill sits clear of both bones.
        val gap = 6f * density + halfW
        val state: OverlayState = when (measured) {
            is JointAngleId -> {
                val p = if (left) measured.proximal.leftId else measured.proximal.rightId
                val v = if (left) measured.vertex.leftId else measured.vertex.rightId
                val d = if (left) measured.distal.leftId else measured.distal.rightId
                AngleGauge.joint(
                    screenX(landmarks, p, w), screenY(landmarks, p, w),
                    screenX(landmarks, v, w), screenY(landmarks, v, w),
                    screenX(landmarks, d, w), screenY(landmarks, d, w),
                    maxRadius, minRadius, gap, gauge,
                )
                jointState(landmarks, v)
            }
            is SegmentInclination -> {
                val lower = if (left) measured.lower.leftId else measured.lower.rightId
                val upper = if (left) measured.upper.leftId else measured.upper.rightId
                AngleGauge.inclination(
                    screenX(landmarks, lower, w), screenY(landmarks, lower, w),
                    screenX(landmarks, upper, w), screenY(landmarks, upper, w),
                    maxRadius, minRadius, gap, gauge,
                )
                jointState(landmarks, lower)
            }
        }
        if (!gauge.visible) return
        val r = gauge.radius
        val cx = gauge.centreX
        val cy = gauge.centreY
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, gauge.startDeg, gauge.sweepDeg, true, arcFillPaints.getValue(state))
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, gauge.startDeg, gauge.sweepDeg, false, arcStrokePaints.getValue(state))

        val halfH = labelPaint.textSize * 0.72f
        val lx = gauge.labelX
        val ly = gauge.labelY
        canvas.drawRoundRect(lx - halfW, ly - halfH, lx + halfW, ly + halfH, halfH, halfH, labelBackPaint)
        canvas.drawText(label, lx - textWidth / 2f, ly + labelPaint.textSize * 0.35f, labelPaint)
    }

    /** "+1" rising from the head and fading, while a counted repetition's flash lasts (P3-76). */
    private fun drawPlusOne(canvas: Canvas, landmarks: Landmarks, w: Float, now: Long) {
        val progress = RepFlash.progress(now, countedAtMs)
        if (progress >= 1f) return
        if (landmarks.visibility(LandmarkId.NOSE) < policy.marginal) return
        val x = screenX(landmarks, LandmarkId.NOSE, w)
        val y = screenY(landmarks, LandmarkId.NOSE, w) - (40f + 50f * progress) * density
        plusOnePaint.alpha = (255 * (1f - progress)).toInt()
        canvas.drawText(PLUS_ONE, x, y, plusOnePaint)
    }

    // Drawn from the steadied positions; which state a joint is drawn in still
    // comes from the raw frame's visibility.
    @Suppress("UNUSED_PARAMETER")
    private fun screenX(landmarks: Landmarks, id: LandmarkId, width: Float): Float =
        OverlayGeometry.screenX(smoothing.x[id.index], displayMirrored, width)

    @Suppress("UNUSED_PARAMETER")
    private fun screenY(landmarks: Landmarks, id: LandmarkId, width: Float): Float =
        OverlayGeometry.screenY(smoothing.y[id.index], width)

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

        const val PLUS_ONE = "+1"

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
