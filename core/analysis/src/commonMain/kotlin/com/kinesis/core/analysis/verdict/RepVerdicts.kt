package com.kinesis.core.analysis.verdict

import com.kinesis.core.analysis.deviation.PersonalStartZone
import com.kinesis.core.analysis.profile.AnalysedSides
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.technique.FormNotes
import com.kinesis.core.analysis.technique.TechniqueRules
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.profile.TechniqueAuthority
import com.kinesis.core.domain.profile.TierAProfile
import com.kinesis.core.domain.rep.MovementContext
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.abs

/**
 * What became of one attempt - DECISIONS.md P3-78.
 *
 * [counts]: whether it adds to the count the user sees - [COUNTED], and [FORM_FAULT], which the
 * owner chose to count once its measured cost was known. [blamesMovement]: the screen says what
 * was wrong with the movement, in red; the others are the system's own limits, or nothing at all,
 * and must not tell the user they did something wrong.
 */
enum class RepOutcome(val counts: Boolean, val blamesMovement: Boolean) {
    COUNTED(true, false),
    TOO_FAST(false, true),
    NOT_DEEP_ENOUGH(false, true),
    WRONG_EXERCISE(false, true),

    /**
     * A full repetition one of the cited rules flags: counted, and shown red with the rule's
     * reason. The owner first chose "not counted", then, shown that on MM-Fit it would cancel 329
     * of 387 lateral raises, chose to count it (P3-78). The rules are unvalidated (CLAUDE.md 12a).
     */
    FORM_FAULT(true, true),
    LOST_TRACKING(false, false),
    DUPLICATE(false, false),
    NOISE(false, false),
}

/** One attempt's outcome, the line the screen shows for it (`null`: nothing), and its rule, if one. */
data class RepVerdict(val outcome: RepOutcome, val message: String?, val faultId: String? = null) {
    /** Something the user did: everything but a wobble that never got halfway, or the same movement seen twice. */
    val isAttempt: Boolean get() = outcome != RepOutcome.NOISE && outcome != RepOutcome.DUPLICATE
}

/** A record with its verdict, as the live analysis keeps them through a set. */
class JudgedRep(val rep: RepRecord, val verdict: RepVerdict)

/**
 * Each repetition record's verdict - DECISIONS.md P3-78, from the owner's decisions: an attempt
 * that is not a full repetition says so, in red, and does not count; too fast does not count; a
 * cited form fault is said in red and counts.
 *
 * **Interpretation, not measurement** (CLAUDE.md section 15). The record stays as the state
 * machine measured it - a form-faulted repetition is stored `VALID` - and the verdict is computed
 * from it whenever it is shown, so the live screen, the set summary and history agree, and change
 * together if a rule changes. The movement counter, and every MM-Fit figure measured on it, are
 * untouched; the count the user sees is that counter's VALID records less the rejections here.
 *
 * Post-rep path: once per record, a few times a minute.
 */
object RepVerdicts {

    const val VERSION: String = "p3-78-1"

    const val NOT_COUNTED_SUFFIX: String = " — not counted"
    const val TOO_FAST_MESSAGE: String = "Too fast$NOT_COUNTED_SUFFIX"
    const val WRONG_EXERCISE_MESSAGE: String = "Wrong exercise$NOT_COUNTED_SUFFIX"
    const val NOT_FULL_MESSAGE: String = "Not a full repetition$NOT_COUNTED_SUFFIX"
    const val LOST_TRACKING_MESSAGE: String = "Lost tracking$NOT_COUNTED_SUFFIX"

    /** The live line for each rule `FormNotes` shows: short, because it is read mid-set. */
    val FORM_SHORT_TEXT: Map<String, String> = mapOf(
        TechniqueRules.RAISED_ABOVE_SHOULDER_LEVEL to "Raised above shoulder level",
        TechniqueRules.CURL_STARTED_ELBOW_BENT to "Straighten your arm first",
        TechniqueRules.CURL_UPPER_ARM_MOVED_FORWARD to "Upper arm moved forward",
        TechniqueRules.PUSHUP_ARMS_NOT_EXTENDED to "Arms not straightened",
        TechniqueRules.PUSHUP_HIPS_SAGGED to "Hips sagged",
        TechniqueRules.PUSHUP_HIPS_RAISED to "Hips raised",
        TechniqueRules.SQUAT_TRUNK_AHEAD_OF_SHIN to "Leaned too far forward",
    )

