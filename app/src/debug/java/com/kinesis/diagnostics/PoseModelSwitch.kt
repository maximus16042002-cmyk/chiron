package com.kinesis.diagnostics

import com.kinesis.camera.PoseModelAsset

/**
 * Debug builds: the pose models the live screen may switch between -
 * DECISIONS.md P3-21. Lite first, because it is what the app runs by default
 * and what release builds carry; then full, if this build carries it.
 *
 * The owner decided on a debug-only way to run the full model live, so the lite
 * and full models can be compared on a person in the camera session. The full
 * model is not committed: it goes in `src/debug/assets`, which `.gitignore`
 * keeps out of git, and without it there is nothing to switch to. The heavy
 * model is not part of the decision and is never offered.
 *
 * The release twin of this function returns an empty list.
 */
fun switchablePoseModels(presentAssets: Set<String>): List<String> =
    listOf(PoseModelAsset.LITE, PoseModelAsset.FULL).filter { it in presentAssets }
