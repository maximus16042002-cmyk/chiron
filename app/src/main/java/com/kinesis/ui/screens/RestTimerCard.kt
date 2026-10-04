package com.kinesis.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kinesis.ui.live.RepFeedback
import com.kinesis.ui.theme.Accent
import com.kinesis.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** The rest timer's arithmetic - DECISIONS.md P3-49. Read from the clock each tick, so it never drifts. */
object RestTimer {

    fun remainingSeconds(startedAtMs: Long, nowMs: Long, lengthSeconds: Int): Int {
        val left = (startedAtMs + lengthSeconds * 1000L - nowMs) / 1000.0
        return ceil(left).toInt().coerceIn(0, lengthSeconds)
    }
}

/**
 * A rest between sets, on the set summary - DECISIONS.md P3-49. Started by the user, with 15
 * seconds more on request; it ends with a vibration and a short tone.
 */
@Composable
fun RestTimerCard(lengthSeconds: Int, autoStart: Boolean = false) {
    val view = LocalView.current
    var length by remember { mutableIntStateOf(lengthSeconds) }
    // Guided mode starts the rest for the user (P3-50); the set summary leaves it to them.
    var startedAt by remember { mutableLongStateOf(if (autoStart) System.currentTimeMillis() else 0L) }
    var running by remember { mutableStateOf(autoStart) }
    var remaining by remember { mutableIntStateOf(lengthSeconds) }

    LaunchedEffect(running, startedAt, length) {
        if (!running) return@LaunchedEffect
        while (true) {
            remaining = RestTimer.remainingSeconds(startedAt, System.currentTimeMillis(), length)
            if (remaining == 0) {
                running = false
                RepFeedback.restOver(view)
                break
            }
            delay(TICK_MS)
        }
    }

    KCard {
        Text("Rest", style = MaterialTheme.typography.titleMedium, color = Ink)
        Text(
            (remaining / 60).toString() + ":" + (remaining % 60).toString().padStart(2, '0'),
            style = MaterialTheme.typography.displayLarge,
            color = if (running) Accent else Ink,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                SecondaryButton("Stop", onClick = { running = false; remaining = length }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SecondaryButton("+15 s", onClick = { length += 15 }, modifier = Modifier.weight(1f))
            } else {
                PrimaryButton(
                    if (remaining == 0) "Rest again" else "Start rest",
                    onClick = {
                        length = lengthSeconds
                        startedAt = System.currentTimeMillis()
                        remaining = lengthSeconds
                        running = true
                    },
                )
            }
        }
    }
}

private const val TICK_MS = 200L
