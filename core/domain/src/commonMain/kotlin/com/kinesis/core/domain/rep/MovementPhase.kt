package com.kinesis.core.domain.rep

/**
 * Where in a repetition the movement currently is.
 *
 * A discrete output, so it must match exactly across platforms - CONTRACTS.md
 * section 6. The names are direction-neutral in intent: [DESCENDING] means
 * moving away from the starting position, whichever way that is in the image.
 * A push-up and a bicep curl both "descend" even though one goes down and the
 * other goes up.
 */
enum class MovementPhase {

    /** No repetition in progress, and the signal is not at a recognised end. */
    IDLE,

    /** At the starting position, between repetitions. */
    TOP,

    /** Moving from the starting position toward the far position. */
    DESCENDING,

    /** At the far position. */
    BOTTOM,

    /** Returning from the far position toward the start. */
    ASCENDING,
}

/**
 * Whether a completed movement counts as a repetition, and if not, why.
 *
 * The distinction between these is the difference between "you did nine" and
 * "you did ten but one was cut short" and "the camera lost you", and a user can
 * act on each of them differently. Collapsing them into a single count would
 * discard the only information that explains the number.
 */
enum class RepValidity {

    /** A complete repetition through both phases, within the expected duration. */
    VALID,

    /** The movement turned back before reaching the far position. */
    ABORTED,

    /** Still in progress when the set ended. */
    INCOMPLETE,

    /**
     * Tracking was lost partway through. The movement may well have been
     * performed correctly; the system simply did not see it, and says so rather
     * than guessing either way.
     */
    INSUFFICIENT_DATA,
}

/** Whether a hold counts, and if not, why. See [RepValidity] for the reasoning. */
enum class HoldValidity {

    /** Held past the minimum duration. Breaks, if any, are counted separately. */
    VALID,

    /** The position was lost and not recovered. */
    BROKEN,

    /** Never held long enough to count, including never established at all. */
    TOO_SHORT,

    /** Too little of the attempt was visible to report a duration honestly. */
    INSUFFICIENT_DATA,
}
