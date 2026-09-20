package com.kinesis.core.analysis

import com.kinesis.core.analysis.profile.AnalysedSides
import com.kinesis.core.analysis.profile.ExerciseLibrary
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
        for (profile in repetitionProfiles) {
            val joint = profile.repSignal.joint
            val bothSides = listOf(joint.proximal, joint.vertex, joint.distal)
                .flatMap { listOf(it.leftId, it.rightId) }
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
                    assertFalse(
                        declared.containsAll(bothSides),
                        profile.id + " can only see one side but requires " + joint +
                            " on both, which makes every frame INVALID by construction",
                    )
                    assertTrue(
                        declared.isNotEmpty(),
                        profile.id + " requires no landmarks at all, so the gate checks nothing",
                    )
                }
            }
        }
    }

    @Test
    fun the_row_is_the_only_near_side_only_entry_in_the_library() {
        assertEquals(
            listOf("row"),
            repetitionProfiles
                .filter { it.analysedSides == AnalysedSides.NEAR_SIDE_ONLY }
                .map { it.id }
                .sorted(),
            "a near-side-only profile gives up symmetry entirely; adding one is a " +
                "decision that belongs in DECISIONS.md",
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

    /**
     * The contrast that proves the near-side handling is not a no-op applied
     * everywhere.
     *
     * A squat analyses both sides. Losing one knee for the whole set is a
     * *failure*, not a supported mode, and it must not quietly produce a full
     * count as though nothing were wrong.
     */
    @Test
    fun a_both_sides_profile_does_not_report_a_full_count_from_half_a_body() {
        val counted = run(
            ExerciseLibrary.squat,
            oneArmedTrack(reps = 10, start = 168.0, far = 78.0),
        )
        assertFalse(
            counted.count { it.validity == RepValidity.VALID } == 10,
            "a squat with one knee invisible for the whole set reported a full count, " +
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
    ): List<Pair<Double, Landmarks>> {
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

    /**
     * Stages both the arm chain and the leg chain at [degrees] on the left, with
     * every right-side landmark below the visibility floor. One builder serves
     * the elbow-driven row and the knee-driven squat.
     */
    private fun frame(degrees: Double): Landmarks {
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
            visibility: Double,
        ) {
            put(vertex, vertexX, vertexY, visibility)
            put(proximal, vertexX, vertexY - 0.20, visibility)
            val radians = degrees * PI / 180.0
            put(distal, vertexX + 0.20 * sin(radians), vertexY - 0.20 * cos(radians), visibility)
        }

        // Visible near side.
        chain(
            LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST,
            0.40, 0.45, 0.95,
        )
        chain(
            LandmarkId.LEFT_HIP, LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE,
            0.44, 0.70, 0.95,
        )
        // Far side, present in the frame but never seen - below
        // VisibilityPolicy.PROVISIONAL.marginal of 0.40.
        chain(
            LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST,
            0.60, 0.45, 0.05,
        )
        chain(
            LandmarkId.RIGHT_HIP, LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE,
            0.56, 0.70, 0.05,
        )
        return Landmarks.fromPacked(packed)
    }
}
