package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.health.ActivityContextResult
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.Raised

/** The cues on a counted repetition, and the rest's length - P3-49. */
data class DuringSetState(val tone: Boolean, val vibrate: Boolean, val speak: Boolean, val restSeconds: Int)

@Composable
private fun DuringSetCard(state: DuringSetState, onChange: (DuringSetState) -> Unit) {
    KCard {
        ToggleRow("Tone on each counted repetition", state.tone) { onChange(state.copy(tone = it)) }
        ToggleRow("Vibrate on each counted repetition", state.vibrate) { onChange(state.copy(vibrate = it)) }
        ToggleRow("Say each count aloud", state.speak) { onChange(state.copy(speak = it)) }
        if (state.speak) Note("Spoken by the phone's own voice, offline. If it has none for your language, counts are not spoken.")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Rest between sets", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    (state.restSeconds / 60).toString() + ":" + (state.restSeconds % 60).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary,
                )
            }
            Stepper("−", "15 seconds shorter", enabled = state.restSeconds > 15) { onChange(state.copy(restSeconds = state.restSeconds - 15)) }
            Spacer(Modifier.width(8.dp))
            Stepper("+", "15 seconds longer", enabled = state.restSeconds < 300) { onChange(state.copy(restSeconds = state.restSeconds + 15)) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A round − or + button. */
@Composable
internal fun Stepper(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Raised)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleLarge, color = if (enabled) Ink else InkFaint)
    }
}

/**
 * Profile: the account, calibrations, data and the small print - DECISIONS.md P3-48.
 *
 * The data card says plainly what leaves the phone and what does not (CLAUDE.md section 16:
 * "disclose in the UI that session data leaves the device"): session history syncs to the
 * signed-in account (P3-14), calibrations stay on the phone (P3-22), and no video is stored or
 * sent anywhere.
 */
@Composable
fun ProfileScreen(
    email: String?,
    onSignOut: () -> Unit,
    baselines: List<Pair<String, CalibrationBaseline>>,
    context: ActivityContextResult?,
    onRequestHealthAccess: (() -> Unit)?,
    onExport: () -> Unit,
    exporting: Boolean,
    onOpenLicences: () -> Unit,
    onShowIntroduction: () -> Unit,
    /** Training reminders - P3-49. */
    reminder: ReminderState,
    onToggleReminders: (Boolean) -> Unit,
    onReminderMinute: (Int) -> Unit,
    onReminderWeekdays: (Set<Int>) -> Unit,
    /** During a set and between sets - P3-49. */
    duringSet: DuringSetState,
    onDuringSet: (DuringSetState) -> Unit,
    /** The weekly goal in active days, 1 to 7 - P3-49. */
    goalDays: Int,
    onGoalDays: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Profile", email ?: "Signed in") }

        item {
            KCard {
                Text("Account", style = MaterialTheme.typography.titleMedium, color = Ink)
                Note("Your training goes to this account. Signing out keeps what is on this phone.")
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Sign out", onClick = onSignOut)
            }
        }

        item { SectionLabel("Weekly goal") }
        item {
            KCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            goalDays.toString() + if (goalDays == 1) " day a week" else " days a week",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                        )
                        Note("A day counts when a repetition or a hold is completed, as for the streak.")
                    }
                    Stepper("−", "Fewer days", enabled = goalDays > 1) { onGoalDays(goalDays - 1) }
                    Spacer(Modifier.width(8.dp))
                    Stepper("+", "More days", enabled = goalDays < 7) { onGoalDays(goalDays + 1) }
                }
            }
        }

        item { SectionLabel("During a set") }
        item { DuringSetCard(duringSet, onDuringSet) }

        item { SectionLabel("Reminders") }
        item { ReminderCard(reminder, onToggleReminders, onReminderMinute, onReminderWeekdays) }

        item { SectionLabel("Calibrations") }
        item { BaselinesPanel(baselines) }

        item { SectionLabel("Activity from Health Connect") }
        item { KCard { HealthContextPanel(context, onRequestHealthAccess) } }

        item { SectionLabel("Your data") }
        item {
            KCard {
                Text("What stays on this phone, and what does not", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(4.dp))
                Note(
                    "Every movement is measured on this phone; nothing is analysed anywhere else, and no " +
                        "video is kept or sent. Your session history is saved here first, then copied to your " +
                        "account so a new phone can have it. Your calibrations stay on this phone.",
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(if (exporting) "Exporting…" else "Export my data", onClick = onExport, enabled = !exporting)
                Spacer(Modifier.height(4.dp))
                Note("Every session and calibration on this phone as JSON and CSV files. They leave the phone only where you send them.")
            }
        }

        item { SectionLabel("About") }
        item {
            KCard {
                Text("Chiron", style = MaterialTheme.typography.titleMedium, color = Ink)
                Note(
                    "Counts and measures your exercise from the camera, and compares it with your own " +
                        "calibration. It is not a medical device and gives no diagnosis.",
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Introduction and camera setup", onClick = onShowIntroduction)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Licences", onClick = onOpenLicences)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
