package com.kinesis.core.analysis.profile

import com.kinesis.core.domain.profile.ExerciseProfile

/**
 * An exercise that is the same movement as another, under a different name -
 * CONTRACTS.md section 3, CLAUDE.md section 9, DECISIONS.md P3-6.
 *
 * > Variants inherit their parent's profile, calibration and claim ceiling:
 * > Goblet Squat → Squat, Reverse Lunge → Lunge.
 *
 * > The variants share everything — profile, thresholds, calibration baseline,
 * > rules — and differ only in a label and a coaching note.
 *
 * CLAUDE.md section 22 puts "treat exercise variants as separate algorithms" on
 * the never-do list. This interface and the two classes below are how that
 * becomes structural rather than a matter of reviewer attention.
 *
 * ### Shape
 *
 * An [ExerciseProfile] that additionally names the profile it came from. The
 * diamond this creates with the delegated interface is not a problem, because
 * delegation generates an override for every inherited member, so there is
 * never more than one implementation to choose between.
 *
 * `sealed`, so a third kind of variant cannot be invented elsewhere and reach
 * the library without passing through this file.
 */
sealed interface ExerciseVariant : ExerciseProfile {

    /**
     * The profile this variant is. Not a copy of its configuration - the
     * instance itself, which is where every delegated value comes from.
     */
    val parent: ExerciseProfile

    /**
     * The one piece of content a variant has of its own.
     *
     * Display only, never parsed, and it never reaches an analysis decision -
     * the same footing as [ExerciseProfile.displayName]. No document defines
     * what belongs in one beyond the phrase "a coaching note", which
     * DECISIONS.md P3-6 records as a gap filled here rather than found.
     */
    val coachingNote: String
}

/**
 * A variant of a repetition exercise.
 *
 * ### The mechanism, in one line
 *
 * `RepetitionExercise by parent` forwards the entire profile surface - `tier`,
 * `maxClaimLevel`, `requiredView`, `posture`, `mode`, `requiredLandmarks`,
 * `repetitionSemantics`, `repSignal` - to the parent object at runtime. None of
 * them is written here, so there is no second copy to drift out of step, and
 * `BilateralRepTracker` cannot tell a variant from its parent.
 *
 * Subclassing the parent class would have done the opposite: it would need every
 * profile and every property to be `open`, making the configuration overridable
 * by default. Here inheritance is the default and a deviation has to be written
 * in deliberately - which is the edit `VariantInheritanceTest` exists to catch,
 * since no type can forbid someone adding an `override` to this class later.
 *
 * ### Claim level
 *
 * `maxClaimLevel` is the parent's, so the ceiling is inherited exactly as
 * CONTRACTS.md section 3 requires. The *authority* to construct a Level 3
 * finding is not: this is not a `TierAProfile`, so it has no
 * `techniqueAuthority`, and a goblet-squat technique rule is a squat rule issued
 * under [parent]. A variant can be analysed by its parent's rules; it cannot
 * author its own. DECISIONS.md P3-6 and P1-3.
 */
class RepetitionVariant(
    override val parent: RepetitionExercise,
    override val id: String,
    override val displayName: String,
    override val coachingNote: String,
) : RepetitionExercise by parent, ExerciseVariant {

    init {
        require(id != parent.id) { "a variant cannot share its parent's id: " + id }
        require(id.isNotBlank()) { "a variant needs an id" }
        require(coachingNote.isNotBlank()) {
            "a variant differs from its parent in a label and a coaching note; " + id +
                " has no note"
        }
    }

    override fun toString(): String = "RepetitionVariant(" + id + " of " + parent.id + ")"
}

/**
 * A variant of an isometric exercise. As [RepetitionVariant], forwarding
 * `holdSignal` instead of the repetition pair.
 *
 * None of the variants the documents name is isometric. It exists so that the
 * two analysis modes stay symmetric and the next isometric variant is a
 * declaration rather than a design problem - CLAUDE.md section 11 requires both
 * modes to be first-class.
 */
class IsometricVariant(
    override val parent: IsometricExercise,
    override val id: String,
    override val displayName: String,
    override val coachingNote: String,
) : IsometricExercise by parent, ExerciseVariant {

    init {
        require(id != parent.id) { "a variant cannot share its parent's id: " + id }
        require(id.isNotBlank()) { "a variant needs an id" }
        require(coachingNote.isNotBlank()) {
            "a variant differs from its parent in a label and a coaching note; " + id +
                " has no note"
        }
    }

    override fun toString(): String = "IsometricVariant(" + id + " of " + parent.id + ")"
}
