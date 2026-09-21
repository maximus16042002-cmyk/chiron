package com.kinesis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kinesis.core.analysis.history.Dashboard
import com.kinesis.core.analysis.history.DashboardSummary
import com.kinesis.core.domain.landmark.FrameValidity
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.rep.MovementPhase
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.health.ActivityContextPermissions
import com.kinesis.health.ActivityContextResult
import com.kinesis.health.ActivityContextSource
import com.kinesis.health.HealthConnectActivityContextSource
import com.kinesis.health.rememberHealthPermissionLauncher
import com.kinesis.storage.KinesisDatabase
import com.kinesis.storage.RoomSessionRepository
import com.kinesis.storage.SessionRepository
import com.kinesis.ui.live.LiveUiState
import com.kinesis.ui.screens.DashboardScreen
import com.kinesis.ui.screens.ExerciseSelectionScreen
import com.kinesis.ui.screens.LiveAnalysisScreen
import com.kinesis.ui.theme.KinesisTheme
import com.kinesis.ui.theme.PhosphorFaint
import com.kinesis.ui.theme.PhosphorGreen
import java.util.TimeZone

/**
 * The app shell - DECISIONS.md P3-12.
 *
 * Three destinations, no navigation library: there are three screens and one
 * back path, so a dependency for it would be a §17 check spent on a `when`.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = RoomSessionRepository(KinesisDatabase.get(applicationContext).sessionDao())
        val contextSource = HealthConnectActivityContextSource(applicationContext)

        setContent {
            KinesisTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KinesisApp(repository, contextSource)
                }
            }
        }
    }
}

private sealed interface Destination {
    data object Dashboard : Destination
    data object Select : Destination
    data class Live(val profile: ExerciseProfile) : Destination
}

@Composable
private fun KinesisApp(
    repository: SessionRepository,
    contextSource: ActivityContextSource,
) {
    var destination: Destination by remember { mutableStateOf(Destination.Dashboard) }
    var summary: DashboardSummary? by remember { mutableStateOf(null) }
    var context: ActivityContextResult? by remember { mutableStateOf(null) }
    var reloadToken by remember { mutableStateOf(0) }

    val offsetMinutes = remember { TimeZone.getDefault().rawOffset / 60_000 }
    val today = remember(reloadToken) {
        LocalDay.of(System.currentTimeMillis().toDouble(), offsetMinutes)
    }

    // The dashboard reads from storage, not from memory - this batch's item 3.
    // Re-read whenever something could have changed it.
    LaunchedEffect(reloadToken) {
        summary = Dashboard.summarise(repository.allSessions(), today)
        context = contextSource.readDay(today, offsetMinutes)
    }

    val requestPermissions = rememberHealthPermissionLauncher { reloadToken++ }

    Column(Modifier.fillMaxSize()) {
        NavBar(
            destination = destination,
            onDashboard = { destination = Destination.Dashboard; reloadToken++ },
            onSelect = { destination = Destination.Select },
        )

        when (val current = destination) {
            Destination.Dashboard -> DashboardScreen(
                summary = summary ?: Dashboard.summarise(emptyList(), today),
                context = context,
                onRequestHealthAccess = requestPermissions?.let {
                    { it(ActivityContextPermissions.ALL) }
                },
            )

            Destination.Select -> ExerciseSelectionScreen(
                onSelect = { destination = Destination.Live(it) },
            )

            is Destination.Live -> LiveAnalysisScreen(
                // The camera arrives in Batch 9; until then the screen renders
                // the same LiveUiState the bridge will feed it, so what changes
                // is what produces the state, not the screen.
                state = LiveUiState(
                    exerciseId = current.profile.id,
                    repCount = 0,
                    phase = MovementPhase.IDLE,
                    frameValidity = FrameValidity.VALID,
                    lastRepValidity = null,
                ),
            )
        }
    }
}

@Composable
private fun NavBar(
    destination: Destination,
    onDashboard: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        NavItem("HISTORY", destination is Destination.Dashboard, onDashboard)
        NavItem("TRAIN", destination !is Destination.Dashboard, onSelect)
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = if (selected) "> $label" else "  $label",
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) PhosphorGreen else PhosphorFaint,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
