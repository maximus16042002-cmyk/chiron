package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.export.SessionExport
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import java.io.File
import java.util.Locale

/**
 * P7's harness - DECISIONS.md P3-27.
 *
 * For every workout with a landmark file, every labelled set, and each of the three protocols fixed
 * before any result was seen: the set through the phone's analysis, and the workout written as a P5
 * export (CONTRACTS.md section 10) - `<out>/<protocol>/wXX.export.json`.
 *
 * The export format carries no set identity or window, so the harness writes its own beside it,
 * `<out>/<protocol>/sets.csv`: each MM-Fit set's label, what was done with it, the window evaluated
 * and the export set it became. The analysis joins the two on workout and `exportSetIndex`.
 *
 * MM-Fit has no wall clock. In these exports `startedAtEpochMs` is video time in milliseconds - the
 * session's is 0, a set's is its window's first frame - and records' times are video time too.
 */
fun main(args: Array<String>) {
    require(args.size in 3..4) { "usage: <landmark dir> <mm-fit root> <out dir> [wXX,wYY,... | split:<name>]" }
    val landmarkDir = File(args[0])
    val mmfitRoot = File(args[1])
    val outRoot = File(args[2])
    val workouts = if (args.size == 4 && args[3].startsWith("split:")) {
        // A split by name, from subjects.json - DECISIONS.md P3-30.
        Subjects.load().split(args[3].removePrefix("split:"))
    } else if (args.size == 4 && args[3].isNotBlank()) {
        args[3].split(',').map { it.trim() }
    } else {
        landmarkDir.listFiles { f -> f.name.matches(Regex("w\\d\\d\\.bin")) }.orEmpty().map { it.name.removeSuffix(".bin") }.sorted()
    }
    require(workouts.isNotEmpty()) { "no landmark files in $landmarkDir" }

    val manifests = Protocol.entries.associateWith { StringBuilder(MANIFEST_HEADER) }
    for (workout in workouts) {
        val labels = MmFitLabels.read(File(mmfitRoot, "mm-fit/$workout/${workout}_labels.csv"))
        val shifts = Shifts.read(File(landmarkDir, "$workout.shifts.csv"))
        val data = LandmarkFile.read(File(landmarkDir, "$workout.bin"))
        check(data.width == 1280 && data.height == 720) { "$workout: unexpected frame size ${data.width}x${data.height}" }
        for (protocol in Protocol.entries) {
            val sets = ArrayList<RepetitionSetRecord>()
            for (label in labels) {
                val shift = shifts[label.setIndex] ?: error("$workout: no shift for set ${label.setIndex}")
                val row = StringBuilder()
                row.append(protocol.name).append(',').append(workout).append(',').append(label.setIndex).append(',')
                    .append(label.exercise).append(',').append(label.start).append(',').append(label.end).append(',')
                    .append(label.reps).append(',').append(shift.value).append(',').append(shift.source).append(',')
                when (val result = Harness.runSet(data, label, shift.value, protocol)) {
                    // status, then an empty cell for every column an excluded set has no value for, then the reason.
                    is SetResult.Excluded ->
                        row.append("excluded,").append(",".repeat(EVALUATED_ONLY_COLUMNS)).append('"')
                            .append(result.reason.replace("\"", "\"\"")).append('"')
                    is SetResult.Evaluated -> {
                        sets += result.set
                        val profile = (MmFitExercises.of(label.exercise) as ExerciseMapping.Evaluated).profile
                        val spec = profile.repSignal
                        row.append("evaluated,").append(result.set.exerciseId).append(',').append(sets.size).append(',')
                            .append(result.window.first).append(',').append(result.window.last).append(',')
                            .append(result.framesFed).append(',').append(result.framesWithPose).append(',')
                            .append(result.framesMissing).append(',')
                        for (v in listOf(
                            result.left.measuredFraction, result.right.measuredFraction,
                            result.left.p5, result.left.p95, result.right.p5, result.right.p95,
                            spec.topThreshold, spec.bottomThreshold, spec.hysteresis,
                        )) {
                            row.append(if (v.isNaN()) "" else String.format(Locale.ROOT, "%.3f", v)).append(',')
                        }
                        row.append(result.left.thresholdCycles).append(',').append(result.right.thresholdCycles).append(',')
                            .append(profile.repetitionSemantics.name).append(',').append(profile.analysedSides.name).append(',')
                    }
                }
                manifests.getValue(protocol).append(row).append('\n')
            }
            val session = SessionRecord("mmfit-$workout", 0.0, 0, sets)
            val out = File(outRoot, protocol.name.lowercase()).also { it.mkdirs() }
            File(out, "$workout.export.json").writeText(SessionExport.json(listOf(session), emptyList(), 0.0))
        }
        println("$workout: ${labels.size} sets")
    }
    for ((protocol, manifest) in manifests) {
        File(File(outRoot, protocol.name.lowercase()), "sets.csv").writeText(manifest.toString())
    }
}

private const val MANIFEST_HEADER =
    "protocol,workout,mmfitSetIndex,exercise,labelStart,labelEnd,labelledReps,shift,shiftSource," +
        "status,profileId,exportSetIndex,windowFirst,windowLast,framesFed,framesWithPose,framesMissing," +
        "leftMeasured,rightMeasured,leftP5,leftP95,rightP5,rightP95,top,bottom,hysteresis," +
        "leftThresholdCycles,rightThresholdCycles,semantics,analysedSides,reason\n"

/** profileId through analysedSides: the columns only an evaluated set fills. */
private const val EVALUATED_ONLY_COLUMNS = 20

/** alignment_by_set.py's `wXX.shifts.csv`: one shift per labelled set, measured or interpolated. */
object Shifts {
    class Shift(val value: Int, val source: String)

    fun read(file: File): Map<Int, Shift> {
        val lines = file.readLines().filter { it.isNotBlank() }
        require(lines.first().startsWith("setIndex,labelStart,shift,source")) { "${file.name}: unexpected header" }
        return lines.drop(1).associate { line ->
            val f = line.split(',')
            f[0].toInt() to Shift(f[2].toInt(), f[3])
        }
    }
}
