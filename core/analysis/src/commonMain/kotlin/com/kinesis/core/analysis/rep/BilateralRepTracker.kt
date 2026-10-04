package com.kinesis.core.analysis.rep

import com.kinesis.core.analysis.kinematics.JointAngles
import com.kinesis.core.analysis.profile.AnalysedSides
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.analysis.temporal.RealTimeSignalPipeline
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.analysis.verifier.MovementContextTracker
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.rep.FormMeasures

/** Receives repetitions as they complete. A `fun interface`, so a lambda boxes nothing. */
fun interface RepSink {
    fun onRepetition(record: RepRecord)
}

/**
 * Counts repetitions for one exercise, keeping the two sides of the body apart.
 *
 * Accepts only a [RepetitionExercise]. An isometric hold cannot be passed here -
 * the call does not compile - which is how CLAUDE.md section 11's prohibition is
 * enforced rather than checked.
 *
 * ### Two machines, never an average
 *
 * There is one [RepFsm] per side, each fed by its own [RealTimeSignalPipeline],
 * so the left and right filter states are genuinely independent and an occlusion
 * on one side cannot perturb the other. No bilateral value is ever reduced to a
 * scalar to drive the counting - CLAUDE.md section 3.
 *
 * What differs between exercises is only how the two machines' cycles combine
 * into repetitions, which the profile declares:
 *
 * - [RepetitionSemantics.PER_LIMB] — each side's cycle is its own repetition and
 *   carries the side that performed it. Five curls on each arm is ten
 *   repetitions, matching MM-Fit's label definition (DECISIONS.md P3-1).
 * - [RepetitionSemantics.SIMULTANEOUS] — the two sides perform one repetition
 *   together, counted once, with both sides' range of motion retained so
 *   asymmetry stays measurable.
 *
 * ### Per frame
 *
 * [onFrame] is O(1): one joint angle, two pipeline pushes, two FSM steps. It
 * reports through a [RepSink] rather than returning a collection, because both
 * sides can complete on the same frame and allocating a list per frame to say
 * "nothing happened" is what the P2 budget guards exist to prevent.
 */
