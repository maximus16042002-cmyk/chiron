package com.kinesis.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kinesis.ui.theme.KinesisTheme
import com.kinesis.ui.theme.PhosphorDim
import com.kinesis.ui.theme.PhosphorGreen

/**
 * Why Kinesis asks for Health Connect data - DECISIONS.md P3-19.
 *
 * Health Connect only shows its permission dialog to an app that declares where
 * it explains that use, and it opens this screen from its own settings when the
 * user asks. The manifest declares it for both routes: the rationale action
 * before Android 14, and a `VIEW_PERMISSION_USAGE` alias from Android 14.
 *
 * Every sentence is true of the code as it stands: three read permissions, read
 * for one day at a time, shown on the History screen, never written, never
 * stored, never uploaded - `HealthConnectActivityContextSource` calls
 * `aggregate` and `getGrantedPermissions` and nothing else, and no storage or
 * sync code refers to it (checked in P3-19). If that changes, this changes with it.
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KinesisTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding()
                        .padding(24.dp),
                ) {
                    Text(
                        "WHY KINESIS ASKS FOR HEALTH CONNECT",
                        style = MaterialTheme.typography.titleMedium,
                        color = PhosphorGreen,
                    )
                    Spacer(Modifier.height(16.dp))
                    for (paragraph in RATIONALE) {
                        Text(paragraph, style = MaterialTheme.typography.bodyMedium, color = PhosphorDim)
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "[ CLOSE ]",
                        style = MaterialTheme.typography.titleMedium,
                        color = PhosphorGreen,
                        modifier = Modifier.clickable { finish() },
                    )
                }
            }
        }
    }

    companion object {
        /** The explanation, as shown. Public so a test can hold it to the code. */
        val RATIONALE: List<String> = listOf(
            "Kinesis reads three things from Health Connect, for today only: your step count, the " +
                "active calories you burned, and your average heart rate.",
            "They are shown on the History screen as context beside the sessions you recorded. " +
                "Nothing in Kinesis's movement analysis uses them.",
            "Kinesis only reads. It never writes to Health Connect, it does not store these values, " +
                "and it does not upload them: they are not part of the sessions that sync to your account.",
            "You can change or withdraw this access at any time in Health Connect's settings.",
        )
    }
}
