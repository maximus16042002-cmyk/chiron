package com.kinesis.ui.theme

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent guard - DECISIONS.md P3-12.
 *
 * The Matrix theme is an explicit product requirement, and it carries an
 * explicit risk: if everything on screen is the same neon green, the user can no
 * longer tell at a glance whether a joint is being measured properly or needs
 * their attention. That is not a cosmetic regression - it is the overlay
 * ceasing to communicate during the one moment it exists to communicate.
 *
 * These assertions are what stop a future palette change from quietly doing
 * that. Every one of them maps to a specific way the distinction fails in the
 * real world:
 *
 * - **a state invisible against the backdrop** - fails on a dim screen;
 * - **two states sharing a colour** - fails always, silently;
 * - **attention differing from tracked only in hue** - fails for red-green
 *   colour deficiency, in greyscale, and through motion blur;
 * - **tracked and degraded differing only in hue** - they share a hue by design,
 *   so luminance is the only channel they have.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class SkeletonPaletteLegibilityTest {

    private val background = OverlayState.BACKGROUND_ARGB

    // --- Visible at all ----------------------------------------------------------------

    /**
     * WCAG AA for non-text content is 3:1. The states that report a measurement
     * are held to 4.5:1, because a skeleton line is thinner than text and is read
     * while the user is moving.
     *
     * **This caught a real fault in the first palette written for this batch**:
     * the degraded green came in at 3.20:1 and would have been close to
     * invisible on a dim screen. The colour was changed, not the threshold.
     */
    @Test
    fun every_informative_state_is_clearly_visible_against_the_backdrop() {
        for (state in OverlayState.entries.filter { it.informative }) {
            val ratio = OverlayState.contrastRatio(state.argb, background)
            assertTrue(
                state.name + " has only " + ratio + ":1 contrast against the backdrop, " +
                    "which will not read on a dim screen",
                ratio >= 4.5,
            )
        }
    }

    /**
     * The recessive state is held to the 3:1 non-text floor rather than 4.5:1,
     * and that is a different requirement rather than a relaxed one. An
     * untracked joint has to show *that* the camera lost it while staying quiet
     * enough that a half-occluded skeleton does not read as a working one - so
     * it is bounded on both sides, here and in
     * [an_untracked_joint_recedes_rather_than_competing_with_a_tracked_one].
     */
    @Test
    fun the_recessive_state_is_still_visible_enough_to_report_an_absence() {
        for (state in OverlayState.entries.filter { !it.informative }) {
            val ratio = OverlayState.contrastRatio(state.argb, background)
            assertTrue(
                state.name + " has only " + ratio + ":1 against the backdrop, so a lost " +
                    "joint simply disappears rather than showing as lost",
                ratio >= 3.0,
            )
        }
    }

    // --- Distinguishable from each other -------------------------------------------------

    @Test
    fun no_two_overlay_states_share_a_colour() {
        val seen = HashSet<Long>()
        for (state in OverlayState.entries) {
            assertTrue(
                state.name + " reuses a colour another state already has",
                seen.add(state.argb),
            )
        }
    }

    /**
     * The central guard. The attention state must differ from the nominal one on
     * **more than hue**, because hue is the channel that fails first - for the
     * roughly eight percent of men with a red-green deficiency, in greyscale, and
     * through the motion blur of a live repetition.
     *
     * Phosphor green and amber have a luminance contrast of only about 1.35:1,
     * which is why the stroke width is doing real work here rather than
     * decorating. If someone ever flattens the widths to one value "for
     * consistency", this fails.
     */
    @Test
    fun the_attention_state_differs_from_the_tracked_state_on_more_than_colour() {
        val tracked = OverlayState.TRACKED
        val attention = OverlayState.ATTENTION

        val hue = OverlayState.hueSeparation(tracked.argb, attention.argb)
        assertTrue(
            "attention is only " + hue + " degrees from tracked, which is not a " +
                "nameably different colour",
            hue >= 60.0,
        )

        assertTrue(
            "attention is not drawn any thicker than tracked, so the distinction " +
                "rests entirely on hue - which fails for colour-deficient users and " +
                "in greyscale",
            attention.strokeDp > tracked.strokeDp * 1.25,
        )

        assertTrue(
            "attention does not pulse, so it has no motion channel either",
            attention.pulses,
        )
    }

    /**
     * DECISIONS.md P3-78: the owner asked for red, which P3-12 had ruled out for red-green
     * deficient users. It is allowed only because this red is much darker than the tracked green:
     * the hue goes for those users, the lightness difference stays. Amber had 1.35:1.
     */
    @Test
    fun the_red_attention_state_differs_from_tracked_green_in_lightness_too() {
        val ratio = OverlayState.contrastRatio(OverlayState.TRACKED.argb, OverlayState.ATTENTION.argb)
        assertTrue(
            "attention is only " + ratio + ":1 from tracked in lightness: a red-green deficient " +
                "user cannot tell them apart by colour",
            ratio >= 2.0,
        )
        assertEquals_(OverlayState.ATTENTION.argb.toInt(), KinesisPalette.FAULT.toInt())
    }

    /**
     * And the scarcity that makes the pulse mean anything. If several states
     * animate, motion stops being a signal and becomes noise.
     */
    @Test
    fun exactly_one_state_pulses_so_motion_stays_meaningful() {
        assertEquals_(1, OverlayState.entries.count { it.pulses })
        assertTrue(OverlayState.ATTENTION.pulses)
    }

    /**
     * Tracked and degraded deliberately share a hue - they are the same signal at
     * different confidence, and giving them different colours would imply a
     * difference in kind rather than degree. That makes luminance their only
     * channel, so it has to be a real difference.
     */
    @Test
    fun tracked_and_degraded_are_separated_by_luminance_because_they_share_a_hue() {
        val hue = OverlayState.hueSeparation(
            OverlayState.TRACKED.argb,
            OverlayState.DEGRADED.argb,
        )
        assertTrue("these are meant to be the same hue, got " + hue + " degrees apart", hue < 30.0)

        val ratio = OverlayState.contrastRatio(
            OverlayState.TRACKED.argb,
            OverlayState.DEGRADED.argb,
        )
        assertTrue(
            "tracked and degraded share a hue and differ by only " + ratio + ":1 in " +
                "luminance, so nothing separates them",
            ratio >= 3.0,
        )
    }

    /**
     * An untracked joint is drawn, because knowing the camera lost your elbow is
     * information. But it must recede - if "not visible" is as loud as "tracked",
     * a half-occluded skeleton reads as a working one.
     */
    @Test
    fun an_untracked_joint_recedes_rather_than_competing_with_a_tracked_one() {
        val tracked = OverlayState.relativeLuminance(OverlayState.TRACKED.argb)
        val untracked = OverlayState.relativeLuminance(OverlayState.UNTRACKED.argb)
        assertTrue(
            "an untracked joint is as bright as a tracked one",
            untracked < tracked / 2.0,
        )
        assertTrue(
            "an untracked joint is not drawn more thinly than a tracked one",
            OverlayState.UNTRACKED.strokeDp < OverlayState.TRACKED.strokeDp,
        )
    }

    // --- Every state means something measurable --------------------------------------------

    /**
     * No state exists without a measured condition behind it. This is the
     * type-level version of "there is no fault colour driven by a stub" - P8's
     * rules will occupy ATTENTION when they arrive, and until then it is reached
     * from repetition validity, which is real.
     */
    @Test
    fun every_state_documents_the_measured_condition_that_produces_it() {
        for (state in OverlayState.entries) {
            assertTrue(
                state.name + " has no stated meaning",
                state.meaning.isNotBlank(),
            )
            assertNotEquals(state.name, state.meaning)
        }
    }

    private fun assertEquals_(expected: Int, actual: Int) {
        assertTrue("expected " + expected + " but was " + actual, expected == actual)
    }
}
