package com.kinesis.core.analysis

import com.kinesis.core.analysis.profile.AnalysedSides
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.analysis.rep.BilateralRepTracker
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.VisibilityPolicy
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.kinesis.core.analysis.kinematics.BodyPoint

/**
 * Permanent guard - CLAUDE.md sections 3, 9 and 10; CONTRACTS.md section 8;
 * DECISIONS.md P3-8.
 *
 * Every profile has always declared a `requiredView` and a `posture`, and until
 * this file existed **nothing read either of them**. They were pinned by
 * per-profile equality assertions and consumed by no code and no sweep, so a
 * profile could declare a view from which its own driving joint is unmeasurable
 * and nothing would notice. P3-7 recorded one instance of exactly that going
 * unactioned in the source documents - the lunge - and this is the check that
 * stops the same thing happening inside the codebase.
 *
 * ### What is actually checkable
 *
 * Not "is this view sensible for this exercise" - that is a judgement the
 * documents make per exercise, and inventing a general rule here would
 * contradict them (the push-up and the bicep curl both drive off the elbow from
 * opposite views, each for a stated reason). What *is* checkable is that the
 * declared view, the declared semantics and the declared landmark requirements
 * do not contradict each other:
 *
 * - a profile whose far limb is invisible cannot wait for that limb to complete
 *   a repetition, so it cannot pair sides;
 * - a profile whose far limb is invisible must not require landmarks on it,
 *   because `ValidityGate` invalidates a frame on **any** required landmark
 *   below the floor, which would block the exercise entirely;
 * - a profile that does analyse both sides must require the joint it measures on
 *   both sides, or the gate is not checking what the analysis uses.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class ViewAndSideContractTest {

    private val policy = VisibilityPolicy.PROVISIONAL
    private val frameMs = 1000.0 / 30.0

    private val repetitionProfiles: List<RepetitionExercise>
        get() = ExerciseLibrary.all.filterIsInstance<RepetitionExercise>()

    // --- The declared combination must be coherent --------------------------------

    /**
     * Alternating limbs and a view that can only see one of them is a
     * contradiction with no honest resolution: half the repetitions happen on a
     * limb the camera never sees, and no amount of type-level care recovers a
     * measurement that was never made. Such an exercise needs a different view or
     * has to be cut - the decision FINAL_ARCHITECTURE section 5 made for the
     * front raise.
     */
    @Test
    fun no_profile_combines_alternating_limbs_with_a_view_that_sees_only_one() {
        for (profile in repetitionProfiles) {
            assertFalse(
                profile.repetitionSemantics == RepetitionSemantics.PER_LIMB &&
                    profile.analysedSides == AnalysedSides.NEAR_SIDE_ONLY,
                profile.id + " alternates limbs but can only see one of them, so half " +
                    "its repetitions are unobservable",
            )
        }
    }

    /**
     * The landmark requirement has to match what the view can deliver, in both
     * directions.
     *
     * Replaces `TierAProfileTest.every_profile_requires_the_landmarks_its_driving_joint_is_built_from`,
     * which asserted only the first half. That version would have forced the row
     * to require a far elbow and wrist it can never see, which
     * `ValidityGate.evaluate` turns into `INVALID` on every frame - blocking the
     * exercise completely. This one keeps that assertion for both-sided profiles
     * and adds the opposite assertion for near-side-only ones, so it is strictly
     * more than the test it replaces rather than an exemption from it.
     * DECISIONS.md P3-8.
     */
    @Test
    fun the_landmarks_a_profile_requires_match_what_its_view_can_deliver() {
        // Isometric holds have no `analysedSides` - P3-9 explains why - but the
        // first half of the rule applies to them unchanged: a hold judged on a
        // joint must require that joint, or the gate is not checking what the
        // analysis uses.
        for (hold in ExerciseLibrary.all.filterIsInstance<IsometricExercise>()) {
            val joint = hold.holdSignal.joint
            val declared = hold.requiredLandmarks.toList()
            for (point in listOf(joint.proximal, joint.vertex, joint.distal)) {
                for (id in listOf(point.leftId, point.rightId)) {
                    assertTrue(
                        id in declared,
                        hold.id + " holds on " + joint + " but does not require " + id,
                    )
                }
            }
        }

        for (profile in repetitionProfiles) {
            val joint = profile.repSignal.joint
            // DECISIONS.md P3-45: every point the driving signal uses, a joint angle's three or a
            // segment's two.
            val bothSides = joint.points.flatMap { listOf(it.leftId, it.rightId) }
            val declared = profile.requiredLandmarks.toList()

            when (profile.analysedSides) {
                AnalysedSides.BOTH -> for (id in bothSides) {
                    assertTrue(
                        id in declared,
                        profile.id + " analyses both sides off " + joint +
                            " but does not require " + id,
                    )
                }

                AnalysedSides.NEAR_SIDE_ONLY -> {
                    // DECISIONS.md P3-46: a driver made of the torso alone - shoulders and hips,
                    // which every frame's torso check reads anyway - is the one exception. On
                    // MM-Fit's 45 side-on development sit-ups the far shoulder and hip were
                    // measurable in every frame; it is the far knee or wrist that a side-on view
                    // loses (P3-28).
                    val torsoOnly = joint.points.all { it == BodyPoint.SHOULDER || it == BodyPoint.HIP }
                    if (!torsoOnly) {
                        assertFalse(
                            declared.containsAll(bothSides),
                            profile.id + " can only see one side but requires " + joint +
                                " on both, which makes every frame INVALID by construction",
                        )
                    }
                    assertTrue(
                        declared.isNotEmpty(),
                        profile.id + " requires no landmarks at all, so the gate checks nothing",
                    )
                }
            }
        }
    }

    /**
     * The row was the only one until P3-28, when the owner moved the push-up and the
     * sit-up to near-side-only: on MM-Fit their far side was measurable in 42% and
     * 53% of frames, and both counted a repetition only when both sides completed
     * one - 5 of 649 push-ups and 101 of 640 sit-ups counted (P3-27).
     *
     * P3-68 added the squat, on the owner's decision after the phone session of 2026-10-07: side-on
     * the far knee is often hidden behind the near leg. The goblet squat inherits it from its parent.
     */
    @Test
    fun the_near_side_only_entries_are_exactly_the_decided_ones() {
        assertEquals(
            listOf("goblet_squat", "pushup", "row", "situp", "squat"),
            repetitionProfiles
                .filter { it.analysedSides == AnalysedSides.NEAR_SIDE_ONLY }
                .map { it.id }
                .sorted(),
            "a near-side-only profile gives up symmetry entirely; adding one is a " +
                "decision that belongs in DECISIONS.md (P3-8, P3-28, P3-68)",
        )
    }

    // --- The behaviour under a permanently invisible far side ------------------------

    /**
     * The failure this whole mechanism exists to prevent.
     *
     * Before `AnalysedSides` was acted on, a `SIMULTANEOUS` profile whose far arm
     * never appears held each completed near-side cycle in `pendingLeft` waiting
     * for a partner, **overwriting the previous one each time**, and flushed a
     * single record at the end of the set - marked `INSUFFICIENT_DATA`, because
     * it never found a partner. Run against that code this test reported
     * `expected:<10> but was:<0>`: ten repetitions became **no counted
     * repetitions at all**, and nine of the ten left no record of any kind. That
     * is precisely the silent wrongness CLAUDE.md section 18 says tests exist to
     * catch, and it was observed rather than predicted - the guess before running
     * it was that one would survive.
     */
    @Test
    fun a_near_side_only_profile_counts_every_repetition_the_visible_side_performs() {
        val counted = run(
            ExerciseLibrary.row,
            oneArmedTrack(reps = 10, start = 168.0, far = 62.0),
        )
        assertEquals(
            10,
            counted.count { it.validity == RepValidity.VALID },
            "ten rows on the visible arm must be reported as ten repetitions",
        )
    }

    /**
     * FINAL_ARCHITECTURE section 5 on the row: "no per-side comparison, no
     * symmetry claim." Structural rather than editorial - the unmeasured side is
     * `NaN`, and every reducer on [com.kinesis.core.domain.bilateral.BilateralSignal]
     * propagates it, so there is no finite asymmetry number for anything
     * downstream to report.
     */
    @Test
    fun a_near_side_only_repetition_reports_one_side_and_no_symmetry_number() {
        val counted = run(
            ExerciseLibrary.row,
            oneArmedTrack(reps = 6, start = 168.0, far = 62.0),
        ).filter { it.validity == RepValidity.VALID }

        assertTrue(counted.isNotEmpty(), "nothing was counted; the assertions below are vacuous")
        for (record in counted) {
            val rom = record.rangeOfMotion
            assertTrue(
                rom.left.isNaN() != rom.right.isNaN(),
                "exactly one side should be measured, got " + rom,
            )
            assertTrue(
                rom.asymmetry().isNaN(),
                "a near-side-only repetition produced a finite asymmetry of " +
                    rom.asymmetry() + ", which is a symmetry claim the document forbids",
            )
            assertEquals(
                null,
                record.attributedSide,
                "the movement is two-armed and only half-seen, so it belongs to no one side",
            )
        }
    }

    // DECISIONS.md P3-28: the push-up and the sit-up, side-on, from the near side alone.

    @Test
    fun a_side_on_push_up_counts_every_repetition_from_the_near_arm_alone() {
        val counted = run(ExerciseLibrary.pushUp, oneArmedTrack(reps = 10, start = 168.0, far = 80.0))
        assertEquals(10, counted.count { it.validity == RepValidity.VALID }, counted.map { it.validity }.toString())
        for (record in counted) {
            assertTrue(record.rangeOfMotion.asymmetry().isNaN(), "a near-side push-up reported a symmetry number")
        }
    }

    /** DECISIONS.md P3-68: the squat, side-on, from the near knee alone. */
    @Test
    fun a_side_on_squat_counts_every_repetition_from_the_near_knee_alone() {
        val counted = run(ExerciseLibrary.squat, oneArmedTrack(reps = 10, start = 175.0, far = 78.0))
        assertEquals(10, counted.count { it.validity == RepValidity.VALID }, counted.map { it.validity }.toString())
        for (record in counted) {
            assertTrue(record.rangeOfMotion.asymmetry().isNaN(), "a near-side squat reported a symmetry number")
        }
    }

    /**
     * Driven off the trunk's inclination since DECISIONS.md P3-46, so it needs the trunk chain
     * staged. With the thigh along the floor, a hip angle of d puts the trunk 180 - d above it:
     * 175 is lying back at 5 degrees, 95 sitting up at 85.
     */
    @Test
    fun a_side_on_sit_up_counts_every_repetition_from_the_near_side_alone() {
        val counted = run(ExerciseLibrary.situp, oneArmedTrack(reps = 10, start = 175.0, far = 95.0, frameAt = ::hipFrame))
        assertEquals(10, counted.count { it.validity == RepValidity.VALID }, counted.map { it.validity }.toString())
        for (record in counted) {
            assertTrue(record.rangeOfMotion.asymmetry().isNaN(), "a near-side sit-up reported a symmetry number")
        }
    }

    // DECISIONS.md P3-29: under near-side counting, only an accepted VALID
    // repetition blocks another that overlaps it.

    /**
     * P3-28's finding on MM-Fit's push-ups and sit-ups, staged on the row. The far
     * arm is seen as each row begins and lost partway down, for longer than a gap
     * is bridged, so its machine gives up - INSUFFICIENT_DATA - before the near arm
     * completes. Until P3-29 that failed attempt claimed its time, and the near
     * arm's VALID repetition, overlapping it, was dropped: no row counted at all.
     * The far arm's failed attempts are still reported; they are not counted.
     */
    @Test
    fun a_far_arm_lost_partway_down_does_not_hide_the_near_arms_repetition() {
        val records = run(
            ExerciseLibrary.row,
            twoArmedTrack(reps = 10, start = 168.0, far = 62.0, farSeen = { it !in 10 until 70 }),
        )
        val validities = records.map { it.validity }.toString()
        assertTrue(
            records.any { it.validity == RepValidity.INSUFFICIENT_DATA },
            "the far arm never gave up, so this staged nothing: $validities",
        )
        assertEquals(10, records.count { it.validity == RepValidity.VALID }, validities)
    }

    /**
     * The double count the rule must not let in. Both arms seen throughout, both
     * rowing, the far one 20 frames (0.67 s) behind - far more than two arms
     * rowing together differ by. Each arm alone counts ten, so both do complete
     * every row; together they still count ten, not twenty.
     */
    @Test
    fun two_arms_both_completing_the_same_rows_count_them_once() {
        fun counted(nearSeen: Boolean, farSeen: Boolean) = run(
            ExerciseLibrary.row,
            twoArmedTrack(
                reps = 10, start = 168.0, far = 62.0, lagFrames = 20,
                nearSeen = { nearSeen }, farSeen = { farSeen },
            ),
        ).count { it.validity == RepValidity.VALID }

        assertEquals(10, counted(nearSeen = true, farSeen = false), "the near arm alone")
        assertEquals(10, counted(nearSeen = false, farSeen = true), "the far arm alone")
        assertEquals(10, counted(nearSeen = true, farSeen = true), "both arms, the same ten rows")
    }

    /**
     * The rule's other half: a counted repetition still blocks whatever overlaps
     * it, failed attempts included. The far arm, 20 frames behind, turns back
     * short of depth each time - an ABORTED cycle that completes after the near
     * arm's counted one. It is the same row half-seen, and is not reported.
     */
    @Test
    fun a_failed_attempt_overlapping_a_counted_repetition_is_not_reported() {
        val records = run(
            ExerciseLibrary.row,
            twoArmedTrack(reps = 10, start = 168.0, far = 62.0, lagFrames = 20, farArm = { maxOf(it, 120.0) }),
        )
        assertEquals(List(10) { RepValidity.VALID }, records.map { it.validity })
    }

    /**
     * The contrast that proves the near-side handling is not a no-op applied
     * everywhere.
     *
     * A lunge analyses both sides. Losing one knee for the whole set is a
     * *failure*, not a supported mode, and it must not quietly produce a full
     * count as though nothing were wrong. (The squat was this test's subject until
     * DECISIONS.md P3-68 made it near-side-only.)
     */
    @Test
    fun a_both_sides_profile_does_not_report_a_full_count_from_half_a_body() {
        val seenWhole = run(ExerciseLibrary.lunge, twoArmedTrack(reps = 10, start = 175.0, far = 78.0))
        assertEquals(
            10,
            seenWhole.count { it.validity == RepValidity.VALID },
            "with both knees seen the lunge must count all ten, or the assertion below is vacuous",
        )

        val counted = run(
            ExerciseLibrary.lunge,
            oneArmedTrack(reps = 10, start = 175.0, far = 78.0),
        )
        assertFalse(
            counted.count { it.validity == RepValidity.VALID } == 10,
            "a lunge with one knee invisible for the whole set reported a full count, " +
                "which would hide the failure rather than surface it",
        )
    }

    // --- Driving the tracker ------------------------------------------------------------

    private fun run(
        profile: RepetitionExercise,
        frames: List<Pair<Double, Landmarks>>,
    ): List<RepRecord> {
        val tracker = BilateralRepTracker(profile)
        val counted = ArrayList<RepRecord>()
        for ((timestamp, frame) in frames) {
            tracker.onFrame(timestamp, frame, policy) { counted += it }
        }
        tracker.finish(frames.last().first + frameMs) { counted += it }
        return counted
    }

    // --- Synthetic movement -------------------------------------------------------------

    /**
     * The near side performs the movement; the far side is present in the frame
     * but below the visibility floor throughout, exactly as an arm behind the
     * torso is. `JointAngles` returns `NaN` for that side, which is what the
     * tracker has to cope with.
     *
     * The track is built from whichever joint the profile drives off, so the same
     * construction serves the row and the squat.
     */
    private fun oneArmedTrack(
        reps: Int,
        start: Double,
        far: Double,
        frameAt: (Double) -> Landmarks = ::frame,
    ): List<Pair<Double, Landmarks>> {
        val frames = ArrayList<Pair<Double, Landmarks>>()
        var index = 0
        fun emit(value: Double) {
            frames += (index * frameMs) to frameAt(value)
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

    /**
     * [oneArmedTrack]'s movement on the near side, and on the far side the same
     * movement [lagFrames] frames later, passed through [farArm]. Each side is seen
     * only where [nearSeen] / [farSeen] say, by frame within the repetition -
     * `0` is the first frame of its descent, `-1` the rest before the first. The
     * set ends with enough rest for a lagging far side to finish. DECISIONS.md P3-29.
     */
    private fun twoArmedTrack(
        reps: Int,
        start: Double,
        far: Double,
        lagFrames: Int = 0,
        farArm: (Double) -> Double = { it },
        nearSeen: (Int) -> Boolean = { true },
        farSeen: (Int) -> Boolean = { true },
    ): List<Pair<Double, Landmarks>> {
        val near = ArrayList<Double>()
        repeat(12) { near += start }
        repeat(reps) {
            for (step in 1..30) near += start + (far - start) * step / 30.0
            repeat(10) { near += far }
            for (step in 1..30) near += far - (far - start) * step / 30.0
            repeat(12) { near += start }
        }
        repeat(lagFrames + 12) { near += start }

        return near.indices.map { index ->
            val inRepetition = if (index < 12) -1 else (index - 12) % 82
            (index * frameMs) to twoSidedFrame(
                near = near[index],
                far = farArm(near[maxOf(0, index - lagFrames)]),
                nearVisibility = if (nearSeen(inRepetition)) 0.95 else 0.05,
                farVisibility = if (farSeen(inRepetition)) 0.95 else 0.05,
            )
        }
    }

    /**
     * Stages both the arm chain and the leg chain at [degrees] on the left, with
     * every right-side landmark below the visibility floor. One builder serves
     * the elbow-driven row and the knee-driven squat.
     */
    private fun frame(degrees: Double): Landmarks =
        // Far side, present in the frame but never seen - below
        // VisibilityPolicy.PROVISIONAL.marginal of 0.40.
        twoSidedFrame(degrees, degrees, nearVisibility = 0.95, farVisibility = 0.05)

    /**
     * [frame] with each side's angle and visibility its own: the near (left) side
     * at [near], the far (right) side at [far]. DECISIONS.md P3-29.
     */
    private fun twoSidedFrame(
        near: Double,
        far: Double,
        nearVisibility: Double,
        farVisibility: Double,
    ): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)

        fun put(id: LandmarkId, x: Double, y: Double, visibility: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 2] = 0.0
            packed[base + 3] = visibility
        }

        fun chain(
            proximal: LandmarkId,
            vertex: LandmarkId,
            distal: LandmarkId,
            vertexX: Double,
            vertexY: Double,
            degrees: Double,
            visibility: Double,
        ) {
            put(vertex, vertexX, vertexY, visibility)
            put(proximal, vertexX, vertexY - 0.20, visibility)
            val radians = degrees * PI / 180.0
            put(distal, vertexX + 0.20 * sin(radians), vertexY - 0.20 * cos(radians), visibility)
        }

        // Near side.
        chain(
            LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST,
            0.40, 0.45, near, nearVisibility,
        )
        chain(
            LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE,
            0.44, 0.70, near, nearVisibility,
        )
        // Far side.
        chain(
            LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST,
            0.60, 0.45, far, farVisibility,
        )
        chain(
            LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE,
            0.56, 0.70, far, farVisibility,
        )
        return Landmarks.fromPacked(packed)
    }

    /**
     * The trunk chain - shoulder, hip, knee - at [degrees] at the hip on the left,
     * the right side present but below the visibility floor: a sit-up side-on
     * (DECISIONS.md P3-28). [frame] cannot serve, because it never moves the hip.
     */
    private fun hipFrame(degrees: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double, visibility: Double) {
            val base = id.index * Landmarks.STRIDE
            packed[base] = x
            packed[base + 1] = y
            packed[base + 3] = visibility
        }
        val radians = degrees * PI / 180.0
        for ((side, x, seen) in listOf(Triple("LEFT", 0.44, 0.95), Triple("RIGHT", 0.46, 0.05))) {
            val hip = LandmarkId.valueOf(side + "_HIP")
            val knee = LandmarkId.valueOf(side + "_KNEE")
            val shoulder = LandmarkId.valueOf(side + "_SHOULDER")
            put(hip, x, 0.60, seen)
            put(knee, x + 0.20, 0.60, seen) // thigh along the floor
            put(shoulder, x + 0.25 * cos(radians), 0.60 - 0.25 * sin(radians), seen)
        }
        return Landmarks.fromPacked(packed)
    }
}
