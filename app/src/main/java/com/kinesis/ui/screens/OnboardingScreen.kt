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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kinesis.ui.theme.Accent
import com.kinesis.ui.theme.Ink
import com.kinesis.ui.theme.InkFaint
import com.kinesis.ui.theme.InkSecondary
import com.kinesis.ui.theme.Raised

/**
 * The first launch: three short pages - DECISIONS.md P3-48. What Kinesis is and is not, what
 * stays on the phone, and how to place it. The placement advice is the pose model card's
 * out-of-scope list (P3-17): one person, the whole body in view, within about 4 m.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        Triple(
            "Movement, measured",
            "Kinesis counts your repetitions and measures range of motion, tempo and each side of your body, " +
                "from your phone's camera.",
            "It compares you with your own calibration, not with anyone else. It gives form feedback, never a " +
                "diagnosis: it is not a medical device.",
        ),
        Triple(
            "Yours, on your phone",
            "Every movement is measured on the phone itself. No video is kept or sent anywhere.",
            "Your session history is saved on the phone first, then copied to your account so a new phone can " +
                "have it. Your calibrations stay on this phone.",
        ),
        Triple(
            "Place the phone",
            "Stand it up so your whole body is in the frame, head to feet, and step back until it is. " +
                "About 2 to 4 metres usually works.",
            "One person in view, in good light. Each exercise says whether it wants you side-on or facing the phone.",
        ),
    )
    val (title, first, second) = pages[page]
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (page < pages.size - 1) {
                Text(
                    "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkSecondary,
                    modifier = Modifier.clickable(onClick = onDone).padding(8.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text(first, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(second, style = MaterialTheme.typography.bodyLarge, color = InkSecondary)
        }
        Column {
            Row(Modifier.padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in pages.indices) {
                    Box(
                        Modifier
                            .size(if (i == page) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (i == page) Accent else Raised),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${page + 1} of ${pages.size}", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            }
            PrimaryButton(
                if (page < pages.size - 1) "Next" else "Get started",
                onClick = { if (page < pages.size - 1) page++ else onDone() },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