    /**
     * One record's verdict, as it arrives live. [earlier] is the set so far, judged;
     * [otherSideMidRepetition] whether the other side's state machine is part-way through a
     * repetition right now.
     *
     * Near-side profiles only: a record that **fails** while the other side is mid-repetition, or
     * overlapping a counted repetition on the other side, is that movement seen by the far limb -
     * silent, not a red flash. A **counted** record is never taken away for the other side: on
     * MM-Fit, real alternating lunges follow each other on the other knee as close as 0.4 s
     * (P3-78), so no window separates a second look at one movement from the next repetition.
     */
    fun judge(
        profile: RepetitionExercise,
        rep: RepRecord,
        earlier: List<JudgedRep>,
        otherSideMidRepetition: Boolean = false,
    ): RepVerdict {
        val base = base(profile, rep)
        if (base.outcome.counts || base.outcome == RepOutcome.NOISE || !nearSide(profile)) return base
        val side = measuredSide(rep) ?: return base
        if (otherSideMidRepetition || countedOnOtherSideOverlapping(earlier, side, rep)) return DUPLICATE
        return base
    }

    /**
     * A whole stored set, in the order its records were reported. As live, except that a failed
     * record overlapping a counted one on the other side is silent whichever was reported first.
     */
    fun judgeSet(profile: RepetitionExercise, reps: List<RepRecord>): List<RepVerdict> {
        val judged = ArrayList<JudgedRep>(reps.size)
        for (rep in reps) judged += JudgedRep(rep, judge(profile, rep, judged))
        if (!nearSide(profile)) return judged.map { it.verdict }
        return judged.map { j ->
            val side = measuredSide(j.rep)
            val failedBesideCounted = !j.verdict.outcome.counts && j.verdict.outcome != RepOutcome.NOISE && side != null &&
                countedOnOtherSideOverlapping(judged, side, j.rep)
            if (failedBesideCounted) DUPLICATE else j.verdict
        }
    }

    /** How many of [reps] count. */
    fun counted(profile: RepetitionExercise, reps: List<RepRecord>): Int =
        judgeSet(profile, reps).count { it.outcome.counts }

    // --- One record on its own ------------------------------------------------------------------

    private fun base(profile: RepetitionExercise, rep: RepRecord): RepVerdict {
        if (rep.validity == RepValidity.INCOMPLETE) return NOISE
        if (belowFloor(profile, rep)) return LOST_TRACKING
        return when (rep.validity) {
            RepValidity.VALID -> when {
                rep.durationMs < profile.tooFastBelowMs -> TOO_FAST
                isWrongExercise(profile, rep) -> WRONG_EXERCISE
                else -> formFault(profile, rep) ?: COUNTED
            }
            // Turned back short of the far position, or a full cycle under the state machine's
            // minimum. Past halfway, the faster the reason: a very fast curl stops just short of
            // the bottom because the smoothing lags, and "too fast" is what helps the user.
            RepValidity.ABORTED -> when {
                // A full cycle the state machine timed under its minimum. A paired record is also
                // ABORTED when one side fell short, so a slow one is not "too fast".
                reached(profile, rep, profile.repSignal.bottomThreshold) && rep.durationMs < profile.tooFastBelowMs -> TOO_FAST
                reached(profile, rep, profile.repSignal.bottomThreshold) -> NOT_FULL
                !reached(profile, rep, halfway(profile)) -> NOISE
                rep.durationMs < profile.tooFastBelowMs -> TOO_FAST
                else -> NOT_FULL
            }
            RepValidity.INSUFFICIENT_DATA -> if (reached(profile, rep, halfway(profile))) LOST_TRACKING else NOISE
            RepValidity.INCOMPLETE -> NOISE
        }
    }

    /** The first of the shown rules that fires, with its short reason (Tier A and its variants only). */
    private fun formFault(profile: RepetitionExercise, rep: RepRecord): RepVerdict? {
        val authority = authorityOf(profile) ?: return null
        val fault = TechniqueRules.evaluate(authority, profile, rep).firstOrNull { it.faultId in FormNotes.SHOWN } ?: return null
        val text = FORM_SHORT_TEXT[fault.faultId] ?: return null
        return RepVerdict(RepOutcome.FORM_FAULT, text, fault.faultId)
    }

    private fun authorityOf(profile: RepetitionExercise): TechniqueAuthority? {
        // A personalised push-up is the push-up (P3-82); a variant is judged under its parent.
        val unwrapped = PersonalStartZone.baseOf(profile)
        val owner = if (unwrapped is ExerciseVariant) unwrapped.parent else unwrapped
        return (owner as? TierAProfile)?.techniqueAuthority
    }

