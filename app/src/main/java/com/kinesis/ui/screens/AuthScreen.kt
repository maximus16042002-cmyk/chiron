package com.kinesis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kinesis.sync.AuthFailure
import com.kinesis.ui.theme.Attention
import com.kinesis.ui.theme.Card
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary

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
            .background(PageBackground)
            // Nothing above this one, so it insets itself: targetSdk 35 lays the
            // app out edge to edge.
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Chiron", style = MaterialTheme.typography.displayLarge, color = Ink)
        Text(
            if (creatingAccount) "Create your account" else "Sign in to your account",
            style = MaterialTheme.typography.titleMedium,
            color = InkSecondary,
        )

        Spacer(Modifier.height(28.dp))

        Field("Email", email, onChange = { email = it })
        Spacer(Modifier.height(12.dp))
        Field("Password", password, onChange = { password = it }, secret = true)

        if (failure != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                describe(failure),
                style = MaterialTheme.typography.bodyMedium,
                color = Attention,
            )
        }

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = when {
                busy -> "Working…"
                creatingAccount -> "Create account"
                else -> "Sign in"
            },
            onClick = { if (creatingAccount) onSignUp(email, password) else onSignIn(email, password) },
            enabled = !busy,
        )

        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            if (creatingAccount) "I have an account: sign in" else "No account? Create one",
            onClick = { creatingAccount = !creatingAccount },
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
            .clip(CardShape)
            .background(Card)
            .border(1.dp, Attention, CardShape)
            .padding(16.dp),
    ) {
        Text(
            "What leaves this phone",
            style = MaterialTheme.typography.titleMedium,
            color = Attention,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your completed sessions - repetition counts, ranges of motion, tempo, " +
                "hold durations and their timestamps - are stored in your account so " +
                "they survive losing this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "No video, no photographs and no camera frames are ever uploaded. Pose " +
                "estimation and all movement analysis run entirely on this device, and " +
                "nothing is sent while you are training.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Sessions are saved on this device first and always work offline. " +
                "Uploading is a background retry, never a requirement.",
            style = MaterialTheme.typography.bodySmall,
            color = InkFaint,
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
        Text(label, style = MaterialTheme.typography.labelLarge, color = InkSecondary)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            cursorBrush = SolidColor(Ink),
            visualTransformation = if (secret) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Card)
                .border(1.dp, InkFaint, RoundedCornerShape(12.dp))
                .padding(14.dp),
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
