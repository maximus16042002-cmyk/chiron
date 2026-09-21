package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kinesis.sync.AuthFailure
import com.kinesis.ui.theme.Amber
import com.kinesis.ui.theme.PhosphorDim
import com.kinesis.ui.theme.PhosphorFaint
import com.kinesis.ui.theme.PhosphorGreen
import com.kinesis.ui.theme.VoidRaised

/**
 * Sign in or create an account - DECISIONS.md P3-14.
 *
 * ### The disclosure is not fine print
 *
 * This project has been explicit about what leaves the device everywhere else -
 * Health Connect asks per metric and reports refusals honestly, and the offline
 * guarantees were verified rather than asserted. This is the first screen where
 * data genuinely does leave, so it says so **above** the buttons, in the same
 * type size as everything else, before the user commits rather than after.
 *
 * It also says what does *not* leave, because that is the more surprising half:
 * no video, no camera frames, no landmarks. What syncs is the numeric record of
 * a finished session.
 */
@Composable
fun AuthScreen(
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    busy: Boolean,
    failure: AuthFailure?,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creatingAccount by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("KINESIS", style = MaterialTheme.typography.displayLarge)
        Text(
            if (creatingAccount) "CREATE ACCOUNT" else "SIGN IN",
            style = MaterialTheme.typography.titleMedium,
            color = PhosphorDim,
        )

        Spacer(Modifier.height(28.dp))

        Field("EMAIL", email, onChange = { email = it })
        Spacer(Modifier.height(12.dp))
        Field("PASSWORD", password, onChange = { password = it }, secret = true)

        if (failure != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                describe(failure),
                style = MaterialTheme.typography.bodyMedium,
                color = Amber,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = when {
                busy -> "[ WORKING... ]"
                creatingAccount -> "[ CREATE ACCOUNT ]"
                else -> "[ SIGN IN ]"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (busy) PhosphorFaint else PhosphorGreen,
            modifier = Modifier.clickable(enabled = !busy) {
                if (creatingAccount) onSignUp(email, password) else onSignIn(email, password)
            },
        )

        Spacer(Modifier.height(16.dp))
        Text(
            if (creatingAccount) "have an account? sign in" else "no account? create one",
            style = MaterialTheme.typography.bodySmall,
            color = PhosphorDim,
            modifier = Modifier.clickable { creatingAccount = !creatingAccount },
        )

        Spacer(Modifier.height(32.dp))
        SyncDisclosure()
    }
}

/**
 * What leaves the device, stated plainly.
 *
 * CLAUDE.md section 16 as amended: *"MUST: disclose in the UI that session data
 * leaves the device. This project does not collect silently."*
 */
@Composable
fun SyncDisclosure(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, Amber)
            .background(VoidRaised)
            .padding(14.dp),
    ) {
        Text(
            "WHAT LEAVES THIS DEVICE",
            style = MaterialTheme.typography.labelSmall,
            color = Amber,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your completed sessions - repetition counts, ranges of motion, tempo, " +
                "hold durations and their timestamps - are stored in your account so " +
                "they survive losing this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = PhosphorDim,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "No video, no photographs and no camera frames are ever uploaded. Pose " +
                "estimation and all movement analysis run entirely on this device, and " +
                "nothing is sent while you are training.",
            style = MaterialTheme.typography.bodySmall,
            color = PhosphorDim,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Sessions are saved on this device first and always work offline. " +
                "Uploading is a background retry, never a requirement.",
            style = MaterialTheme.typography.bodySmall,
            color = PhosphorFaint,
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    secret: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = PhosphorFaint)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = PhosphorGreen),
            cursorBrush = SolidColor(PhosphorGreen),
            visualTransformation = if (secret) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PhosphorDim)
                .background(VoidRaised)
                .padding(12.dp),
        )
    }
}

/**
 * One sentence per failure. The wording says what happened without implying
 * whether an address is registered - "no account for that address" would let
 * anyone probe for users.
 */
private fun describe(failure: AuthFailure): String = when (failure) {
    AuthFailure.INVALID_CREDENTIALS -> "That email and password do not match an account."
    AuthFailure.EMAIL_ALREADY_IN_USE -> "That email already has an account. Sign in instead."
    AuthFailure.MALFORMED_EMAIL -> "That does not look like an email address."
    AuthFailure.WEAK_PASSWORD -> "That password is too short."
    AuthFailure.NETWORK_UNAVAILABLE ->
        "No network. Signing in needs one; recording a session does not."
    AuthFailure.UNKNOWN -> "Sign-in failed, and the reason was not one we recognise."
}
