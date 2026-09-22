package com.kinesis.core.analysis.rep

import com.kinesis.core.domain.rep.MovementPhase

/**
 * The one sanctioned way to show two sides' phases as a single phase -
 * CLAUDE.md section 3, DECISIONS.md P3-15.
 *
 * [BilateralRepTracker] runs one state machine per side and exposes both phases
 * separately. A live readout has room for one word, so something has to choose,
 * and CLAUDE.md section 3 requires that choice to be a named operation at the
 * call site rather than a silent collapse. This is that operation.
 *
 * ### The rule: furthest through a repetition
 *
 * Phases are ranked by how far through a repetition they are,
 * `IDLE < TOP < DESCENDING < BOTTOM < ASCENDING`, and the side ranked higher is
 * shown. That gives three properties, each pinned by `BilateralPhaseTest`:
 *
 * - **Idle only when both sides are idle.** An untracked or occluded side reads
 *   `IDLE`, so it can never hide a side that is being measured.
 * - **A side mid-repetition beats a side at rest.** The resting arm of an
 *   alternating curl sits at `TOP`; the working arm is what the user is doing.
 * - **Both mid-repetition: the one closer to completing.** `ASCENDING` outranks
 *   `BOTTOM` outranks `DESCENDING`, so the readout describes the repetition that
 *   will be counted next.
 *
 * Under `SIMULTANEOUS` semantics the third property has a useful consequence:
 * the tracker counts a paired repetition only when both sides have returned to
 * the top, and this rule shows `TOP` only when both have - so the phase and the
 * count change on the same frame instead of disagreeing for a frame or two.
 *
 * ### What is lost, and why that is acceptable here
 *
 * Under `PER_LIMB` semantics, when both arms are mid-repetition at once, the
 * readout shows one arm's phase and not the other's. That is a real loss of
 * information and it is why this is named rather than hidden. It is acceptable
 * only because the result is **display-only**: nothing counts, measures, stores
 * or judges from it. Repetitions, `RepRecord`s and every finding are built from
 * the two per-side machines, which this does not touch, and both per-side phases
 * stay readable on the tracker.
 *
 * The ranking is spelled out in [progress] rather than read from
 * `MovementPhase.ordinal`, so reordering the enum cannot silently change it.
 */
object BilateralPhase {

    /**
     * The phase of whichever side is further through its repetition. Symmetric:
     * swapping the arguments never changes the answer.
     */
    fun furthestThroughRepetition(left: MovementPhase, right: MovementPhase): MovementPhase =
        if (progress(right) > progress(left)) right else left

    private fun progress(phase: MovementPhase): Int = when (phase) {
        MovementPhase.IDLE -> 0
        MovementPhase.TOP -> 1
        MovementPhase.DESCENDING -> 2
        MovementPhase.BOTTOM -> 3
        MovementPhase.ASCENDING -> 4
    }
}
