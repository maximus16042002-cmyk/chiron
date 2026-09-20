package com.kinesis.core.analysis

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.ExerciseVariant
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.rep.BilateralRepTracker
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.profile.TierAProfile
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Permanent guard - CONTRACTS.md section 3, CLAUDE.md sections 9 and 22,
 * DECISIONS.md P3-6.
 *
 * "Variants inherit their parent's profile, calibration and claim ceiling."
 * "Treat exercise variants as separate algorithms" is on the never-do list.
 *
 * The mechanism (P3-6) makes that structural: a variant delegates its whole
 * profile surface to the parent instance and re-declares only its id, display
 * name and coaching note. Nothing else is written in the variant at all, so
 * there is no second copy to drift.
 *
 * What no type can prevent is somebody adding an `override` to the variant class
 * later - delegation is the default, not a prohibition. That edit is what these
 * tests exist to catch, and it is the one the mutation probe exercises: a
 * variant declaring a claim level or repetition semantics of its own defeats the
 * entire point of CONTRACTS.md section 3, and it would do so silently, because
 * an exercise that counts reps differently from its parent still counts reps.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class VariantInheritanceTest {

    private val policy = VisibilityPolicy.PROVISIONAL
    private val frameMs = 1000.0 / 30.0

    private val variants: List<ExerciseVariant>
        get() = ExerciseLibrary.all.filterIsInstance<ExerciseVariant>()

    // --- Entries, profiles and the honest count -----------------------------------

    /**
     * CLAUDE.md section 9 requires entries and profiles to be counted
     * separately - "15 exercise profiles ... presented as 17 library entries" -
     * and never conflated into "17 exercises". P3-6 makes that structural rather than
     * editorial: a variant is an entry and is not a profile.
     */
    @Test
    fun entries_and_profiles_are_counted_separately() {
        assertEquals(
            ExerciseLibrary.all.size,
            ExerciseLibrary.profiles.size + ExerciseLibrary.variants.size,
            "every entry is either a profile or a variant, and never both",
        )
        for (profile in ExerciseLibrary.profiles) {
            assertFalse(
                profile is ExerciseVariant,
                profile.id + " is a variant counted among the profiles",
            )
        }
        for (variant in ExerciseLibrary.variants) {
            assertTrue(
                variant in ExerciseLibrary.all,
                variant.id + " is a variant that is not a library entry",
            )
        }
    }

    @Test
    fun the_library_holds_exactly_the_variants_the_documents_name() {
        assertEquals(
            listOf("goblet_squat", "reverse_lunge"),
            ExerciseLibrary.variants.map { it.id }.sorted(),
        )
    }

    /**
     * Permanent guard - DECISIONS.md P3-6 and P3-7.
     *
     * The documents name exactly two variants and their parents. A variant cannot
     * be built before its parent exists, so this holds the pairs and asserts that
     * once a parent is in the library its variant is too, attached to it. A
     * deferred item with nothing holding it is an item that gets forgotten.
     *
     * **This replaced a weaker guard.** P3-6 wrote
     * `the_reverse_lunge_variant_is_deferred_until_its_parent_profile_exists`,
     * which asserted that neither `lunge` nor `reverse_lunge` was present. It was
     * meant to fire when the parent arrived without its variant, and it actually
     * fired whenever the parent arrived at all - so it could not be made to pass
     * by doing the right thing, and would have had to be deleted after P3-7. This
     * one is strictly stronger: it keeps guarding both pairs permanently, and
     * guards any pair later added to the list. P3-7 records the replacement,
     * because swapping out a guard test is exactly the move CLAUDE.md section 18
     * warns about.
     */
    @Test
    fun every_documented_variant_whose_parent_exists_is_built() {
        // CONTRACTS.md section 3: "Goblet Squat → Squat, Reverse Lunge → Lunge."
        val documented = listOf("goblet_squat" to "squat", "reverse_lunge" to "lunge")

        for ((variantId, parentId) in documented) {
            val parent = ExerciseLibrary.all.firstOrNull { it.id == parentId } ?: continue
            val variant = ExerciseLibrary.variants.firstOrNull { it.id == variantId }
            assertTrue(
                variant != null,
                parentId + " is in the library, so its documented variant " + variantId +
                    " must be too",
            )
            assertSame(
                parent,
                variant.parent,
                variantId + " does not delegate to " + parentId,
            )
        }

        // And nothing calls itself a variant that the documents never named.
        assertEquals(
            documented.map { it.first }.sorted(),
            ExerciseLibrary.variants.map { it.id }.sorted(),
            "an undocumented variant reached the library",
        )
    }

    // --- The inheritance itself ------------------------------------------------------

    /**
     * The central guard. Every field a variant is not allowed to differ in must
     * equal its parent's, swept across every variant rather than named one by
     * one, so this keeps holding as later batches add more.
     *
     * FINAL_ARCHITECTURE line 300: the variants "share everything - profile,
     * thresholds, calibration baseline, rules - and differ only in a label and a
     * coaching note."
     */
    @Test
    fun every_variant_agrees_with_its_parent_on_everything_that_is_not_a_label() {
        assertTrue(variants.isNotEmpty(), "no variants to check; the sweep is guarding nothing")

        for (variant in variants) {
            val parent = variant.parent
            val where = variant.id + " vs its parent " + parent.id + ": "

            assertEquals(parent.tier, variant.tier, where + "tier")
            assertEquals(parent.maxClaimLevel, variant.maxClaimLevel, where + "claim ceiling")
            assertEquals(parent.requiredView, variant.requiredView, where + "camera view")
            assertEquals(parent.posture, variant.posture, where + "posture")
            assertEquals(parent.mode, variant.mode, where + "analysis mode")
            assertEquals(
                parent.requiredLandmarks.toList(),
                variant.requiredLandmarks.toList(),
                where + "required landmarks",
            )

            if (parent is RepetitionExercise) {
                val child = variant as RepetitionExercise
                assertEquals(
                    parent.repetitionSemantics,
                    child.repetitionSemantics,
                    where + "repetition semantics - this is the factor-of-two error",
                )
                assertEquals(parent.repSignal, child.repSignal, where + "repetition signal")
            }
            if (parent is IsometricExercise) {
                assertEquals(
                    parent.holdSignal,
                    (variant as IsometricExercise).holdSignal,
                    where + "hold signal",
                )
            }
        }
    }

    /**
     * A variant's parent has to be a real profile in the library, and must not
     * itself be a variant. Nothing in any document contemplates a chain, and a
     * chain would make "inherits its parent's claim ceiling" ambiguous about
     * which ancestor is meant.
     */
    @Test
    fun a_variant_parent_is_a_library_profile_and_never_another_variant() {
        for (variant in variants) {
            assertFalse(
                variant.parent is ExerciseVariant,
                variant.id + " is a variant of a variant",
            )
            assertTrue(
                ExerciseLibrary.profiles.any { it === variant.parent },
                variant.id + " has a parent that is not a profile in the library",
            )
            assertNotEquals(variant.id, variant.parent.id, "a variant shares its parent's id")
        }
    }

    /**
     * The claim **ceiling** is inherited; the **authority to construct** a Level
     * 3 finding is not - P3-6, P1-3.
     *
     * `TechniqueAuthority` is reachable only through
     * `TierAProfile.techniqueAuthority`, and a variant is a delegating class
     * rather than a `TierAProfile`. So a goblet-squat technique rule is a squat
     * rule, issued under the squat profile, reached through `variant.parent`.
     * That is what "not a separate algorithm" means in types: a variant cannot
     * author rules of its own, only be analysed by its parent's.
     *
     * It needs no special case for the other tiers either - a Tier B parent has
     * no authority to lend, so a Tier B variant cannot reach one.
     */
    @Test
    fun a_variant_inherits_the_claim_ceiling_but_never_the_authority_to_reach_it() {
        for (variant in variants) {
            assertFalse(
                variant is TierAProfile,
                variant.id + " is a TierAProfile, so it can mint its own technique authority",
            )
            assertEquals(
                variant.parent.maxClaimLevel,
                variant.maxClaimLevel,
                variant.id + " does not carry its parent's claim ceiling",
            )

            // The following does not compile, and must never be made to:
            //
            //     ExerciseLibrary.gobletSquat.techniqueAuthority
            //         // unresolved reference: techniqueAuthority
            //
            // The route that does exist runs through the parent, which is the
            // profile that owns the rules.
        }
    }

    @Test
    fun every_variant_carries_a_coaching_note_that_says_something_its_parent_does_not() {
        for (variant in variants) {
            assertTrue(
                variant.coachingNote.isNotBlank(),
                variant.id + " has no coaching note, which is one of the two things a " +
                    "variant is allowed to have of its own",
            )
            assertNotEquals(
                variant.parent.displayName,
                variant.coachingNote,
                variant.id + " has a coaching note that only repeats the parent's name",
            )
        }
    }

    // --- The goblet squat specifically -------------------------------------------------

    @Test
    fun the_goblet_squat_is_a_squat_variant_and_not_a_profile_of_its_own() {
        val goblet = ExerciseLibrary.gobletSquat
        assertEquals("goblet_squat", goblet.id)
        assertSame(
            ExerciseLibrary.squat,
            goblet.parent,
            "the goblet squat must delegate to the squat profile instance itself, not to a copy",
        )
        assertFalse(
            ExerciseLibrary.profiles.any { it.id == "goblet_squat" },
            "the goblet squat is counted as a profile, which is the miscount CLAUDE.md " +
                "section 9 forbids",
        )
    }

    /**
     * The declarations above say a variant and its parent agree. This shows they
     * behave the same way: identical synthetic repetitions, driven through a
     * tracker built on the variant and one built on the parent, must produce the
     * same count, the same validity and the same ranges of motion.
     *
     * A variant that declared everything correctly but was somehow routed through
     * different analysis would pass every assertion above and fail this one.
     *
     * Swept across every variant, with the track derived from the parent's own
     * thresholds so it works for whatever the parent measures. A variant whose
     * parent drives off a joint this test has no frame builder for fails loudly
     * rather than being skipped - losing behavioural coverage silently is the one
     * outcome worse than a noisy test.
     */
    @Test
    fun every_variant_and_its_parent_analyse_identical_movement_identically() {
        assertTrue(variants.isNotEmpty(), "no variants to compare")

        for (variant in variants) {
            val parent = variant.parent
            if (parent !is RepetitionExercise) continue
            val where = variant.id + " vs " + parent.id + ": "

            val track = trackFor(parent, reps = 6)
            val viaParent = count(parent, track)
            val viaVariant = count(variant as RepetitionExercise, track)

            assertTrue(viaParent.isNotEmpty(), where + "the parent counted nothing")
            assertEquals(
                viaParent.size,
                viaVariant.size,
                where + "the variant counted a different number of repetitions than its parent",
            )

            for (index in viaParent.indices) {
                val a = viaParent[index]
                val b = viaVariant[index]
                assertEquals(a.validity, b.validity, where + "repetition " + index + " validity")
                assertEquals(
                    a.attributedSide,
                    b.attributedSide,
                    where + "repetition " + index + " side",
                )
                assertEquals(
                    a.rangeOfMotion.left,
                    b.rangeOfMotion.left,
                    absoluteTolerance = 1e-12,
                    message = where + "repetition " + index + " left range of motion",
                )
                assertEquals(
                    a.rangeOfMotion.right,
                    b.rangeOfMotion.right,
                    absoluteTolerance = 1e-12,
                    message = where + "repetition " + index + " right range of motion",
                )
            }
        }
    }

    // --- Driving the tracker -------------------------------------------------------------

    private fun count(
        profile: RepetitionExercise,
        track: List<Pair<Double, Landmarks>>,
    ): List<RepRecord> {
        val tracker = BilateralRepTracker(profile)
        val counted = ArrayList<RepRecord>()
        for ((timestamp, frame) in track) {
            tracker.onFrame(timestamp, frame, policy) { counted += it }
        }
        return counted.filter { it.validity == RepValidity.VALID }
    }

    /**
     * A movement that clears the profile's own thresholds by a margin, so the
     * same construction works for any parent rather than hard-coding one
     * exercise's angles.
     */
    private fun trackFor(profile: RepetitionExercise, reps: Int): List<Pair<Double, Landmarks>> {
        val spec = profile.repSignal
        val outward = if (spec.topIsGreater) -1.0 else 1.0
        val start = spec.topThreshold - outward * 8.0
        val far = spec.bottomThreshold + outward * 12.0

        val frame: (Double) -> Landmarks = when (spec.joint) {
            JointAngleId.KNEE -> { degrees -> legFrame(degrees) }
            else -> throw AssertionError(
                profile.id + " drives off " + spec.joint + " and this test has no frame " +
                    "builder for it. Add one rather than dropping its variant from the sweep.",
            )
        }

        val frames = ArrayList<Pair<Double, Landmarks>>()
        var index = 0
        fun emit(value: Double) {
            frames += (index * frameMs) to frame(value)
            index++
        }

        repeat(12) { emit(start) }
        repeat(reps) {
            for (step in 1..30) emit(start + (far - start) * step / 30.0)
            repeat(10) { emit(far) }
            for (step in 1..30) emit(far - (far - start) * step / 30.0)
            repeat(12) { emit(start) }
        }
        return frames
    }

    /** Hip - knee - ankle on both legs, at exactly [degrees]. */
    private fun legFrame(degrees: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = 0.95
        }
        fun leg(hip: LandmarkId, knee: LandmarkId, ankle: LandmarkId, x: Double) {
            put(hip, x, 0.50)
            put(knee, x, 0.70)
            val radians = degrees * PI / 180.0
            put(ankle, x + 0.20 * sin(radians), 0.70 - 0.20 * cos(radians))
        }
        leg(LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE, 0.44)
        leg(LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE, 0.56)
        return Landmarks.fromPacked(packed)
    }
}
