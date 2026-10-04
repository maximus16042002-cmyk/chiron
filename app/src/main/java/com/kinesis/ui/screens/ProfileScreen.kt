package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.health.ActivityContextResult
import com.kinesis.ui.theme.Ink

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

        item { SectionLabel("Calibrations") }
        item { KCard { BaselinesPanel(baselines) } }

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
                Text("Kinesis", style = MaterialTheme.typography.titleMedium, color = Ink)
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
