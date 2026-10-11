package com.kinesis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The Kinesis look: a dark fitness style, one bright accent - DECISIONS.md P3-48, superseding P3-12's
 * phosphor-green terminal theme at the owner's direction (FEATURE_PLAN).
 *
 * The colours are [KinesisPalette]'s tokens, plain numbers so `KinesisPaletteContrastTest` can hold
 * every text colour to WCAG AA on every surface. A normal sans-serif replaces monospace, and every
 * style that shows a changing number uses tabular figures, so a count going from 9 to 10 does not
 * shift sideways - the one thing monospace was there for.
 *
 * The skeleton overlay keeps its own palette, [OverlayState], unchanged (P3-12's legibility rules).
 */
object KinesisPalette {
    /** The background. */
    const val CANVAS: Long = 0xFF0E0F12

    /** Cards. */
    const val CARD: Long = 0xFF1A1C21

    /** A card on a card; the bottom bar. */
    const val RAISED: Long = 0xFF23262D

    /** Text. */
    const val INK: Long = 0xFFF2F3F5

    /** Secondary text. */
    const val INK_SECONDARY: Long = 0xFFA0A4AD

    /** Labels and outlines: the lightest grey that still reads as text on every surface. */
    const val INK_FAINT: Long = 0xFF8F939C

    /** The one bright colour: selection, the main action, the streak. */
    const val ACCENT: Long = 0xFFFF6B35

    /** Words on the accent. */
    const val ON_ACCENT: Long = CANVAS

    /** Advice: something needs the user's eye, nothing is wrong yet - the camera view, the frame rate. Amber. */
    const val ATTENTION: Long = 0xFFFFB000

    /**
     * An attempt that did not count, through the movement's fault - DECISIONS.md P3-78. The same
     * red as the overlay's attention state, which is darker than the phosphor green so that a
     * red-green deficient eye still tells them apart.
     */
    const val FAULT: Long = 0xFFFF5A4F

    /**
     * The charts' left and right series - DECISIONS.md P3-51: the data-viz reference palette's dark
     * steps for blue and aqua, validated on [CARD]. Orange is left to [ACCENT].
     */
    const val SERIES_LEFT: Long = 0xFF3987E5
    const val SERIES_RIGHT: Long = 0xFF199E70
}

val Canvas = Color(KinesisPalette.CANVAS)
val Card = Color(KinesisPalette.CARD)
val Raised = Color(KinesisPalette.RAISED)
val Ink = Color(KinesisPalette.INK)
val InkSecondary = Color(KinesisPalette.INK_SECONDARY)
val InkFaint = Color(KinesisPalette.INK_FAINT)
val Accent = Color(KinesisPalette.ACCENT)
val OnAccent = Color(KinesisPalette.ON_ACCENT)
val Attention = Color(KinesisPalette.ATTENTION)
val Fault = Color(KinesisPalette.FAULT)
val SeriesLeft = Color(KinesisPalette.SERIES_LEFT)
val SeriesRight = Color(KinesisPalette.SERIES_RIGHT)

/** The camera area's backdrop, shared with the overlay so the two cannot drift apart. */
val Void = Color(OverlayState.BACKGROUND_ARGB.toInt())

private val KinesisColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = Raised,
    onPrimaryContainer = Ink,
    secondary = InkSecondary,
    onSecondary = Canvas,
    background = Canvas,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Raised,
    onSurfaceVariant = InkSecondary,
    outline = InkFaint,
    error = Attention,
    onError = Canvas,
)

private val Sans = FontFamily.SansSerif

/** Tabular figures: every digit the same width. */
private const val TABULAR = "tnum"

private val KinesisTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 64.sp, fontFeatureSettings = TABULAR),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, fontFeatureSettings = TABULAR),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, fontFeatureSettings = TABULAR),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFeatureSettings = TABULAR),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontFeatureSettings = TABULAR),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, fontFeatureSettings = TABULAR),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, fontFeatureSettings = TABULAR),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

@Composable
fun KinesisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KinesisColors,
        typography = KinesisTypography,
        content = content,
    )
}

/** The overlay palette, as Compose colours. */
val OverlayState.color: Color get() = Color(argb.toInt())
