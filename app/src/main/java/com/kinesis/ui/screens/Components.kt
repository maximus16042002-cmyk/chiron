package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kinesis.ui.theme.Accent
import com.kinesis.ui.theme.Canvas
import com.kinesis.ui.theme.Card
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.OnAccent
import com.kinesis.ui.theme.Raised

/**
 * The building blocks of the new look - DECISIONS.md P3-48: rounded cards on a dark canvas, one
 * accent for the main action and for selection. Layout only: no component here says anything about
 * movement (CONTRACTS.md section 2).
 */

val CardShape = RoundedCornerShape(16.dp)

/** A rounded card on the canvas. */
@Composable
fun KCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Card)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
        content = content,
    )
}

/** A page's title, large, at the top of a scrolling page. */
@Composable
fun PageTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
        }
    }
}

/** A small heading above a group of cards. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = InkFaint,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

/** The page's main action: filled with the accent. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Accent else Raised)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) OnAccent else InkFaint,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A quieter action: outlined. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, InkFaint, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

/** A filter chip. Selection is carried by fill and by a dot, not by colour alone. */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Raised else Card)
            .border(1.dp, if (selected) Accent else Raised, RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Accent))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Ink else InkSecondary)
    }
}

/** A number with its label beneath, for a row of totals. */
@Composable
fun NumberBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
    }
}

/** The five pages - DECISIONS.md P3-48. */
enum class Tab(val label: String, val glyph: String) {
    HOME("Home", "⌂"),
    WORKOUTS("Workouts", "☰"),
    TRAIN("Train", "●"),
    PROGRESS("Progress", "↗"),
    PROFILE("Profile", "◉"),
}

/**
 * The bottom bar. The selected page is marked three ways - accent colour, a bar above the label
 * and bold text - so it never rests on colour alone (FEATURE_PLAN rule 7's principle).
 */
@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Raised)
            .navigationBarsPadding()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (tab in Tab.entries) {
            val selected = tab == current
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .semantics {
                        this.selected = selected
                        contentDescription = tab.label
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) Accent else Raised),
                )
                Spacer(Modifier.height(4.dp))
                Text(tab.glyph, style = MaterialTheme.typography.titleMedium, color = if (selected) Accent else InkSecondary)
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Ink else InkSecondary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A plain note: a placeholder, or what a page cannot show yet, said plainly. */
@Composable
fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
}

/** The canvas colour, for page backgrounds. */
val PageBackground = Canvas
