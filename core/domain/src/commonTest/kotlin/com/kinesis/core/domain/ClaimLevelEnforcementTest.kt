package com.kinesis.core.domain

import com.kinesis.core.domain.finding.ClaimLevel
import com.kinesis.core.domain.finding.Deviation
import com.kinesis.core.domain.finding.DeviationDirection
import com.kinesis.core.domain.finding.Finding
import com.kinesis.core.domain.finding.Measurement
import com.kinesis.core.domain.finding.TechniqueFault
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.core.domain.profile.AnalysisMode
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.profile.Posture
import com.kinesis.core.domain.profile.Tier
import com.kinesis.core.domain.profile.TierAProfile
import com.kinesis.core.domain.profile.TierBProfile
import com.kinesis.core.domain.profile.TierCProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Permanent guard test - CLAUDE.md section 8 and 18, CONTRACTS.md section 2 and 3.
 *
 * Proves that a Tier B or Tier C profile cannot emit a Level 3 finding. The real
 * enforcement is the compiler: a TechniqueFault can only be built from a
 * TechniqueAuthority, and only [TierAProfile] exposes one. This test pins the
 * runtime-observable consequences of that design, so a refactor that quietly
 * reopens the path fails here.
 *
 * CONTRACTS.md section 3 is explicit that filtering results afterwards is not
 * acceptable, so this file never asserts "the L3 findings were removed" - it
 * asserts they could not be produced in the first place.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class ClaimLevelEnforcementTest {

    // --- Test profiles, one per tier -----------------------------------------

    private class SquatUnderTest : TierAProfile() {
        override val id = "squat"
        override val displayName = "Squat"
        override val requiredView = CameraView.SIDE_ON
        override val posture = Posture.STANDING
        override val mode = AnalysisMode.REPETITION
        override val requiredLandmarks = RequiredLandmarks.of(
            LandmarkId.LEFT_HIP,
            LandmarkId.RIGHT_HIP,
            LandmarkId.LEFT_KNEE,
            LandmarkId.RIGHT_KNEE,
        )
    }

    private class RowUnderTest : TierBProfile() {
        override val id = "row"
        override val displayName = "Row"
        override val requiredView = CameraView.SIDE_ON
        override val posture = Posture.STANDING
        override val mode = AnalysisMode.REPETITION
        override val requiredLandmarks = RequiredLandmarks.of(
            LandmarkId.LEFT_SHOULDER,
            LandmarkId.LEFT_ELBOW,
            LandmarkId.LEFT_WRIST,
        )
    }

    private class PlankUnderTest : TierCProfile() {
        override val id = "plank"
        override val displayName = "Plank"
        override val requiredView = CameraView.SIDE_ON
        override val posture = Posture.PRONE
        override val mode = AnalysisMode.ISOMETRIC_HOLD
        override val requiredLandmarks = RequiredLandmarks.of(
            LandmarkId.LEFT_SHOULDER,
            LandmarkId.LEFT_HIP,
            LandmarkId.LEFT_ANKLE,
        )
    }

    private val allProfiles: List<ExerciseProfile> =
        listOf(SquatUnderTest(), RowUnderTest(), PlankUnderTest())

    // --- The ceiling cannot disagree with the tier ---------------------------

    @Test
    fun the_claim_ceiling_is_a_property_of_the_tier_itself() {
        assertEquals(ClaimLevel.L3_TECHNIQUE, Tier.A.maxClaimLevel)
        assertEquals(ClaimLevel.L2_DEVIATION, Tier.B.maxClaimLevel)
        assertEquals(ClaimLevel.L2_DEVIATION, Tier.C.maxClaimLevel)
    }

    @Test
    fun every_profile_reports_the_ceiling_of_its_own_tier() {
        for (profile in allProfiles) {
            assertEquals(
                profile.tier.maxClaimLevel,
                profile.maxClaimLevel,
                profile.id + " reports a ceiling its tier does not grant",
            )
        }
    }

    /**
     * The suppression is the point. The compiler reports these two checks as
     * "always false" because it can prove statically that a Tier B or Tier C
     * profile is not a [TierAProfile] - which is precisely the guarantee under
     * test, delivered at compile time rather than at runtime.
     *
     * Kept as runtime assertions anyway: if a future refactor makes either of
     * them reachable, the warning disappears and the assertion starts doing real
     * work. Removing them would lose both signals.
     */
    @Suppress("USELESS_IS_CHECK")
    @Test
    fun a_tier_b_or_c_profile_cannot_declare_itself_tier_a() {
        // tier and maxClaimLevel are `final override` on the tier base classes,
        // so a subclass cannot restate them. If that `final` is ever dropped,
        // this assertion is the one that notices.
        assertEquals(Tier.B, RowUnderTest().tier)
        assertEquals(Tier.C, PlankUnderTest().tier)
        assertFalse(RowUnderTest() is TierAProfile)
        assertFalse(PlankUnderTest() is TierAProfile)
    }

    // --- The authority token is the gate -------------------------------------

    @Test
    fun only_a_tier_a_profile_carries_a_technique_authority() {
        // Tier A has one.
        val squat = SquatUnderTest()
        assertEquals("squat", squat.techniqueAuthority.describeProfile())

        // Tier B and Tier C do not, and there is no cast that gets one, because
        // the property is declared on TierAProfile and nothing else implements
        // it. These lines do not compile and must never be made to:
        //
        //     RowUnderTest().techniqueAuthority       // unresolved reference
        //     PlankUnderTest().techniqueAuthority     // unresolved reference
        //     TechniqueAuthority(profileId = "row")   // private constructor
        //
        // What is checkable at runtime is that the only profiles which can
        // reach that property are the ones the type system says can.
        for (profile in allProfiles) {
            val hasAuthority = profile is TierAProfile
            assertEquals(
                profile.maxClaimLevel == ClaimLevel.L3_TECHNIQUE,
                hasAuthority,
                profile.id + ": authority and claim ceiling disagree",
            )
        }
    }

    @Test
    fun a_technique_fault_built_from_a_tier_a_authority_is_level_three() {
        val squat = SquatUnderTest()
        val fault = TechniqueFault.from(
            authority = squat.techniqueAuthority,
            faultId = "knee_valgus",
            displayText = "Knees tracked inward during the ascent.",
            ruleVersion = "test-1",
            sourceRef = "Placeholder citation, this test only.",
        )
        assertEquals(ClaimLevel.L3_TECHNIQUE, fault.level)
        assertEquals("squat", fault.profileId)
    }

    // --- sourceRef is not optional -------------------------------------------

    @Test
    fun a_technique_fault_without_a_source_citation_does_not_construct() {
        // DECISIONS.md P0-6: a rule with no traceable source does not ship.
        // That is enforced at the construction site, not in code review.
        val authority = SquatUnderTest().techniqueAuthority
        for (empty in listOf("", "   ", "\t")) {
            assertFailsWith<IllegalArgumentException> {
                TechniqueFault.from(
                    authority = authority,
                    faultId = "knee_valgus",
                    displayText = "Knees tracked inward during the ascent.",
                    ruleVersion = "test-1",
                    sourceRef = empty,
                )
            }
        }
    }

    // --- Level is fixed per type, never a caller's choice ---------------------

    @Test
    fun each_finding_type_carries_a_level_the_caller_cannot_set() {
        val measurement: Finding = Measurement(
            metric = "rom_deg",
            displayText = "Knee flexion reached 118 degrees.",
            ruleVersion = "test-1",
        )
        val deviation: Finding = Deviation(
            metric = "rom_deg",
            direction = DeviationDirection.BELOW_BASELINE,
            displayText = "Depth was below your usual range on this set.",
            ruleVersion = "test-1",
        )
        assertEquals(ClaimLevel.L1_MEASUREMENT, measurement.level)
        assertEquals(ClaimLevel.L2_DEVIATION, deviation.level)

        // There is no constructor parameter named `level` on any of them. The
        // following do not compile:
        //
        //     Measurement(metric = "rom_deg", level = ClaimLevel.L3_TECHNIQUE, ...)
        //     Deviation(metric = "rom_deg", level = ClaimLevel.L3_TECHNIQUE, ...)
    }

    // --- The whole-rule-set sweep --------------------------------------------

    /**
     * CONTRACTS.md section 3: "Across the entire rule set, no Tier B or Tier C
     * profile produces an L3 finding."
     *
     * P1 has no rule set yet, so this runs a stand-in rule runner that tries as
     * hard as it can to emit a technique fault for every profile. It is written
     * so that P3 can swap the stand-in for the real rule registry without
     * touching the assertion.
     */
    @Test
    fun no_tier_b_or_c_profile_produces_a_level_three_finding() {
        for (profile in allProfiles) {
            val findings = runEveryRuleAgainst(profile)
            if (profile.maxClaimLevel == ClaimLevel.L3_TECHNIQUE) continue
            assertTrue(
                findings.none { it.level == ClaimLevel.L3_TECHNIQUE },
                profile.id + " is Tier " + profile.tier + " and produced a Level 3 finding",
            )
        }
    }

    /**
     * Stand-in for the P3 rule registry. Note the shape: the technique branch is
     * reachable only through `is TierAProfile`, because that is the only way to
     * obtain the authority TechniqueFault.from demands. There is no else branch
     * that produces an L3 finding, and none can be written.
     */
    private fun runEveryRuleAgainst(profile: ExerciseProfile): List<Finding> = buildList {
        add(
            Measurement(
                metric = "reps",
                displayText = "8 repetitions.",
                ruleVersion = "test-1",
            ),
        )
        add(
            Deviation(
                metric = "tempo_s",
                direction = DeviationDirection.ABOVE_BASELINE,
                displayText = "Slower than your baseline tempo.",
                ruleVersion = "test-1",
            ),
        )
        if (profile is TierAProfile) {
            add(
                TechniqueFault.from(
                    authority = profile.techniqueAuthority,
                    faultId = "trunk_lean",
                    displayText = "Trunk leaned forward past the cited threshold.",
                    ruleVersion = "test-1",
                    sourceRef = "Placeholder citation, this test only.",
                ),
            )
        }
    }
}
