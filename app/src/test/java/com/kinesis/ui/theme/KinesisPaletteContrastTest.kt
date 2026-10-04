package com.kinesis.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's colours are legible - DECISIONS.md P3-48, as `SkeletonPaletteLegibilityTest` holds the
 * overlay to. Asserted on the numbers, not eyeballed: if a value fails, the value changes, not this.
 */
class KinesisPaletteContrastTest {

    private val surfaces = mapOf(
        "canvas" to KinesisPalette.CANVAS,
        "card" to KinesisPalette.CARD,
        "raised" to KinesisPalette.RAISED,
    )

    private fun ratio(a: Long, b: Long) = OverlayState.contrastRatio(a, b)

    /** WCAG AA for text: 4.5:1, for every text colour on every surface it is drawn on. */
    @Test
    fun every_text_colour_reads_on_every_surface() {
        val texts = mapOf(
            "ink" to KinesisPalette.INK,
            "ink secondary" to KinesisPalette.INK_SECONDARY,
            "ink faint" to KinesisPalette.INK_FAINT,
            "attention" to KinesisPalette.ATTENTION,
        )
        for ((surfaceName, surface) in surfaces) for ((textName, text) in texts) {
            val r = ratio(text, surface)
            assertTrue("$textName on $surfaceName is $r:1, under 4.5:1", r >= 4.5)
        }
    }

    /** The accent marks selection and the main action: at least 3:1 as a non-text mark, everywhere. */
    @Test
    fun the_accent_stands_out_on_every_surface() {
        for ((surfaceName, surface) in surfaces) {
            val r = ratio(KinesisPalette.ACCENT, surface)
            assertTrue("accent on $surfaceName is $r:1, under 3:1", r >= 3.0)
        }
    }

    /**
     * The charts' two series, left and right (P3-51), are marks: at least 3:1 on the card they are
     * drawn on. Their separation under colour-vision deficiency was checked with the data-viz
     * validator when they were chosen (CVD ΔE 19.6, normal-vision ΔE 20.9) and is recorded there.
     */
    @Test
    fun the_chart_series_stand_out_on_the_card() {
        for ((name, series) in listOf("left" to KinesisPalette.SERIES_LEFT, "right" to KinesisPalette.SERIES_RIGHT)) {
            val r = ratio(series, KinesisPalette.CARD)
            assertTrue("the $name series on the card is $r:1, under 3:1", r >= 3.0)
        }
        assertTrue("the two series must differ from the accent", KinesisPalette.ACCENT !in setOf(KinesisPalette.SERIES_LEFT, KinesisPalette.SERIES_RIGHT))
    }

    /** The main button's words, canvas on accent, read as text. */
    @Test
    fun text_on_the_accent_button_reads() {
        val r = ratio(KinesisPalette.ON_ACCENT, KinesisPalette.ACCENT)
        assertTrue("text on the accent is $r:1, under 4.5:1", r >= 4.5)
    }

    /** The three greys keep their order, so secondary never reads louder than primary. */
    @Test
    fun the_greys_step_down_from_ink() {
        val ink = OverlayState.relativeLuminance(KinesisPalette.INK)
        val secondary = OverlayState.relativeLuminance(KinesisPalette.INK_SECONDARY)
        val faint = OverlayState.relativeLuminance(KinesisPalette.INK_FAINT)
        assertTrue(ink > secondary && secondary > faint)
    }
}
