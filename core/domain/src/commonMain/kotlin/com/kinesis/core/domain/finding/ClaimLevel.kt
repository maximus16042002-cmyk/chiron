package com.kinesis.core.domain.finding

/**
 * What kind of claim a finding is entitled to make - CLAUDE.md section 8 and
 * 12a, CONTRACTS.md section 2.
 *
 * Kinesis is explainable movement analysis, not a medical diagnostic system.
 * The three levels are the honest boundary of what the evidence behind each
 * layer supports, and they are enforced in the type system rather than in UI
 * wording, because UI wording is the first thing to drift.
 */
enum class ClaimLevel {

    /**
     * Measurement. Rep count, range of motion, tempo, velocity, symmetry,
     * consistency. Backed by the kinematics themselves.
     */
    L1_MEASUREMENT,

    /**
     * Personal deviation. Below baseline, above baseline, unusual for you.
     * Backed by the user's own calibration reps and their own history. No
     * external data and no population norms are involved.
     */
    L2_DEVIATION,

    /**
     * Technique judgement. Trunk compensation, incomplete lockout, body-line
     * deviation. Backed by cited published thresholds, and available to Tier A
     * profiles only.
     *
     * DECISIONS.md P0-6 and P0-7: these rules implement thresholds from cited
     * sources, and their agreement with expert human judgement was not measured
     * in this work. No accuracy figure is ever attached to one.
     */
    L3_TECHNIQUE,
    ;

    /**
     * Whether a claim at [this] level is permitted when [this] is a ceiling.
     * Levels are ordered by how much evidence they demand, so a ceiling permits
     * everything at or below itself.
     */
    fun permits(claim: ClaimLevel): Boolean = claim.ordinal <= ordinal
}
