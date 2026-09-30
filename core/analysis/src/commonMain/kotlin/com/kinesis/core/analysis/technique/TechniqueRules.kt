package com.kinesis.core.analysis.technique

import com.kinesis.core.analysis.calibration.CalibrationKey
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.finding.TechniqueFault
import com.kinesis.core.domain.profile.TechniqueAuthority
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.roundToInt

/**
 * P8 Layer 2, step A: cited technique rules - CLAUDE.md section 12a, DECISIONS.md P3-43.
 *
 * Level 3, Tier A only, by type: [evaluate] demands a [TechniqueAuthority], which only a
 * `TierAProfile` can supply, so no Tier B or C exercise can reach a rule here. A variant speaks
 * under its parent's authority.
 *
 * **What each rule implements.** A position a cited source describes - "level with your
 * shoulders", "fully extended" - read from the driving angle the repetition machine already keeps
 * per side: `peakValue`, the most extended value from the end of the last repetition to this one's
 * far point, and `troughValue`, the far point. No work per frame.
 *
 * **The tolerance is the app's, not the sources'.** The sources describe positions, not error
 * bands. Every rule adds [TOLERANCE_DEG], fixed in P3-43 before any rule ran on any data, for a
 * 2D angle from one camera, MediaPipe's noise and the sources' own "approximately". Every message
 * says where Kinesis starts to speak.
 *
 * **Sides are judged apart** (CLAUDE.md section 3), one message per side, and only a VALID
 * repetition is judged. No message says wrong, unsafe or injured.
 *
 * **No accuracy figure** is attached to any rule, anywhere: there are no fault labels in this
 * project. Their agreement with expert judgement was not measured.
 */
object TechniqueRules {

    const val RULE_VERSION: String = "p8-layer2-1"

    /** The app's own allowance on every described position, degrees - P3-43. */
    const val TOLERANCE_DEG: Double = 20.0

    const val RAISED_ABOVE_SHOULDER_LEVEL: String = "raised_above_shoulder_level"
    const val CURL_STARTED_ELBOW_BENT: String = "curl_started_elbow_bent"
    const val PUSHUP_ARMS_NOT_EXTENDED: String = "pushup_arms_not_extended"

    const val SOURCE_LATERAL_RAISE: String =
        "American Council on Exercise, Exercise Library: Lateral Raise " +
            "(acefitness.org/resources/everyone/exercise-library/26/lateral-raise/, read 2026-10-01): " +
            "\"Continue raising the dumbbells until your arms are level with your shoulders and approximately " +
            "parallel with the floor.\""

    const val SOURCE_CURL: String =
        "American Council on Exercise, Exercise Library: Hammer Curl " +
            "(acefitness.org/resources/everyone/exercise-library/10/hammer-curl/, read 2026-10-01): " +
            "\"gently lower the dumbbells back towards your starting position until the elbow(s) is/are fully " +
            "extended\". Applied to the curl: the grip differs, not where the elbow ends."

    const val SOURCE_PUSHUP: String =
        "American Council on Exercise, Exercise Library: Push-Ups " +
            "(acefitness.org/resources/everyone/exercise-library/41/push-up/, read 2026-10-01): " +
            "\"Continue pressing until the arms are fully extended at the elbows.\""

    /** A lateral raise ends level with the shoulders: the arm at 90 degrees from the side (S1). */
    const val SHOULDER_LEVEL_DEG: Double = 90.0

    /** A fully extended elbow: a straight arm, 180 degrees (S2, S3). */
    const val FULL_EXTENSION_DEG: Double = 180.0

    fun evaluate(authority: TechniqueAuthority, profile: RepetitionExercise, rep: RepRecord): List<TechniqueFault> {
        val key = CalibrationKey.of(profile)
        require(authority.describeProfile() == key) { "${authority.describeProfile()}'s authority cannot judge ${profile.id}" }
        if (rep.validity != RepValidity.VALID) return emptyList()
        return when (key) {
            "lateral_raise" -> raisedAboveShoulderLevel(authority, rep)
            "bicep_curl" -> curlStartedElbowBent(authority, rep)
            "pushup" -> pushUpArmsNotExtended(authority, rep)
            else -> emptyList()
        }
    }

    private fun raisedAboveShoulderLevel(authority: TechniqueAuthority, rep: RepRecord): List<TechniqueFault> {
        val limit = SHOULDER_LEVEL_DEG + TOLERANCE_DEG
        return Side.entries.mapNotNull { side ->
            val far = rep.troughValue.on(side)
            if (!(far > limit)) return@mapNotNull null
            TechniqueFault.from(
                authority = authority,
                faultId = RAISED_ABOVE_SHOULDER_LEVEL,
                displayText = "${armOf(side)} raised above shoulder level: about ${degrees(far)} from your side. " +
                    "ACE's lateral raise goes up until the arms are level with the shoulders " +
                    "(${degrees(SHOULDER_LEVEL_DEG)}); Kinesis says so only past ${degrees(limit)}.",
                ruleVersion = RULE_VERSION,
                sourceRef = SOURCE_LATERAL_RAISE,
            )
        }
    }

    /** The working arm only: in an alternating curl the other arm is resting (P3-1). */
    private fun curlStartedElbowBent(authority: TechniqueAuthority, rep: RepRecord): List<TechniqueFault> {
        val sides = rep.attributedSide?.let { listOf(it) } ?: Side.entries
        val limit = FULL_EXTENSION_DEG - TOLERANCE_DEG
        return sides.mapNotNull { side ->
            val start = rep.peakValue.on(side)
            if (!(start < limit)) return@mapNotNull null
            TechniqueFault.from(
                authority = authority,
                faultId = CURL_STARTED_ELBOW_BENT,
                displayText = "${armOf(side)} not straightened before this curl: the elbow was at about " +
                    "${degrees(start)}. ACE lowers a curl until the elbow is fully extended; " +
                    "Kinesis says so only below ${degrees(limit)}.",
                ruleVersion = RULE_VERSION,
                sourceRef = SOURCE_CURL,
            )
        }
    }

    private fun pushUpArmsNotExtended(authority: TechniqueAuthority, rep: RepRecord): List<TechniqueFault> {
        val limit = FULL_EXTENSION_DEG - TOLERANCE_DEG
        return Side.entries.mapNotNull { side ->
            val top = rep.peakValue.on(side)
            if (!(top < limit)) return@mapNotNull null
            TechniqueFault.from(
                authority = authority,
                faultId = PUSHUP_ARMS_NOT_EXTENDED,
                displayText = "${armOf(side)} not fully extended at the top of this push-up: the elbow reached " +
                    "about ${degrees(top)}. ACE presses until the arms are fully extended at the elbows; " +
                    "Kinesis says so only below ${degrees(limit)}.",
                ruleVersion = RULE_VERSION,
                sourceRef = SOURCE_PUSHUP,
            )
        }
    }

    private fun armOf(side: Side): String = if (side == Side.LEFT) "Left arm" else "Right arm"

    /** Whole degrees, written from an integer so every platform prints it the same. */
    private fun degrees(value: Double): String = value.roundToInt().toString() + "°"
}
