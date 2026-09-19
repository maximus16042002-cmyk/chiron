package com.kinesis.core.domain.finding

import com.kinesis.core.domain.profile.TechniqueAuthority

/**
 * Something the system has concluded and is prepared to say out loud.
 *
 * CONTRACTS.md section 2. Three things are structural here, not conventional:
 *
 *  - [level] is fixed per type. It is not a constructor parameter, so no caller
 *    can label an L3 judgement as an L1 measurement or the reverse.
 *  - [displayText] is composed here in commonMain from measured values. The UI
 *    renders what it is given and writes no sentences of its own. That is what
 *    stops product copy from quietly bypassing the tier system.
 *  - The hierarchy is sealed, so the set of claims the system can make is closed
 *    and exhaustively handled at every call site.
 */
sealed interface Finding {

    /** Fixed by the concrete type. Never supplied by a caller. */
    val level: ClaimLevel

    /** The sentence shown to the user, composed in the shared core. */
    val displayText: String

    /** Which version of the rule produced this, so results stay re-analysable. */
    val ruleVersion: String
}

/**
 * Level 1. A measured quantity, stated plainly.
 */
data class Measurement(
    /** Stable identifier for the measured quantity, e.g. `rom_deg`, `reps`. */
    val metric: String,
    override val displayText: String,
    override val ruleVersion: String,
) : Finding {
    override val level: ClaimLevel get() = ClaimLevel.L1_MEASUREMENT
}

/** Which way a measurement departed from the user's own baseline. */
enum class DeviationDirection {
    BELOW_BASELINE,
    ABOVE_BASELINE,

    /** Layer 4 anomaly detection. Personal unusualness, never correctness. */
    UNUSUAL_FOR_YOU,
}

/**
 * Level 2. A departure from this user's own baseline.
 *
 * The comparison is always against the user's calibration reps and their own
 * history - never against a population norm, and never against a notion of
 * correct form.
 */
data class Deviation(
    val metric: String,
    val direction: DeviationDirection,
    override val displayText: String,
    override val ruleVersion: String,
) : Finding {
    override val level: ClaimLevel get() = ClaimLevel.L2_DEVIATION
}

/**
 * Level 3. A technique judgement, available to Tier A profiles only.
 *
 * This type is the load-bearing half of CLAUDE.md section 8. It cannot be
 * constructed directly. The only way to obtain one is [from], which demands a
 * [TechniqueAuthority], and the only way to obtain one of those is from a
 * `TierAProfile`. A Tier B or Tier C profile has no such property, so the call
 * does not compile for it - the prohibition is a consequence of the signatures
 * rather than a rule someone has to remember, and there is no filtering step
 * downstream that could be removed by accident.
 *
 * Deliberately not a `data class`: a generated `copy()` would let a caller take
 * a valid fault and produce one with a blank [sourceRef], stepping around the
 * check in [from].
 */
class TechniqueFault private constructor(

    /** The Tier A profile whose authority produced this fault. */
    val profileId: String,

    /** Stable identifier for the fault, e.g. `knee_valgus`. */
    val faultId: String,

    override val displayText: String,

    override val ruleVersion: String,

    /**
     * Citation for the published threshold this rule implements.
     *
     * DECISIONS.md P0-6: every technique rule is traceable to a published
     * source, and a rule with no traceable source does not ship. This is
     * mandatory, checked at construction, and never blank.
     */
    val sourceRef: String,
) : Finding {

    override val level: ClaimLevel get() = ClaimLevel.L3_TECHNIQUE

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TechniqueFault) return false
        return profileId == other.profileId &&
            faultId == other.faultId &&
            displayText == other.displayText &&
            ruleVersion == other.ruleVersion &&
            sourceRef == other.sourceRef
    }

    override fun hashCode(): Int {
        var result = profileId.hashCode()
        result = 31 * result + faultId.hashCode()
        result = 31 * result + displayText.hashCode()
        result = 31 * result + ruleVersion.hashCode()
        result = 31 * result + sourceRef.hashCode()
        return result
    }

    override fun toString(): String =
        "TechniqueFault(profileId=" + profileId +
            ", faultId=" + faultId +
            ", ruleVersion=" + ruleVersion +
            ", sourceRef=" + sourceRef + ")"

    companion object {

        /**
         * The only way to build a Level 3 finding.
         *
         * @param authority proof that a Tier A profile authorised this claim.
         * @param sourceRef the published threshold being applied. Must not be
         *   blank; a rule with no traceable source does not ship.
         */
        fun from(
            authority: TechniqueAuthority,
            faultId: String,
            displayText: String,
            ruleVersion: String,
            sourceRef: String,
        ): TechniqueFault {
            require(faultId.isNotBlank()) { "a technique fault needs a stable faultId" }
            require(ruleVersion.isNotBlank()) { "a technique fault needs a ruleVersion" }
            require(sourceRef.isNotBlank()) {
                "a technique rule with no traceable source does not ship (DECISIONS.md P0-6): " +
                    "faultId=" + faultId
            }
            return TechniqueFault(
                profileId = authority.profileId,
                faultId = faultId,
                displayText = displayText,
                ruleVersion = ruleVersion,
                sourceRef = sourceRef,
            )
        }
    }
}
