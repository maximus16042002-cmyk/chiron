package com.kinesis.core.analysis

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.analysis.rep.BilateralRepTracker
import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.Posture
import com.kinesis.core.domain.profile.Tier
import com.kinesis.core.domain.profile.TierAProfile
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three profiles that complete Tier A - CONTRACTS.md section 3,
 * DECISIONS.md P3-5.
 *
 * Push-up, Lateral Raise (seated) and Glute Bridge join the Squat and Bicep Curl
 * archetypes, which are pinned separately in [ArchetypeProfileTest]. Written
 * before the profiles they guard, per CLAUDE.md section 18.
 *
 * ### What is being guarded, and why each of these is worth a test
 *
 * **Repetition semantics.** The same factor-of-two error the bicep-curl pin
 * exists to prevent. P3-5 measured push-up and lateral raise against MM-Fit's
 * label files - both are `SIMULTANEOUS`, on two independent signals, with not
 * one of the 121 sets reading half the label. The glute bridge has no label data
 * anywhere and is `SIMULTANEOUS` **by assumption**; that distinction is carried
 * in the tests below rather than flattened, because an assumption that looks
 * like a measurement is how a weak basis gets forgotten.
 *
 * **Camera view and posture.** Lateral Raise is seated (P0-4). A technique rule
 * derived from seated data does not transfer to standing, so this is not a
 * cosmetic field.
 *
 * **Claim ceiling.** All three are Tier A, so all three can reach Level 3. The
 * tier is what grants that, and these assertions are what notice if one is
 * silently re-tiered.
 */
class TierAProfileTest {

    private val policy = VisibilityPolicy.PROVISIONAL
    private val frameMs = 1000.0 / 30.0

    // --- The library, and what it contains ---------------------------------------

    /**
     * `ArchetypeProfiles.all` used to be the set of every profile that existed.
     * It is not any more, and a sweep that still iterated it would silently stop
     * covering everything added after it. [ExerciseLibrary] is now the registry,
     * and this is the pin that keeps growth deliberate.
     *
     * Entries, not profiles: the goblet squat is a library entry that shares the
     * squat's profile (DECISIONS.md P3-6). The two counts are pinned separately
     * below, because conflating them is what CLAUDE.md section 9 forbids.
     */
    @Test
    fun the_library_holds_exactly_the_entries_built_so_far() {
        assertEquals(
            listOf(
                "bicep_curl", "glute_bridge", "goblet_squat", "hip_abduction",
                "knee_raise", "lateral_raise", "leg_raise", "lunge", "plank",
                "pushup", "reverse_lunge", "row", "shoulder_press", "side_plank",
                "situp", "squat", "wall_sit",
            ),
            ExerciseLibrary.all.map { it.id }.sorted(),
        )
    }

    @Test
    fun the_library_holds_exactly_the_profiles_built_so_far() {
        assertEquals(
            listOf(
                "bicep_curl", "glute_bridge", "hip_abduction", "knee_raise",
                "lateral_raise", "leg_raise", "lunge", "plank", "pushup", "row",
                "shoulder_press", "side_plank", "situp", "squat", "wall_sit",
            ),
            ExerciseLibrary.profiles.map { it.id }.sorted(),
        )
    }

    /**
     * CONTRACTS.md section 3 assigns Tier A to exactly five exercises. With this
     * batch that set is complete, and nothing else may quietly join it - Tier A
     * is the only tier that can produce a technique judgement at all.
     *
     * Swept over profiles rather than entries. A variant inherits its parent's
     * tier and does not add one to it, so counting entries here would read the
     * goblet squat as a sixth Tier A exercise - the exact miscount CLAUDE.md
     * section 9 exists to prevent.
     */
    @Test
    fun tier_a_is_exactly_the_five_exercises_contracts_assigns_it() {
        assertEquals(
            listOf("bicep_curl", "glute_bridge", "lateral_raise", "pushup", "squat"),
            ExerciseLibrary.profiles.filter { it.tier == Tier.A }.map { it.id }.sorted(),
        )
    }

