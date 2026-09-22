package com.kinesis.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat

/**
 * Camera permission for the live screen - DECISIONS.md P3-15.
 *
 * The same shape as `rememberHealthPermissionLauncher`: the screen asks, and a
 * refusal is an answer rather than something to retry. Only the live screen
 * needs the camera; the dashboard, history and library work without it.
 */
fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun rememberCameraPermissionLauncher(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onResult,
    )
    return remember(launcher) { { launcher.launch(Manifest.permission.CAMERA) } }
}