class BilateralRepTracker(
    val profile: RepetitionExercise,
    val temporalConfig: TemporalConfig = TemporalConfig.PROVISIONAL,
    bufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
) {

    private val leftPipeline = RealTimeSignalPipeline(temporalConfig, bufferCapacity)
    private val rightPipeline = RealTimeSignalPipeline(temporalConfig, bufferCapacity)

    // DECISIONS.md P3-23: each side carries a repetition across a short gap.
    private val leftFsm = RepFsm(profile.repSignal, bufferCapacity, temporalConfig.resampleLength, temporalConfig.maxBridgedGapMs)
    private val rightFsm = RepFsm(profile.repSignal, bufferCapacity, temporalConfig.resampleLength, temporalConfig.maxBridgedGapMs)

    // DECISIONS.md P3-44: the joints the profile's form measures need, per side, every frame.
    private val form = FormTracker(profile, bufferCapacity)

    // DECISIONS.md P3-52: multi-joint context tracker for exercise verification.
    private val movementContextTracker = MovementContextTracker(bufferCapacity)

    /** Repetitions counted so far, by this profile's own definition. */
    var repetitionCount: Int = 0
        private set

    /**
     * Records reported so far, valid or not - the source of [RepRecord.repIndex],
     * which is the position in the set counting from 1. Kept apart from
     * [repetitionCount]: numbering records by the valid count gave every record
     * before the first valid one the number 0, and a rejected record the number
     * of the valid one before it. DECISIONS.md P3-19.
     */
    private var recordsReported: Int = 0

    private fun nextPosition(): Int = ++recordsReported

    /**
     * Each side's phase, separately. There is deliberately no combined `phase`
     * property: showing one phase for two limbs is a reduction, and CLAUDE.md
     * section 3 wants it named where it happens - [BilateralPhase].
     */
    val leftPhase: MovementPhase get() = leftFsm.phase
    val rightPhase: MovementPhase get() = rightFsm.phase

    /**
     * Each side's joint angle as measured on the last frame, before filtering -
     * the value the outlier gate judges. `NaN` where that side was not measured.
     * Diagnostic only: nothing counts, stores or judges from it. DECISIONS.md P3-17.
     */
    var rawAngleLeft: Double = Double.NaN
        private set
    var rawAngleRight: Double = Double.NaN
        private set

    /**
     * A side's cycle waiting for the other side to catch up, under
     * [RepetitionSemantics.SIMULTANEOUS]. At most one is ever held.
     */
    private var pendingLeft: RepCycle? = null
    private var pendingRight: RepCycle? = null

    /**
     * Whether a repetition waits for both sides to complete.
     *
     * A simultaneous exercise normally does. One whose far limb is behind the
     * body from its required view never can - the partner cycle is not late, it
     * does not exist - so waiting for it reports nothing at all. Decided once
     * here rather than per frame, from two values fixed at construction.
     * DECISIONS.md P3-8.
     */
    private val pairsSides: Boolean =
        profile.repetitionSemantics == RepetitionSemantics.SIMULTANEOUS &&
            profile.analysedSides == AnalysedSides.BOTH

    /**
     * End of the last VALID repetition counted under [AnalysedSides.NEAR_SIDE_ONLY].
     *
     * If the far limb does become briefly measurable and completes a cycle
     * overlapping one already counted, that is the same repetition seen twice,
     * not a second one.
     *
     * Only a counted repetition sets it - DECISIONS.md P3-29. When a failed
     * attempt set it too, a far side flickering in and out gave up first,
     * claimed the time, and the near side's VALID repetition overlapping it was
     * dropped without a record: on MM-Fit that cost 49 push-ups and 117 sit-ups
     * (P3-28).
     */
    private var lastNearSideCountedEndMs: Double = Double.NaN

    fun onFrame(
        timestampMs: Double,
        landmarks: Landmarks,
        policy: VisibilityPolicy,
        sink: RepSink,
    ) {
        val angle = JointAngles.compute(
            landmarks,
            profile.repSignal.joint,
            profile.repSignal.plane,
            policy,
        )
        rawAngleLeft = angle.left
        rawAngleRight = angle.right
        // Before the machines step, so the frame that completes a repetition is inside its window.
        form.push(timestampMs, landmarks, policy, angle.left, angle.right)
        movementContextTracker.push(timestampMs, landmarks, policy)

        val left = leftFsm.step(timestampMs, leftPipeline.push(timestampMs, angle.left))
        val right = rightFsm.step(timestampMs, rightPipeline.push(timestampMs, angle.right))

        when (profile.repetitionSemantics) {
            RepetitionSemantics.PER_LIMB -> {
                if (left != null) emitPerLimb(left, Side.LEFT, sink)
                if (right != null) emitPerLimb(right, Side.RIGHT, sink)
            }

            RepetitionSemantics.SIMULTANEOUS -> if (pairsSides) {
                if (left != null) {
                    if (pendingLeft != null) flushUnpairedSimultaneous(sink)
                    pendingLeft = left
                }
                if (right != null) {
                    if (pendingRight != null) flushUnpairedSimultaneous(sink)
                    pendingRight = right
                }
                tryPairSimultaneous(sink)
            } else {
                if (left != null) emitNearSide(left, Side.LEFT, sink)
                if (right != null) emitNearSide(right, Side.RIGHT, sink)
            }
        }
    }

    /** Ends the set, reporting any repetition still in progress as incomplete. */
    fun finish(timestampMs: Double, sink: RepSink) {
        val left = leftFsm.finish(timestampMs)
        val right = rightFsm.finish(timestampMs)
        when (profile.repetitionSemantics) {
            RepetitionSemantics.PER_LIMB -> {
                if (left != null) emitPerLimb(left, Side.LEFT, sink)
                if (right != null) emitPerLimb(right, Side.RIGHT, sink)
            }

            RepetitionSemantics.SIMULTANEOUS -> if (pairsSides) {
                if (left != null) {
                    if (pendingLeft != null) flushUnpairedSimultaneous(sink)
                    pendingLeft = left
                }
                if (right != null) {
                    if (pendingRight != null) flushUnpairedSimultaneous(sink)
                    pendingRight = right
                }
                tryPairSimultaneous(sink)
                flushUnpairedSimultaneous(sink)
            } else {
                if (left != null) emitNearSide(left, Side.LEFT, sink)
                if (right != null) emitNearSide(right, Side.RIGHT, sink)
            }
        }
    }

    fun reset() {
        leftPipeline.reset()
        rightPipeline.reset()
        leftFsm.reset()
        rightFsm.reset()
        repetitionCount = 0
        recordsReported = 0
        rawAngleLeft = Double.NaN
        rawAngleRight = Double.NaN
        form.reset()
        movementContextTracker.reset()
        pendingLeft = null
        pendingRight = null
        lastNearSideCountedEndMs = Double.NaN
    }

    // --- Combining the two sides ---------------------------------------------------

    /**
     * Reports a repetition seen on one limb of a two-limbed movement, for a
     * profile whose required view can only ever show one - DECISIONS.md P3-8.
     *
     * Unlike [emitPerLimb] the repetition is **not** attributed to a side: both
     * arms of a bent-over row pull together, so the repetition belongs to no one
     * limb and only half of it was measured. The unmeasured side stays
     * [Double.NaN], which every reducer on [BilateralSignal] propagates, so no
     * finite asymmetry exists for anything downstream to report - which is
     * FINAL_ARCHITECTURE section 5's "no per-side comparison, no symmetry claim",
     * enforced rather than requested.
     *
     * A cycle overlapping a counted repetition is dropped, whatever its validity.
     * Any other is reported; a failed attempt is reported but blocks nothing -
     * DECISIONS.md P3-29.
     */
    private fun emitNearSide(cycle: RepCycle, side: Side, sink: RepSink) {
        if (!lastNearSideCountedEndMs.isNaN() && cycle.startMs < lastNearSideCountedEndMs) return
        if (cycle.validity == RepValidity.VALID) lastNearSideCountedEndMs = cycle.endMs
        emitOneSided(cycle, side, attributed = null, sink = sink)
    }

    private fun emitPerLimb(cycle: RepCycle, side: Side, sink: RepSink) =
        emitOneSided(cycle, side, attributed = side, sink = sink)

    private fun emitOneSided(
        cycle: RepCycle,
        side: Side,
        attributed: Side?,
        sink: RepSink,
    ) {
        if (cycle.validity == RepValidity.VALID) repetitionCount++
        val measured = BilateralSignal(
            left = if (side == Side.LEFT) cycle.rangeOfMotion else Double.NaN,
            right = if (side == Side.RIGHT) cycle.rangeOfMotion else Double.NaN,
        )
        val peak = BilateralSignal(
            left = if (side == Side.LEFT) cycle.peakValue else Double.NaN,
            right = if (side == Side.RIGHT) cycle.peakValue else Double.NaN,
        )
        val trough = BilateralSignal(
            left = if (side == Side.LEFT) cycle.troughValue else Double.NaN,
            right = if (side == Side.RIGHT) cycle.troughValue else Double.NaN,
        )
        val blank = DoubleArray(cycle.resampled.size) { Double.NaN }

        sink.onRepetition(
            RepRecord(
                repIndex = nextPosition(),
                attributedSide = attributed,
                startMs = cycle.startMs,
                endMs = cycle.endMs,
                validity = cycle.validity,
                rangeOfMotion = measured,
                peakValue = peak,
                troughValue = trough,
                descentMs = cycle.descentMs,
                ascentMs = cycle.ascentMs,
                resampledLeft = if (side == Side.LEFT) cycle.resampled else blank,
                resampledRight = if (side == Side.RIGHT) cycle.resampled else blank,
                form = if (side == Side.LEFT) formOf(cycle, null) else formOf(null, cycle),
                movementContext = movementContextTracker.extract(cycle.startMs, cycle.endMs),
            ),
        )
    }

    /**
     * The form summary for a record: each side over its own cycle's window, the side with no
     * cycle not measured - DECISIONS.md P3-44. Never one side's values on the other's.
     */
    private fun formOf(left: RepCycle?, right: RepCycle?): FormMeasures {
        if (profile.formMeasures.isEmpty()) return FormMeasures.NONE
        val l = if (left != null) form.summarise(Side.LEFT, left.startMs, left.endMs) else emptyMap()
        val r = if (right != null) form.summarise(Side.RIGHT, right.startMs, right.endMs) else emptyMap()
        return FormMeasures(profile.formMeasures.associateWith { BilateralSignal(l[it] ?: Double.NaN, r[it] ?: Double.NaN) })
    }

    /**
     * Pairs one cycle from each side into a single repetition.
     *
     * Both sides move together in a simultaneous exercise, so their cycles
     * complete within a frame or two of each other. Waiting for both is what
     * keeps the repetition count honest when one knee tracks poorly for a moment:
     * the count does not double, and the poorly-tracked side shows up as its own
     * range of motion rather than as an extra repetition.
     */
    private fun tryPairSimultaneous(sink: RepSink) {
        val left = pendingLeft ?: return
        val right = pendingRight ?: return
        // Two halves that never shared a moment are not one repetition. Without
        // this, a side's cycle waited for the other side's next one however late,
        // and the pair became one record spanning both: 93.5 s on the phone, and
        // two valid one-sided halves a minute apart were counted as a squat.
        // The older half is reported alone; the newer waits for its own partner.
        // DECISIONS.md P3-19.
        if (!overlaps(left, right)) {
            flushOrphan(if (left.endMs <= right.endMs) Side.LEFT else Side.RIGHT, sink)
            return
        }
        pendingLeft = null
        pendingRight = null

        // Unseen outranks unfinished outranks turned-back. An INCOMPLETE half -
        // the set ended with that side still moving - makes the repetition
        // INCOMPLETE; it used to fall through to ABORTED, which told the user
        // they turned back early when the truth was that the set stopped.
        // DECISIONS.md P3-16.
        val validity = if (left.validity == RepValidity.VALID && right.validity == RepValidity.VALID) {
            RepValidity.VALID
        } else if (left.validity == RepValidity.INSUFFICIENT_DATA ||
            right.validity == RepValidity.INSUFFICIENT_DATA
        ) {
            RepValidity.INSUFFICIENT_DATA
        } else if (left.validity == RepValidity.INCOMPLETE || right.validity == RepValidity.INCOMPLETE) {
            RepValidity.INCOMPLETE
        } else {
            RepValidity.ABORTED
        }
        if (validity == RepValidity.VALID) repetitionCount++

        val start = if (left.startMs <= right.startMs) left.startMs else right.startMs
        val end = if (left.endMs >= right.endMs) left.endMs else right.endMs

        sink.onRepetition(
            RepRecord(
                repIndex = nextPosition(),
                attributedSide = null,
                startMs = start,
                endMs = end,
                validity = validity,
                rangeOfMotion = BilateralSignal(left.rangeOfMotion, right.rangeOfMotion),
                peakValue = BilateralSignal(left.peakValue, right.peakValue),
                troughValue = BilateralSignal(left.troughValue, right.troughValue),
                // Phase durations are taken from the side that was measurable for
                // longer. A named choice, not an average of two timings that may
                // describe different amounts of the movement.
                descentMs = longerOf(left.descentMs, right.descentMs),
                ascentMs = longerOf(left.ascentMs, right.ascentMs),
                resampledLeft = left.resampled,
                resampledRight = right.resampled,
                form = formOf(left, right),
                movementContext = movementContextTracker.extract(start, end),
            ),
        )
    }

    /**
     * Reports a side whose partner never completed.
     *
     * One knee occluded for the whole repetition should not erase it - the
     * movement happened and half of it was measured. It is emitted with the
     * unmeasured side marked absent, so a reader can see that it was half-seen
     * rather than assume a symmetric measurement.
     */
    private fun flushUnpairedSimultaneous(sink: RepSink) {
        val left = pendingLeft
        val right = pendingRight
        if (left != null && right != null) {
            // Only reachable if both are held without overlapping; oldest first.
            val first = if (left.endMs <= right.endMs) Side.LEFT else Side.RIGHT
            flushOrphan(first, sink)
            flushOrphan(if (first == Side.LEFT) Side.RIGHT else Side.LEFT, sink)
        } else if (left != null) {
            flushOrphan(Side.LEFT, sink)
        } else if (right != null) {
            flushOrphan(Side.RIGHT, sink)
        }
    }

    /** Whether two cycles' time spans share at least one instant. */
    private fun overlaps(a: RepCycle, b: RepCycle): Boolean = a.startMs <= b.endMs && b.startMs <= a.endMs

    private fun flushOrphan(side: Side, sink: RepSink) {
        val orphan = (if (side == Side.LEFT) pendingLeft else pendingRight) ?: return
        if (side == Side.LEFT) pendingLeft = null else pendingRight = null

        val blank = DoubleArray(orphan.resampled.size) { Double.NaN }
        sink.onRepetition(
            RepRecord(
                repIndex = nextPosition(),
                attributedSide = null,
                startMs = orphan.startMs,
                endMs = orphan.endMs,
                validity = RepValidity.INSUFFICIENT_DATA,
                rangeOfMotion = sided(side, orphan.rangeOfMotion),
                peakValue = sided(side, orphan.peakValue),
                troughValue = sided(side, orphan.troughValue),
                descentMs = orphan.descentMs,
                ascentMs = orphan.ascentMs,
                resampledLeft = if (side == Side.LEFT) orphan.resampled else blank,
                resampledRight = if (side == Side.RIGHT) orphan.resampled else blank,
                form = if (side == Side.LEFT) formOf(orphan, null) else formOf(null, orphan),
                movementContext = movementContextTracker.extract(orphan.startMs, orphan.endMs),
            ),
        )
    }

    private fun sided(side: Side, value: Double): BilateralSignal = BilateralSignal(
        left = if (side == Side.LEFT) value else Double.NaN,
        right = if (side == Side.RIGHT) value else Double.NaN,
    )

    private fun longerOf(a: Double, b: Double): Double = when {
        a.isNaN() -> b
        b.isNaN() -> a
        a >= b -> a
        else -> b
    }

    companion object {
        /**
         * Frames of history each side keeps. At 30 fps this is roughly seventeen
         * seconds, comfortably longer than any single repetition and bounded.
         */
        const val DEFAULT_BUFFER_CAPACITY: Int = 512
    }
}