    @Test
    fun every_profile_id_is_unique_because_persistence_keys_on_it() {
        val ids = ExerciseLibrary.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "duplicate profile ids: " + ids)
    }

    // --- Tiers and claim levels -----------------------------------------------------

    /**
     * The compiler reports the `is TierAProfile` check as always true, which is
     * the guarantee rather than a defect: the three are statically known to
     * extend it, so the claim ceiling cannot be restated by a subclass. The
     * assertion stays so that if one is ever re-tiered, the warning disappears
     * and the check starts doing real work.
     */
    @Suppress("USELESS_IS_CHECK")
    @Test
    fun each_new_profile_declares_tier_a_and_the_ceiling_that_follows_from_it() {
        for (profile in listOf(
            ExerciseLibrary.pushUp,
            ExerciseLibrary.lateralRaise,
            ExerciseLibrary.gluteBridge,
        )) {
            assertEquals(Tier.A, profile.tier, profile.id + " is not Tier A")
            assertEquals(
                ClaimLevel.L3_TECHNIQUE,
                profile.maxClaimLevel,
                profile.id + " has a ceiling that disagrees with its tier",
            )
            assertTrue(profile is TierAProfile, profile.id + " does not extend TierAProfile")
        }
    }

    // --- The camera-view contract -----------------------------------------------------

    /**
     * CLAUDE.md section 10 and CONTRACTS.md section 8: every profile declares the
     * view it must be filmed from, and the reasoning sits beside the declaration
     * in the profile source.
     *
     * The seated posture on Lateral Raise is DECISIONS.md P0-4 and is the one
     * here that is a decision rather than a description.
     */
    @Test
    fun each_new_profile_declares_an_explicit_camera_view_and_posture() {
        // Elbow flexion and the trunk line are both sagittal; from the head end
        // the elbow angle is foreshortening rather than flexion.
        assertEquals(CameraView.SIDE_ON, ExerciseLibrary.pushUp.requiredView)
        assertEquals(Posture.PRONE, ExerciseLibrary.pushUp.posture)

        // Abduction is a frontal-plane movement, so it is only measurable
        // front-on. Seated per P0-4, matching MM-Fit.
        assertEquals(CameraView.FRONT_ON, ExerciseLibrary.lateralRaise.requiredView)
        assertEquals(
            Posture.SEATED,
            ExerciseLibrary.lateralRaise.posture,
            "DECISIONS.md P0-4: the lateral raise is seated, matching MM-Fit. A rule " +
                "derived from seated data does not transfer to standing.",
        )

        // Hip extension is sagittal; from above, a bridge and a flat back look
        // the same.
        assertEquals(CameraView.SIDE_ON, ExerciseLibrary.gluteBridge.requiredView)
        assertEquals(Posture.SUPINE, ExerciseLibrary.gluteBridge.posture)
    }

    // The sweep that used to live here -
    // `every_profile_requires_the_landmarks_its_driving_joint_is_built_from` -
    // moved to `ViewAndSideContractTest` and gained the other half of the rule.
    // It asserted only that a profile requires its driving joint on both sides,
    // which would have forced the near-side-only row to require a far elbow and
    // wrist it can never see - and `ValidityGate` turns any unseen required
    // landmark into an INVALID frame, blocking the exercise outright. The
    // replacement keeps that assertion for both-sided profiles and adds the
    // opposite one for near-side-only profiles, so it is strictly more than what
    // it replaces. DECISIONS.md P3-8.

    // --- Repetition semantics: the measured pins -------------------------------------

    /**
     * Permanent guard - DECISIONS.md P3-5, CLAUDE.md section 18.
     *
     * Measured from MM-Fit's own label files by the method P3-1 established: each
     * side completes one full cycle per labelled repetition, on both the raw
     * vertical signal and the joint angle the profile drives off, with left and
     * right in phase at +0.99 or better. In none of the 65 push-up sets and none
     * of the 56 lateral-raise sets do both sides read half the label - which is
     * what an alternating exercise looks like, and is exactly what the bicep-curl
     * control does read in the same run.
     *
     * If either of these silently flips to `PER_LIMB`, every MM-Fit rep-count
     * comparison for it doubles, and it presents as a threshold-tuning problem.
     */
    @Test
    fun push_up_and_lateral_raise_are_simultaneous_matching_the_mmfit_label_definition() {
        assertEquals(
            RepetitionSemantics.SIMULTANEOUS,
            ExerciseLibrary.pushUp.repetitionSemantics,
        )
        assertEquals(
            RepetitionSemantics.SIMULTANEOUS,
            ExerciseLibrary.lateralRaise.repetitionSemantics,
        )
    }

    /**
     * Permanent guard - DECISIONS.md P3-5.
     *
     * **This one is an assumption, and the test says so deliberately.** MM-Fit has
     * no glute-bridge class, so unlike the two above there is no label file this
     * can be checked against. It is `SIMULTANEOUS` because the movement is a
     * bilateral hip extension with both feet planted and no common alternating
     * variant - the single-leg bridge being a different exercise with its own
     * loading, which would be its own profile.
     *
     * Kept separate from the measured pin above so that the weaker basis stays
     * visible. If P4 or a later benchmark contradicts it, this is the test to
     * change, and P3-5 is the entry to correct.
     */
    @Test
    fun a_glute_bridge_repetition_is_simultaneous_by_anatomy_with_no_dataset_behind_it() {
        assertEquals(
            RepetitionSemantics.SIMULTANEOUS,
            ExerciseLibrary.gluteBridge.repetitionSemantics,
        )
    }

    // --- Repetition semantics: the behaviour, not just the declaration ----------------

    /**
     * The declaration pins intent; this pins what the engine actually does with
     * it. Ten two-sided push-ups are ten repetitions, not twenty, and each one
     * belongs to no single side.
     */
    @Test
    fun ten_push_ups_count_as_ten_repetitions_and_not_twenty() {
        val counted = run(ExerciseLibrary.pushUp, pushUpTrack(reps = 10))
        assertEquals(
            10,
            counted.count { it.validity == RepValidity.VALID },
            "ten push-ups must count once each, not once per arm",
        )
        assertTrue(
            counted.all { it.attributedSide == null },
            "a simultaneous repetition belongs to no single side",
        )
    }

    @Test
    fun ten_lateral_raises_count_as_ten_repetitions_and_not_twenty() {
        val counted = run(ExerciseLibrary.lateralRaise, lateralRaiseTrack(reps = 10))
        assertEquals(
            10,
            counted.count { it.validity == RepValidity.VALID },
            "ten lateral raises must count once each, not once per arm",
        )
        assertTrue(counted.all { it.attributedSide == null })
    }

    // DECISIONS.md P3-29: the lateral raise's start zone, at or below 36 degrees.

    /**
     * On MM-Fit, about one return in five between raises stops at 28-33 degrees -
     * the upper arm clears the thighs when seated. With the start zone at or
     * below 28, each such return merged two raises into one (P3-28). Here every
     * return stops at 32 degrees.
     */
    @Test
    fun raises_whose_arms_come_down_only_to_thirty_two_degrees_still_count_each_raise() {
        val counted = run(ExerciseLibrary.lateralRaise, lateralRaiseTrack(reps = 10, returnTo = 32.0))
        assertEquals(10, counted.count { it.validity == RepValidity.VALID }, counted.map { it.validity }.toString())
    }

    /**
     * The limit of that loosening: arms that come down only to 42 degrees have not
     * returned to the start, and nothing after the first raise's ascent counts.
     */
    @Test
    fun raises_whose_arms_come_down_only_to_forty_two_degrees_do_not_count_as_separate_raises() {
        val counted = run(ExerciseLibrary.lateralRaise, lateralRaiseTrack(reps = 10, returnTo = 42.0))
        assertEquals(0, counted.count { it.validity == RepValidity.VALID }, counted.map { it.validity }.toString())
    }

    /**
     * The zones P3-29 decided, pinned: a start at or below 36 degrees, a far
     * position at or above 77 unchanged, so a raise still sweeps at least 41.
     */
    @Test
    fun the_lateral_raise_zones_are_the_decided_ones() {
        val spec = ExerciseLibrary.lateralRaise.repSignal
        assertEquals(36.0, spec.topThreshold + spec.hysteresis, "start zone edge")
        assertEquals(77.0, spec.bottomThreshold - spec.hysteresis, "far zone edge")
    }

    @Test
    fun ten_glute_bridges_count_as_ten_repetitions_and_not_twenty() {
        val counted = run(ExerciseLibrary.gluteBridge, gluteBridgeTrack(reps = 10))
        assertEquals(
            10,
            counted.count { it.validity == RepValidity.VALID },
            "ten glute bridges must count once each, not once per leg",
        )
        assertTrue(counted.all { it.attributedSide == null })
    }

    /**
     * A simultaneous repetition still carries both sides separately, which is
     * what makes asymmetry measurable at all - CLAUDE.md section 3. Here one arm
     * is deliberately raised less far than the other.
     */
    @Test
    fun a_lateral_raise_with_one_arm_short_reports_the_asymmetry_rather_than_hiding_it() {
        val counted = run(
            ExerciseLibrary.lateralRaise,
            lateralRaiseTrack(reps = 3, leftFar = 95.0, rightFar = 115.0),
        )
        val valid = counted.filter { it.validity == RepValidity.VALID }
        assertTrue(valid.isNotEmpty(), "no repetitions were counted")
        val record = valid[0]
        assertTrue(
            record.rangeOfMotion.left < record.rangeOfMotion.right,
            "the arm that travelled less should show the smaller range, got " +
                record.rangeOfMotion,
        )
        assertTrue(
            record.rangeOfMotion.asymmetry() > 0.0,
            "an asymmetric raise reported perfect symmetry",
        )
    }

    // --- Signal declarations ------------------------------------------------------------

    @Test
    fun each_new_profile_drives_off_the_joint_that_defines_its_movement() {
        assertEquals(JointAngleId.ELBOW, ExerciseLibrary.pushUp.repSignal.joint)
        assertEquals(JointAngleId.SHOULDER, ExerciseLibrary.lateralRaise.repSignal.joint)
        assertEquals(JointAngleId.HIP, ExerciseLibrary.gluteBridge.repSignal.joint)
    }

    /**
     * Swept across the whole library rather than the three new profiles, so this
     * keeps holding as later batches land. A hysteresis band wider than the
     * movement it sits inside makes both thresholds true at once, and the FSM
     * would never leave the top.
     */
    @Test
    fun every_repetition_spec_in_the_library_has_a_usable_threshold_pair() {
        for (profile in ExerciseLibrary.all) {
            if (profile !is RepetitionExercise) continue
            val spec = profile.repSignal
            assertTrue(
                spec.hysteresis * 2 < kotlin.math.abs(spec.topThreshold - spec.bottomThreshold),
                profile.id + " has a hysteresis band wider than the movement it measures",
            )
            assertTrue(
                spec.maxRepDurationMs > spec.minRepDurationMs,
                profile.id + " has a repetition window that excludes everything",
            )
        }
    }

    // --- Driving the tracker ---------------------------------------------------------------

    private fun run(
        profile: RepetitionExercise,
        track: List<Pair<Double, Landmarks>>,
    ): List<RepRecord> {
        val tracker = BilateralRepTracker(profile)
        val counted = ArrayList<RepRecord>()
        for ((timestamp, frame) in track) {
            tracker.onFrame(timestamp, frame, policy) { counted += it }
        }
        return counted
    }

    // --- Synthetic movement ------------------------------------------------------------------

    /**
     * Both elbows extend and flex together: the push-up as MM-Fit's labels
     * define it. Start near lockout, down to a bent elbow, back up.
     */
    private fun pushUpTrack(reps: Int): List<Pair<Double, Landmarks>> =
        repetitionTrack(reps, start = 172.0, far = 78.0) { left, right ->
            armFrame(left, right)
        }

    /** Both arms abduct from the sides to overhead-of-horizontal and back down. */
    private fun lateralRaiseTrack(
        reps: Int,
        leftFar: Double = 110.0,
        rightFar: Double = 110.0,
        returnTo: Double = 10.0,
    ): List<Pair<Double, Landmarks>> =
        repetitionTrack(reps, start = 10.0, far = leftFar, farRight = rightFar, returnTo = returnTo) { left, right ->
            abductionFrame(left, right)
        }

    /** Both hips extend from resting on the floor to a straight body line. */
    private fun gluteBridgeTrack(reps: Int): List<Pair<Double, Landmarks>> =
        repetitionTrack(reps, start = 132.0, far = 178.0) { left, right ->
            hipFrame(left, right)
        }

    /**
     * One shape for all three: dwell at the start, travel out, dwell, travel
     * back, dwell. The dwells matter - the pipeline smooths with an EMA, and a
     * track that reverses the instant it reaches an extreme never gets there.
     * Each return goes to [returnTo], and the next repetition leaves from there.
     */
    private fun repetitionTrack(
        reps: Int,
        start: Double,
        far: Double,
        farRight: Double = far,
        returnTo: Double = start,
        frame: (Double, Double) -> Landmarks,
    ): List<Pair<Double, Landmarks>> {
        val frames = ArrayList<Pair<Double, Landmarks>>()
        var index = 0
        fun emit(left: Double, right: Double) {
            frames += (index * frameMs) to frame(left, right)
            index++
        }

        repeat(12) { emit(start, start) }
        repeat(reps) { rep ->
            val from = if (rep == 0) start else returnTo
            for (step in 1..30) {
                emit(
                    from + (far - from) * step / 30.0,
                    from + (farRight - from) * step / 30.0,
                )
            }
            repeat(10) { emit(far, farRight) }
            for (step in 1..30) {
                emit(
                    far - (far - returnTo) * step / 30.0,
                    farRight - (farRight - returnTo) * step / 30.0,
                )
            }
            repeat(12) { emit(returnTo, returnTo) }
        }
        return frames
    }

    // --- Frame construction --------------------------------------------------------------------

    private fun put(packed: DoubleArray, id: LandmarkId, x: Double, y: Double) {
        val base = id.index * Landmarks.STRIDE
        packed[base] = x
        packed[base + 1] = y
        packed[base + 2] = 0.0
        packed[base + 3] = 0.95
    }

    /**
     * Places a three-landmark joint whose interior angle at the vertex is exactly
     * [degrees].
     *
     * The proximal landmark goes one limb-length along [dirX]/[dirY] from the
     * vertex; the distal one goes the same distance along that direction rotated
     * by [degrees]. The interior angle between the two rays is therefore the
     * requested value by construction, with no dependence on where in the frame
     * the joint sits.
     */
    private fun joint(
        packed: DoubleArray,
        proximal: LandmarkId,
        vertex: LandmarkId,
        distal: LandmarkId,
        vertexX: Double,
        vertexY: Double,
        dirX: Double,
        dirY: Double,
        degrees: Double,
        limb: Double = 0.20,
    ) {
        put(packed, vertex, vertexX, vertexY)
        put(packed, proximal, vertexX + dirX * limb, vertexY + dirY * limb)
        val radians = degrees * PI / 180.0
        val rx = dirX * cos(radians) - dirY * sin(radians)
        val ry = dirX * sin(radians) + dirY * cos(radians)
        put(packed, distal, vertexX + rx * limb, vertexY + ry * limb)
    }

    /** Shoulder - elbow - wrist, both arms. */
    private fun armFrame(leftDeg: Double, rightDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        // Ray elbow -> shoulder points up the image.
        joint(
            packed, LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST,
            0.40, 0.50, 0.0, -1.0, leftDeg,
        )
        joint(
            packed, LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST,
            0.60, 0.50, 0.0, -1.0, rightDeg,
        )
        return Landmarks.fromPacked(packed)
    }

    /** Hip - shoulder - elbow, both arms: shoulder abduction. */
    private fun abductionFrame(leftDeg: Double, rightDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        // Ray shoulder -> hip points down the image, so zero degrees is an arm
        // hanging at the side and ninety is an arm held horizontal.
        joint(
            packed, LandmarkId.LEFT_HIP, LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW,
            0.42, 0.35, 0.0, 1.0, leftDeg,
        )
        joint(
            packed, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW,
            0.58, 0.35, 0.0, 1.0, rightDeg,
        )
        return Landmarks.fromPacked(packed)
    }

    /** Shoulder - hip - knee, both sides: the trunk-to-thigh line. */
    private fun hipFrame(leftDeg: Double, rightDeg: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        // Ray hip -> shoulder points along the floor, the subject being supine.
        joint(
            packed, LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE,
            0.50, 0.60, -1.0, 0.0, leftDeg,
        )
        joint(
            packed, LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE,
            0.50, 0.62, -1.0, 0.0, rightDeg,
        )
        return Landmarks.fromPacked(packed)
    }
}
