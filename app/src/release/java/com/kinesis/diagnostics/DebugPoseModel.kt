package com.kinesis.diagnostics

import java.io.File

/**
 * Release builds: no model override. Only the bundled model in `assets/` is ever
 * loaded. The debug twin lives in `src/debug`. DECISIONS.md P3-21.
 */
@Suppress("UNUSED_PARAMETER")
fun debugPoseModelOverride(externalFilesDir: File?): String? = null
