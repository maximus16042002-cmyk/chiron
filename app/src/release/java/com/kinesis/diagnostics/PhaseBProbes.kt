package com.kinesis.diagnostics

import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.pose.CameraFacing

/**
 * Release builds: no Phase B probe. The readout, its Logcat output and its
 * statistics exist only in `src/debug` and are not compiled into a release APK.
 * DECISIONS.md P3-17.
 */
@Suppress("UNUSED_PARAMETER")
fun phaseBProbeOrNull(
    required: RequiredLandmarks,
    camera: CameraFacing,
    show: (String) -> Unit,
): PhaseBProbe? = null
