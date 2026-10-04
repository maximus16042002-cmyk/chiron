package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.analysis.verifier.MovementContextTracker
import com.kinesis.core.domain.rep.MovementContext
import com.kinesis.core.domain.rep.RepRecord
import java.io.File
import java.util.Locale

/**
 * P9 Part B: MovementContext feature extraction for the Exercise Verifier - DECISIONS.md P3-52.
 *
 * Runs MM-Fit development workouts (subjects A, B, C, S19 = w00..w11, w14, w15, w19) through candidate
 * repetition profiles and extracts the 145-dimensional MovementContext for each repetition.
 *
 * Each repetition is labelled with its true MM-Fit exercise class (one of 10 classes).
 *
 * Usage: <landmark dir> <mm-fit root> <out dir>
 */
fun main(args: Array<String>) {
    val landmarkDir = File(if (args.isNotEmpty()) args[0] else "data/mm-fit-mediapipe")
    val mmfitRoot = File(if (args.size > 1) args[1] else "data/mm-fit")
    val outDir = File(if (args.size > 2) args[2] else "data/verifier").also { it.mkdirs() }

    val subjects = Subjects.load()
    val developmentWorkouts = subjects.split("development")
    println("Extracting MovementContext for ${developmentWorkouts.size} development workouts: $developmentWorkouts")

    val candidateProfiles = listOf(
        ExerciseLibrary.squat,
        ExerciseLibrary.pushUp,
        ExerciseLibrary.shoulderPress,
        ExerciseLibrary.lunge,
        ExerciseLibrary.row,
        ExerciseLibrary.situp,
        ExerciseLibrary.bicepCurl,
        ExerciseLibrary.lateralRaise,
    )

    fun canonicalClass(mmfitName: String): String = when (mmfitName) {
        "squats" -> "squat"
        "pushups" -> "pushup"
        "dumbbell_shoulder_press" -> "shoulder_press"
        "lunges" -> "lunge"
        "dumbbell_rows" -> "row"
        "situps" -> "situp"
        "bicep_curls" -> "bicep_curl"
        "lateral_shoulder_raises" -> "lateral_raise"
        "tricep_extensions" -> "tricep_extension"
        "jumping_jacks" -> "jumping_jacks"
        else -> mmfitName
    }

    val header = StringBuilder(
        "subject,workout,setIndex,candidateProfile,trueExercise,extractionMethod,repIndex,validity,startMs,endMs,durationMs"
    )
    for (i in 0 until MovementContext.FEATURE_COUNT) {
        header.append(",f").append(i)
    }
    header.append('\n')

    val csvOut = File(outDir, "movement_contexts_dev.csv")
    csvOut.bufferedWriter().use { writer ->
        writer.write(header.toString())

        var totalSamples = 0
        val countsByClass = mutableMapOf<String, Int>()

        for (workout in developmentWorkouts) {
            val binFile = File(landmarkDir, "$workout.bin")
            if (!binFile.exists()) {
                println("WARNING: $binFile does not exist, skipping.")
                continue
            }
            val labels = MmFitLabels.read(File(mmfitRoot, "mm-fit/$workout/${workout}_labels.csv"))
            val shifts = Shifts.read(File(landmarkDir, "$workout.shifts.csv"))
            val data = LandmarkFile.read(binFile)
            val subject = subjects.subjectOf(workout)

            println("Processing workout $workout (Subject $subject, ${labels.size} sets)...")

            for (label in labels) {
                val shift = shifts[label.setIndex] ?: error("$workout: no shift for set ${label.setIndex}")
                val window = Protocol.PRIMARY.window(label, shift.value)
                val trueClass = canonicalClass(label.exercise)

                // 1. Run all candidate profiles
                for (profile in candidateProfiles) {
                    val reps = ArrayList<RepRecord>()
                    val analysis = LiveRepetitionAnalysis(profile, temporalConfig = TemporalConfig.PROVISIONAL, onRepetition = { reps += it })
                    for (frame in window) {
                        val record = data.frame(label.setIndex, frame)
                        val at = frameTimeMs(frame)
                        when {
                            record == null -> analysis.onNothingSeen(at)
                            record.posePresent -> analysis.onFrame(at, FrameShape.toLandmarks(record.values, data.width, data.height))
                            else -> analysis.onNothingSeen(at)
                        }
                    }
                    analysis.finish(frameTimeMs(window.last))

                    for (rep in reps) {
                        val ctx = rep.movementContext ?: continue
                        writeSample(writer, subject, workout, label.setIndex, profile.id, trueClass, "profile_tracker", rep.repIndex, rep.validity.name, rep.startMs, rep.endMs, ctx)
                        totalSamples++
                        countsByClass[trueClass] = (countsByClass[trueClass] ?: 0) + 1
                    }
                }

                // 2. Also extract direct labelled slices using MovementContextTracker
                // This guarantees ground-truth repetition samples for exercises without a profile (tricep_extensions, jumping_jacks)
                // as well as clean ground-truth repetitions for all exercises.
                if (label.reps > 0 && window.count() > 0) {
                    val tracker = MovementContextTracker(window.count() + 20)
                    for (frame in window) {
                        val record = data.frame(label.setIndex, frame)
                        val at = frameTimeMs(frame)
                        if (record != null && record.posePresent) {
                            tracker.push(at, FrameShape.toLandmarks(record.values, data.width, data.height), com.kinesis.core.domain.landmark.VisibilityPolicy.PROVISIONAL)
                        }
                    }
                    val setStartMs = frameTimeMs(window.first)
                    val setEndMs = frameTimeMs(window.last)
                    val sliceDuration = (setEndMs - setStartMs) / label.reps
                    for (r in 0 until label.reps) {
                        val sliceStart = setStartMs + r * sliceDuration
                        val sliceEnd = sliceStart + sliceDuration
                        val ctx = tracker.extract(sliceStart, sliceEnd)
                        if (ctx != null) {
                            writeSample(writer, subject, workout, label.setIndex, "direct_slice", trueClass, "label_slice", r + 1, "VALID", sliceStart, sliceEnd, ctx)
                            totalSamples++
                            countsByClass[trueClass] = (countsByClass[trueClass] ?: 0) + 1
                        }
                    }
                }
            }
        }

        println("Extraction complete! Total samples: $totalSamples")
        println("Samples per true class:")
        countsByClass.toSortedMap().forEach { (cls, count) ->
            println("  $cls: $count")
        }
    }
}

private fun writeSample(
    writer: java.io.BufferedWriter,
    subject: String,
    workout: String,
    setIndex: Int,
    candidateProfile: String,
    trueExercise: String,
    extractionMethod: String,
    repIndex: Int,
    validity: String,
    startMs: Double,
    endMs: Double,
    ctx: MovementContext,
) {
    writer.write("$subject,$workout,$setIndex,$candidateProfile,$trueExercise,$extractionMethod,$repIndex,$validity,")
    writer.write(String.format(Locale.ROOT, "%.1f,%.1f,%.1f", startMs, endMs, ctx.durationMs))
    for (f in ctx.features) {
        writer.write(String.format(Locale.ROOT, ",%.6f", f))
    }
    writer.write("\n")
}