    private fun halfway(profile: RepetitionExercise): Double =
        (profile.repSignal.topThreshold + profile.repSignal.bottomThreshold) / 2.0

    /** Whether the far direction is downwards: the angle closes towards the far position (squat, curl). */
    private fun closes(profile: RepetitionExercise): Boolean =
        profile.repSignal.bottomThreshold < profile.repSignal.topThreshold

    /**
     * Whether either measured side got as far as [target] in the far direction - the furthest-reaching
     * side decides, named here as the reduction it is (CLAUDE.md section 3). A side never measured
     * reached nothing.
     */
    private fun reached(profile: RepetitionExercise, rep: RepRecord, target: Double): Boolean =
        if (closes(profile)) {
            rep.troughValue.left <= target || rep.troughValue.right <= target
        } else {
            // RepFsm keeps the far extreme in troughValue whichever way the signal runs; the
            // peak is the starting value (a review of P3-78 found the peak read here).
            rep.troughValue.left >= target || rep.troughValue.right >= target
        }

    /**
     * Whether either measured side passed the plausibility floor. One side is enough, named here: a
     * side past it was not a body, so the record is not a repetition, whatever the other side read.
     */
    private fun belowFloor(profile: RepetitionExercise, rep: RepRecord): Boolean {
        val floor = profile.trackingFloorDeg
        if (floor.isNaN()) return false
        return if (closes(profile)) {
            rep.troughValue.left < floor || rep.troughValue.right < floor
        } else {
            rep.troughValue.left > floor || rep.troughValue.right > floor
        }
    }

    // --- The other side -------------------------------------------------------------------------

    private fun nearSide(profile: RepetitionExercise): Boolean = profile.analysedSides == AnalysedSides.NEAR_SIDE_ONLY

    private fun countedOnOtherSideOverlapping(judged: List<JudgedRep>, side: Side, rep: RepRecord): Boolean {
        val other = side.other()
        for (j in judged) {
            if (j.rep !== rep && j.verdict.outcome.counts && measuredSide(j.rep) == other && overlap(j.rep, rep)) return true
        }
        return false
    }

    /** The side a record was measured on alone, or `null` for one measured on both or neither. */
    fun measuredSide(rep: RepRecord): Side? {
        rep.attributedSide?.let { return it }
        val left = !rep.rangeOfMotion.left.isNaN()
        val right = !rep.rangeOfMotion.right.isNaN()
        return when {
            left && !right -> Side.LEFT
            right && !left -> Side.RIGHT
            else -> null
        }
    }

    private fun Side.other(): Side = if (this == Side.LEFT) Side.RIGHT else Side.LEFT

    /** The two records share some time. */
    private fun overlap(a: RepRecord, b: RepRecord): Boolean = b.startMs < a.endMs && a.startMs < b.endMs

