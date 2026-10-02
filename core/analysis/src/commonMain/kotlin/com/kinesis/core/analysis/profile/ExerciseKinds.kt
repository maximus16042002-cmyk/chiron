package com.kinesis.core.analysis.profile

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.domain.profile.AnalysisMode
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.rep.FormMeasure

/**
 * How a set of an exercise turns into repetitions - CLAUDE.md section 11.
 *
 * Declared explicitly on every repetition profile. There is no default, because
 * a default is how this gets wrong silently, and getting it wrong is a
 * factor-of-two error in every rep count the exercise ever produces.
 */
enum class RepetitionSemantics {

    /**
     * One limb's movement is one repetition. The arms or legs alternate, and a
     * set of ten is five on each side.
     *
     * This is what MM-Fit's labels define for bicep curls, measured from its
     * label files - each arm completes exactly five cycles per labelled
     * ten-repetition set, in antiphase. See DECISIONS.md P3-1.
     */
    PER_LIMB,

    /**
     * Both sides perform the same repetition together, and it counts once. A
     * squat is one repetition, not one per leg.
     */
    SIMULTANEOUS,
}

/**
 * Which sides of the body the required camera view can actually measure.
 *
 * Separate from [RepetitionSemantics] because they answer different questions.
 * Semantics is a fact about the **movement** - do the limbs work together or in
 * turn. This is a fact about the **view** - can the camera see both of them.
 * A bent-over row is a two-armed movement that a side-on camera can only ever
 * see one arm of, and collapsing the two axes into one enum would make that
 * inexpressible.
 *
 * Declared explicitly on every repetition profile, with no default, for the same
 * reason [RepetitionSemantics] has none: a silent default is how this gets wrong.
 * DECISIONS.md P3-8.
 */
enum class AnalysedSides {

    /** Both limbs are visible from the required view, and both are measured. */
    BOTH,

    /**
     * Only the limb nearer the camera is measurable. The far one is behind the
     * body from the required view for the whole movement, by construction rather
     * than by accident.
     *
     * This is **not** the same as the intermittent occlusion the rep tracker
     * already handles by reporting `INSUFFICIENT_DATA`. That case is a
     * two-sided movement whose far side drops out for a while; this one is a
     * two-sided movement whose far side is never there at all, so waiting for it
     * means waiting forever.
     *
     * FINAL_ARCHITECTURE section 5, on the bent-over row: "Keep it, but near-side
     * only, and say so explicitly - no per-side comparison, no symmetry claim."
     */
    NEAR_SIDE_ONLY,
}

/**
 * Whether a phase of a repetition works the muscle shortening (concentric - the
 * lift) or lengthening under load (eccentric - the lowering). DECISIONS.md P3-26
 * and P3-30.
 *
 * A record's `descentMs` is the time moving away from the starting position and
 * `ascentMs` the time returning to it. Which of those is the lift depends on the
 * exercise: a curl moves away from the start by lifting, a squat by lowering. So
 * each repetition profile says which, with no default, for the reason
 * [RepetitionSemantics] has none.
 */
enum class Contraction {
    CONCENTRIC,
    ECCENTRIC,
}

/**
 * Which signal drives repetition detection, and the thresholds that bound it.
 *
 * **The threshold values are provisional.** They are starting points chosen so
 * the FSM can be built and tested, not measured quantities, and they support no
 * claim - the same status as `TemporalConfig.PROVISIONAL` and for the same
 * reason (CLAUDE.md section 23). P6 personalises them from the user's own
 * calibration repetitions, which is where the real values come from; P4 sanity
 * checks these on a device first.
 */
data class RepSignalSpec(

    /** The joint whose angle defines the movement. */
    val joint: JointAngleId,

    val plane: MeasurementPlane,

    /** Signal value at the starting position. */
    val topThreshold: Double,

    /** Signal value at the far position. May be above or below [topThreshold]. */
    val bottomThreshold: Double,

    /**
     * Band around each threshold within which the phase does not change.
     *
     * Without it a signal resting on a threshold flickers between phases from
     * frame to frame and manufactures repetitions out of estimator noise.
     */
    val hysteresis: Double,

    /** Below this, a completed cycle is treated as a twitch rather than a repetition. */
    val minRepDurationMs: Double,

    /** Above this, the movement is assumed abandoned rather than very slow. */
    val maxRepDurationMs: Double,
) {
    init {
        require(topThreshold != bottomThreshold) {
            "top and bottom thresholds must differ; both are " + topThreshold
        }
        require(hysteresis >= 0.0) { "hysteresis must be non-negative, got " + hysteresis }
        require(minRepDurationMs > 0.0) { "minRepDurationMs must be positive" }
        require(maxRepDurationMs > minRepDurationMs) {
            "maxRepDurationMs must exceed minRepDurationMs"
        }
    }

    /** True when the starting position is the numerically larger value. */
    val topIsGreater: Boolean get() = topThreshold > bottomThreshold
}

/**
 * Which signal defines an isometric hold, and how far it may stray.
 *
 * Same provisional status as [RepSignalSpec].
 */
data class HoldSignalSpec(

    val joint: JointAngleId,

    val plane: MeasurementPlane,

    /** The value the held position corresponds to - a straight body line, say. */
    val targetValue: Double,

    /** How far from [targetValue] still counts as holding. */
    val toleranceBand: Double,

    /** Below this total, the attempt is reported as too short rather than as a hold. */
    val minHoldDurationMs: Double,
) {
    init {
        require(toleranceBand > 0.0) { "toleranceBand must be positive, got " + toleranceBand }
        require(minHoldDurationMs > 0.0) { "minHoldDurationMs must be positive" }
    }
}

/**
 * An exercise analysed by counting repetitions.
 *
 * This interface is half of the structural separation CLAUDE.md section 11
 * requires. `BilateralRepTracker` accepts only a [RepetitionExercise], so an
 * isometric hold cannot be handed to it - the call does not compile, rather than
 * failing a check that could later be removed.
 */
interface RepetitionExercise : ExerciseProfile {

    override val mode: AnalysisMode get() = AnalysisMode.REPETITION

    val repetitionSemantics: RepetitionSemantics

    /**
     * Which sides the required view can measure. No default, deliberately -
     * see [AnalysedSides].
     */
    val analysedSides: AnalysedSides

    /**
     * What moving away from the starting position is: the lift or the lowering.
     * No default - see [Contraction]. DECISIONS.md P3-30.
     */
    val awayFromStart: Contraction

    val repSignal: RepSignalSpec

    /**
     * Joints other than the driving one that each repetition records - DECISIONS.md P3-44.
     * None by default: an exercise that asks for nothing computes nothing per frame. A variant
     * asks for its parent's, through delegation.
     */
    val formMeasures: List<FormMeasure> get() = emptyList()
}

/**
 * An exercise analysed by measuring how long a position is held.
 *
 * The other half of the separation. `HoldAnalyser` accepts only an
 * [IsometricExercise].
 */
interface IsometricExercise : ExerciseProfile {

    override val mode: AnalysisMode get() = AnalysisMode.ISOMETRIC_HOLD

    val holdSignal: HoldSignalSpec
}
