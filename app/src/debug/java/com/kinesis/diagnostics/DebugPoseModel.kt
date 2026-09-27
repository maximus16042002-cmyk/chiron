package com.kinesis.diagnostics

import java.io.File

/** The full model's file name, as Google publishes it. Never committed or bundled. */
const val FULL_MODEL_FILE = "pose_landmarker_full.task"

/**
 * Debug builds: the full pose model, if the owner has pushed one - DECISIONS.md
 * P3-21. Returns its absolute path when `bench/pose_landmarker_full.task` is in
 * the app's external files, the same place `FramePipelineBenchmark` reads it
 * from; otherwise `null`, and the bundled lite model is used as before.
 *
 * Switched by the presence of the file, so lite and full can be compared live
 * with one install:
 *
 * ```
 * adb push pose_landmarker_full.task /sdcard/Android/data/com.kinesis/files/bench/
 * adb shell rm /sdcard/Android/data/com.kinesis/files/bench/pose_landmarker_full.task
 * ```
 *
 * The release twin of this function returns `null`: no release build can load a
 * model from outside its assets. Pure, so it is JVM-testable; the live screen
 * logs which model it opened.
 */
fun debugPoseModelOverride(externalFilesDir: File?): String? {
    val file = File(externalFilesDir ?: return null, "bench/$FULL_MODEL_FILE")
    if (!file.isFile || file.length() == 0L) return null
    return file.absolutePath
}
