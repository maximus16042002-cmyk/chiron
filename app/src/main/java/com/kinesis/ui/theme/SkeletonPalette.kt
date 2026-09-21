package com.kinesis.ui.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * How the skeleton overlay says what it knows - DECISIONS.md P3-12.
 *
 * ### The problem the theme creates
 *
 * Kinesis's visual identity is green on black. Left alone, that means every line
 * on screen is the same colour, and the one thing a user must be able to read at
 * a glance - is this joint being measured properly, or is something wrong -
 * disappears into the aesthetic. A theme that makes the product unreadable
 * during the exact moment it is meant to be useful is not a theme, it is a
 * regression.
 *
 * ### The convention
 *
 * Four states, each mapped to something the engine actually measures today.
 * There is no "correct form" state, because **nothing in the codebase produces a
 * form fault yet** - Layer 2 technique rules are P8, and inventing a red joint
 * with no rule behind it is what CLAUDE.md sections 22 and 23 exist to prevent.
 * [ATTENTION] is reachable today from repetition validity, and is the slot P8's
 * findings will occupy when they exist.
 *
 * ### Why amber, and why not red
 *
 * Red on green is the worst pairing available. Roughly eight percent of men have
 * a red-green deficiency, and for them a red joint on a green skeleton is a
 * green joint on a green skeleton. Amber keeps a nameable hue difference for
 * everyone.
 *
 * ### Why colour alone is not enough, measured rather than assumed
 *
 * Phosphor green and amber are **both bright**. Their luminance contrast ratio is
 * about **1.35:1** - far below anything WCAG would call a distinction. In
 * greyscale, or on a dim screen at arm's length, or through the motion blur of a
 * live repetition, hue is the only thing separating them and hue is the first
 * thing to go.
 *
 * So the attention state carries **three channels at once**: a different hue, a
 * fifty percent wider stroke, and the only pulse animation in the overlay. Any
 * one of them is enough to read it. The stroke width is not decoration - it is
 * the channel that survives when the colour does not, and
 * `SkeletonPaletteLegibilityTest` fails if it is ever flattened.
 *
 * What *can* be guaranteed by colour is visibility against the background, and
 * every state clears WCAG AA contrast against the near-black backdrop.
 */
enum class OverlayState(

    /** Packed `0xAARRGGBB`. Plain `Long` so the palette is testable without Compose. */
    val argb: Long,

    /** Stroke width in dp. A non-colour channel, load-bearing - see above. */
    val strokeDp: Float,

    /** Whether the segment pulses. Scarce on purpose: motion is the loudest cue. */
    val pulses: Boolean,

    /**
     * True when this state reports a measurement the user should read, false
     * when it reports the absence of one. Recessive states are held to a lower
     * contrast floor - see [UNTRACKED].
     */
    val informative: Boolean,

    /** What measured condition puts a joint in this state. */
    val meaning: String,
) {

    /**
     * Landmark visibility at or above `VisibilityPolicy.reliable`. The default,
     * and the only state most of a set should ever be in.
     */
    TRACKED(0xFF39FF14, strokeDp = 4f, pulses = false, informative = true, meaning = "tracked reliably"),

    /**
     * Visible, but between `marginal` and `reliable` - the frame is
     * `FrameValidity.DEGRADED`. Measurement continues and is honest about being
     * less certain, which is exactly what the validity model already says.
     */
    DEGRADED(0xFF208F0B, strokeDp = 3f, pulses = false, informative = true, meaning = "tracked, low confidence"),

    /**
     * Below `marginal`, so the angle came back `NaN`. Drawn, but drawn as
     * absent - the same distinction the engine keeps everywhere between "not
     * measured" and "measured as zero".
     *
     * The one state held to a lower contrast bar than the others, deliberately:
     * it has to be visible enough to show *that* the camera lost the joint, and
     * quiet enough that a half-occluded skeleton does not read as a working one.
     */
    UNTRACKED(0xFF5C6B5C, strokeDp = 2f, pulses = false, informative = false, meaning = "not visible"),

    /**
     * Something needs the user's eye. Today that is a repetition the FSM
     * abandoned - `RepValidity.ABORTED` or `INCOMPLETE`. When P8's technique
     * rules exist, a Level 3 finding lands here too.
     */
    ATTENTION(0xFFFFB000, strokeDp = 6f, pulses = true, informative = true, meaning = "needs attention");

    val red: Int get() = ((argb shr 16) and 0xFF).toInt()
    val green: Int get() = ((argb shr 8) and 0xFF).toInt()
    val blue: Int get() = (argb and 0xFF).toInt()

    companion object {

        /** The backdrop everything is drawn on. Near black, with a green cast. */
        const val BACKGROUND_ARGB: Long = 0xFF060A06

        /**
         * WCAG relative luminance. Pure arithmetic, so the legibility guard can
         * assert on it rather than on a screenshot.
         */
        fun relativeLuminance(argb: Long): Double {
            fun channel(value: Int): Double {
                val s = value / 255.0
                return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            }
            val r = channel(((argb shr 16) and 0xFF).toInt())
            val g = channel(((argb shr 8) and 0xFF).toInt())
            val b = channel((argb and 0xFF).toInt())
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        /** WCAG contrast ratio, 1.0 (identical) to 21.0 (black on white). */
        fun contrastRatio(first: Long, second: Long): Double {
            val a = relativeLuminance(first)
            val b = relativeLuminance(second)
            return (max(a, b) + 0.05) / (min(a, b) + 0.05)
        }

        /** Hue in degrees, 0..360. Used to check two states are nameably different. */
        fun hueDegrees(argb: Long): Double {
            val r = ((argb shr 16) and 0xFF).toInt() / 255.0
            val g = ((argb shr 8) and 0xFF).toInt() / 255.0
            val b = (argb and 0xFF).toInt() / 255.0
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val delta = maxC - minC
            if (delta == 0.0) return 0.0
            val hue = when (maxC) {
                r -> 60.0 * (((g - b) / delta) % 6.0)
                g -> 60.0 * (((b - r) / delta) + 2.0)
                else -> 60.0 * (((r - g) / delta) + 4.0)
            }
            return if (hue < 0) hue + 360.0 else hue
        }

        /** Smallest angle between two hues, 0..180. */
        fun hueSeparation(first: Long, second: Long): Double {
            val difference = abs(hueDegrees(first) - hueDegrees(second))
            return if (difference > 180.0) 360.0 - difference else difference
        }
    }
}
