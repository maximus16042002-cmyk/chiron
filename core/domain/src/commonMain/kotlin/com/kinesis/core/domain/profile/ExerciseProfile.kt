package com.kinesis.core.domain.profile

import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.finding.TechniqueFault
import com.kinesis.core.domain.landmark.RequiredLandmarks

/**
 * How much evidence stands behind an exercise, and therefore how strong a claim
 * the system may make about it.
 *
 * DECISIONS.md P0-7 is worth reading before using this. Under the zero-dataset
 * plan there are no fault labels anywhere, so Tier A does not mean "validated".
 * It means "cited technique rules were written for this exercise, and not for
 * the others". The tier system remains an honest scope boundary enforced by
 * types; it is not a claim of measured accuracy.
 *
 * The ceiling lives on the tier itself so a profile cannot report one that
 * disagrees with its tier.
 */
enum class Tier(val maxClaimLevel: ClaimLevel) {

    /** Cited technique rules exist. Squat, Push-up, Bicep Curl, Lateral Raise (seated), Glute Bridge. */
    A(ClaimLevel.L3_TECHNIQUE),

    /** Measurement and personal deviation only. No technique judgement. */
    B(ClaimLevel.L2_DEVIATION),

    /** Isometric holds. Measurement and personal deviation only. */
    C(ClaimLevel.L2_DEVIATION),
}

/**
 * The camera position an exercise has to be filmed from - CLAUDE.md section 10,
 * CONTRACTS.md section 8.
 *
 * Every profile declares one. An exercise filmed from an unsuitable view is not
 * silently analysed: the setup gate blocks scoring rather than producing
 * confident nonsense.
 */
enum class CameraView {

    /** The subject faces the camera. Frontal-plane movement is measurable. */
    FRONT_ON,

    /** The camera is perpendicular to the subject. Sagittal-plane movement is measurable. */
    SIDE_ON,

    /** Between the two, for movements that need both planes partially. */
    ANGLED_45,
}

/**
 * The subject's posture during the exercise.
 *
 * DECISIONS.md P0-4 requires posture to be stated explicitly on each profile:
 * Lateral Raise and Shoulder Press are seated, matching MM-Fit, because a rule
 * derived from seated data does not transfer to standing - seated removes the
 * lower body from the picture and changes the trunk reference.
 */
enum class Posture {
    STANDING,
    SEATED,

    /**
     * Standing with the trunk hinged forward toward horizontal, e.g. a
     * bent-over row.
     *
     * Distinct from [STANDING] for the reason P0-4 gives for posture existing at
     * all: it changes the trunk reference. FINAL_ARCHITECTURE section 5 notes
     * that a bent-over row's "trunk angle also varies enormously between
     * people", and ZERO_DATASET_BUILD_PLAN records that MM-Fit's row is a
     * *standing* one whose "posture mismatch [is] too large to claim
     * measurement". Calling both of those STANDING would erase the difference
     * that makes the benchmark inapplicable. DECISIONS.md P3-8.
     */
    HINGED,

    /** Face down, e.g. plank, push-up. */
    PRONE,

    /** Face up, e.g. glute bridge, sit-up. */
    SUPINE,

    /** On one side, e.g. side plank, hip abduction. */
    SIDE_LYING,
}

/**
 * How a set of this exercise is segmented - CLAUDE.md section 11.
 *
 * An isometric exercise is never forced through the repetition FSM.
 */
enum class AnalysisMode {
    REPETITION,
    ISOMETRIC_HOLD,
}

/**
 * The static description of one exercise: what it is, how it must be filmed, and
 * how strong a claim it is entitled to.
 *
 * Profiles are defined in `:core:analysis` from P3 onward. They extend one of
 * [TierAProfile], [TierBProfile] or [TierCProfile] rather than implementing this
 * interface directly, because the tier classes are what fix the claim ceiling.
 *
 * Variants inherit their parent's profile, calibration and claim ceiling. They
 * are not separate algorithms - CLAUDE.md section 9.
 */
interface ExerciseProfile {

    /** Stable identifier used in persistence and export, e.g. `squat`. */
    val id: String

    /** Human-readable name. Display only; never parsed. */
    val displayName: String

    val tier: Tier

    /**
     * The strongest claim this profile may make. Derived from [tier] so the two
     * cannot disagree.
     */
    val maxClaimLevel: ClaimLevel get() = tier.maxClaimLevel

    val requiredView: CameraView

    val posture: Posture

    val mode: AnalysisMode

    /**
     * The landmarks this exercise actually depends on. The validity gate judges
     * a frame against these, not against all 33 - a squat does not care that the
     * wrists are out of frame.
     */
    val requiredLandmarks: RequiredLandmarks
}

/**
 * Proof that a Tier A profile authorised a Level 3 claim.
 *
 * This class is the mechanism behind CLAUDE.md section 8. It carries no
 * behaviour; its whole purpose is to be difficult to obtain. The constructor is
 * private, the only issue point is internal to `:core:domain`, and the only
 * public way to reach one is [TierAProfile.techniqueAuthority].
 *
 * Because [TechniqueFault.from] demands one, a Tier B or Tier C profile cannot
 * produce a Level 3 finding: the call does not compile, so there is no unsafe
 * path left in the codebase to filter afterwards.
 */
class TechniqueAuthority private constructor(internal val profileId: String) {

    /** The authorising profile's id. Exposed for diagnostics and tests. */
    fun describeProfile(): String = profileId

    override fun toString(): String = "TechniqueAuthority(profileId=" + profileId + ")"

    internal companion object {
        /** Not public, and not callable from `:core:analysis` or `:app`. */
        fun issueTo(profile: TierAProfile): TechniqueAuthority = TechniqueAuthority(profile.id)
    }
}

/**
 * An exercise with cited technique rules. The only kind of profile that can
 * produce a Level 3 finding.
 *
 * [tier] and [maxClaimLevel] are `final` so a subclass cannot restate them.
 */
abstract class TierAProfile : ExerciseProfile {
    final override val tier: Tier = Tier.A
    final override val maxClaimLevel: ClaimLevel = Tier.A.maxClaimLevel

    /**
     * The token [TechniqueFault.from] requires. Declared here and nowhere else,
     * which is what makes Level 3 unreachable for the other tiers.
     *
     * Computed on access rather than stored, so the abstract [id] is read after
     * the subclass has finished constructing itself. This is post-rep path
     * (CONTRACTS.md section 7), not per frame.
     */
    val techniqueAuthority: TechniqueAuthority
        get() = TechniqueAuthority.issueTo(this)
}

/**
 * A repetition exercise with no cited technique rules. Measurement and personal
 * deviation only. There is no `techniqueAuthority` property here, and adding one
 * would mean deleting the guard test that says there is not.
 */
abstract class TierBProfile : ExerciseProfile {
    final override val tier: Tier = Tier.B
    final override val maxClaimLevel: ClaimLevel = Tier.B.maxClaimLevel
}

/**
 * An isometric hold. Measurement and personal deviation only, for the same
 * reason as [TierBProfile].
 */
abstract class TierCProfile : ExerciseProfile {
    final override val tier: Tier = Tier.C
    final override val maxClaimLevel: ClaimLevel = Tier.C.maxClaimLevel
}