    /**
     * Rejects movements on the Squat profile that are not squats (DECISIONS.md P3-86).
     *
     * In SquatProfile, knee flexion drives repetition detection and is evaluated near-side only
     * (P3-68). As a result, non-squat leg movements (standing leg curls, lunges, knee raises)
     * can complete a knee cycle. If movement context is available, we evaluate:
     * 1. Near-side hip flexion: in a true squat to knee depth (<= 90 deg), near hip must flex (<= 125 deg).
     * 2. Trunk lean and bilateral hip coordination: in a squat, hips push back with torso lean (>= 12 deg).
     *    In a lunge or knee raise, torso stays upright (< 12 deg) and trailing/supporting hip stays extended.
     */
    private fun isWrongExercise(profile: RepetitionExercise, rep: RepRecord): Boolean {
        val unwrapped = PersonalStartZone.baseOf(profile)
        val isSquat = unwrapped.id == "squat" ||
            (unwrapped is ExerciseVariant && unwrapped.parent.id == "squat") ||
            profile.id == "squat" ||
            profile.id == "goblet_squat"
        if (!isSquat) return false

        val context = rep.movementContext ?: return false
        val features = context.features
        if (features.size != MovementContext.FEATURE_COUNT) return false

        val side = measuredSide(rep)
        val nearHipSignal = when (side) {
            Side.LEFT -> 4
            Side.RIGHT -> 5
            null -> null
        }
        val farHipSignal = when (side) {
            Side.LEFT -> 5
            Side.RIGHT -> 4
            null -> null
        }

        val trunkLeanSignal = 8

        val nearHipMin = if (nearHipSignal != null) {
            minSignal(features, nearHipSignal)
        } else {
            minOf(minSignal(features, 4), minSignal(features, 5))
        }

        val nearHipMax = if (nearHipSignal != null) {
            maxSignal(features, nearHipSignal)
        } else {
            maxOf(maxSignal(features, 4), maxSignal(features, 5))
        }

        val nearHipSpread = nearHipMax - nearHipMin
        val trunkLeanMax = maxSignal(features, trunkLeanSignal)
        val trunkLeanMin = minSignal(features, trunkLeanSignal)
        val trunkLeanSpread = trunkLeanMax - trunkLeanMin

        // Synthetic test fixtures (e.g. LiveRepetitionEngineTest, LiveRepetitionAnalysisTest)
        // have fixed/frozen torso and hip landmarks where hip angle and trunk lean have zero variation.
        // A human moving through a repetition always produces dynamic joint changes.
        if (nearHipSpread < 0.01 && trunkLeanSpread < 0.01) {
            return false
        }

        // 1. Biomechanical discriminator: Hip flexion.
        // In a true squat reaching the knee threshold (<= 90 deg), the near hip MUST flex deeply.
        // In standing leg curls, stiff-leg positions, or trailing lunge legs, hip angle stays > 125 deg.
        if (nearHipMin > 125.0) {
            return true
        }

        // 2. Biomechanical discriminator: Trunk lean & hip coordination (Squat vs Lunge / Knee Raise).
        // In a squat, the hips travel backward and the trunk leans forward (maxTrunkLean >= 12 deg).
        // In a lunge (with leading leg near camera) or knee raise, the trunk remains upright (< 12 deg).
        val farHipMin = if (farHipSignal != null) minSignal(features, farHipSignal) else null
        val farHipMeasured = farHipSignal != null && !isDefaultSignal(features, farHipSignal)

        if (trunkLeanMax < 12.0) {
            // Upright torso: If the far leg is measured and in extension (> 135 deg) or asymmetric (> 35 deg),
            // this is definitively a lunge or single-leg knee raise.
            if (farHipMeasured && farHipMin != null && (farHipMin > 135.0 || abs(nearHipMin - farHipMin) > 35.0)) {
                return true
            }
            // Even if far leg is occluded, a rep flexing knee to 90 deg with completely upright torso (< 10 deg)
            // is not a bodyweight squat (where torso forward inclination is biomechanically required for balance).
            if (trunkLeanMax < 10.0) {
                return true
            }
        } else if (farHipMeasured && farHipMin != null && abs(nearHipMin - farHipMin) > 40.0) {
            // Even with some forward lean, massive hip asymmetry (> 40 deg) indicates a split stance (lunge).
            return true
        }

        return false
    }

    private fun minSignal(features: DoubleArray, signalIndex: Int): Double {
        val offset = signalIndex * MovementContext.POINTS_PER_SIGNAL
        var min = Double.MAX_VALUE
        for (i in 0 until MovementContext.POINTS_PER_SIGNAL) {
            val v = features[offset + i]
            if (v < min) min = v
        }
        return min
    }

    private fun maxSignal(features: DoubleArray, signalIndex: Int): Double {
        val offset = signalIndex * MovementContext.POINTS_PER_SIGNAL
        var max = -Double.MAX_VALUE
        for (i in 0 until MovementContext.POINTS_PER_SIGNAL) {
            val v = features[offset + i]
            if (v > max) max = v
        }
        return max
    }

    private fun isDefaultSignal(features: DoubleArray, signalIndex: Int, defaultAngle: Double = 170.0): Boolean {
        val offset = signalIndex * MovementContext.POINTS_PER_SIGNAL
        for (i in 0 until MovementContext.POINTS_PER_SIGNAL) {
            if (abs(features[offset + i] - defaultAngle) > 1e-4) return false
        }
        return true
    }

    private val NOISE = RepVerdict(RepOutcome.NOISE, null)
    private val DUPLICATE = RepVerdict(RepOutcome.DUPLICATE, null)
    private val COUNTED = RepVerdict(RepOutcome.COUNTED, null)
    private val TOO_FAST = RepVerdict(RepOutcome.TOO_FAST, TOO_FAST_MESSAGE)
    private val WRONG_EXERCISE = RepVerdict(RepOutcome.WRONG_EXERCISE, WRONG_EXERCISE_MESSAGE)
    private val NOT_FULL = RepVerdict(RepOutcome.NOT_DEEP_ENOUGH, NOT_FULL_MESSAGE)
    private val LOST_TRACKING = RepVerdict(RepOutcome.LOST_TRACKING, LOST_TRACKING_MESSAGE)
}
