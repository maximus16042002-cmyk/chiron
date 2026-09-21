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
 * The Kinesis look: phosphor green on near-black - DECISIONS.md P3-12.
 *
 * Monospace throughout, because the screen is mostly numbers that change while
 * the user is watching them, and a proportional face makes a rep count jitter
 * sideways as it goes from 9 to 10.
 *
 * The overlay's own palette is [OverlayState], kept separate and free of Compose
 * types so its legibility can be asserted arithmetically rather than eyeballed.
 */

/** Phosphor green. The one bright thing on the screen. */
val PhosphorGreen = Color(0xFF39FF14)

/** Dimmer green for secondary text - readable, not shouting. */
val PhosphorDim = Color(0xFF208F0B)

/** Structure: dividers, inactive outlines, the grid. */
val PhosphorFaint = Color(0xFF5C6B5C)

/** The backdrop, shared with the overlay so the two cannot drift apart. */
val Void = Color(OverlayState.BACKGROUND_ARGB.toInt())

/** Raised surfaces - cards, sheets. Barely lighter than the void. */
val VoidRaised = Color(0xFF0D140D)

/** Attention. Amber rather than red - see [OverlayState] for why. */
val Amber = Color(0xFFFFB000)

private val KinesisColors = darkColorScheme(
    primary = PhosphorGreen,
    onPrimary = Void,
    primaryContainer = VoidRaised,
    onPrimaryContainer = PhosphorGreen,
    secondary = PhosphorDim,
    onSecondary = Void,
    background = Void,
    onBackground = PhosphorGreen,
    surface = VoidRaised,
    onSurface = PhosphorGreen,
    surfaceVariant = VoidRaised,
    onSurfaceVariant = PhosphorFaint,
    outline = PhosphorFaint,
    error = Amber,
    onError = Void,
)

private val Mono = FontFamily.Monospace

private val KinesisTypography = Typography(
    displayLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 56.sp),
    headlineMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Mono, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp),
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
