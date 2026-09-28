package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.rep.BilateralRepTracker
import com.kinesis.core.analysis.rep.RepCycle
import com.kinesis.core.analysis.rep.RepFsm
import com.kinesis.core.analysis.temporal.RealTimeSignalPipeline
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.domain.rep.RepValidity
import java.io.File

/**
 * A diagnostic, not a result: what near-side-only counting loses to the way it combines the two
 * sides' cycles - DECISIONS.md P3-28, P3-29.
 *
 * Until P3-29 `BilateralRepTracker.emitNearSide` reported a cycle unless it started before the last
 * reported one ended - from either side, whatever its validity. So a short ABORTED or INSUFFICIENT_DATA
 * cycle from a flickering far side, reported first, claimed the time and the near side's VALID cycle
 * was dropped as a duplicate, leaving no record. This re-runs each side's own pipeline and state
 * machine - the engine's classes, the tracker's construction - on the engine's own raw angles, and
 * counts three ways:
 *
 *  A. the rule until P3-29, re-created;
 *  B. only an accepted VALID cycle blocks an overlapping one - the engine's rule from P3-29, so it
 *     must equal the harness's count exactly on a P3-29 run, or this is not the engine;
 *  C. only the side measured in more frames of the set counts.
 *
 * gradlew :mmfit-eval:nearSideDiagnostic
 */
fun main(args: Array<String>) {
    val (landmarkDir, mmfitRoot, harnessDir) = args
    val manifest = File(harnessDir, "primary/sets.csv").readLines()
    val header = manifest.first().split(',')
    fun col(name: String) = header.indexOf(name)
    val totals = sortedMapOf<String, IntArray>() // labelled, harness, A, B, C
    for (workout in (0..20).map { "w%02d".format(it) }) {
        val labels = MmFitLabels.read(File(mmfitRoot, "mm-fit/$workout/${workout}_labels.csv"))
        val shifts = Shifts.read(File(landmarkDir, "$workout.shifts.csv"))
        val data = LandmarkFile.read(File(landmarkDir, "$workout.bin"))
        val exported = File(harnessDir, "primary/$workout.export.json").readText()
        for (label in labels) {
            val mapping = MmFitExercises.of(label.exercise) as? ExerciseMapping.Evaluated ?: continue
            val profile = mapping.profile
            if (profile.id != "pushup" && profile.id != "situp") continue
            val row = manifest.first { it.startsWith("PRIMARY,$workout,${label.setIndex},") }.split(',')
            val exportIndex = row[col("exportSetIndex")].toInt()
            val harnessCount = validCountOf(exported, exportIndex)

            val config = TemporalConfig.PROVISIONAL
            val capacity = BilateralRepTracker.DEFAULT_BUFFER_CAPACITY
            val pipes = List(2) { RealTimeSignalPipeline(config, capacity) }
            val fsms = List(2) { RepFsm(profile.repSignal, capacity, config.resampleLength, config.maxBridgedGapMs) }
            val analysis = LiveRepetitionAnalysis(profile)
            val emitted = ArrayList<Pair<Int, RepCycle>>() // side 0 = left, 1 = right, in the tracker's order
            val measured = IntArray(2)
            val window = Protocol.PRIMARY.window(label, shifts.getValue(label.setIndex).value)
            for (frame in window) {
                val record = data.frame(label.setIndex, frame)
                val at = frameTimeMs(frame)
                if (record != null && record.posePresent) analysis.onFrame(at, FrameShape.toLandmarks(record.values, data.width, data.height)) else analysis.onNothingSeen(at)
                val raw = doubleArrayOf(analysis.rawAngleLeft, analysis.rawAngleRight)
                for (side in 0..1) {
                    if (!raw[side].isNaN()) measured[side]++
                    fsms[side].step(at, pipes[side].push(at, raw[side]))?.let { emitted += side to it }
                }
            }
            val end = frameTimeMs(window.last)
            for (side in 0..1) fsms[side].finish(end)?.let { emitted += side to it }

            var lastEnd = Double.NaN
            var a = 0
            for ((_, c) in emitted) {
                if (!lastEnd.isNaN() && c.startMs < lastEnd) continue
                lastEnd = c.endMs
                if (c.validity == RepValidity.VALID) a++
            }
            var lastValidEnd = Double.NaN
            var b = 0
            for ((_, c) in emitted) {
                if (c.validity != RepValidity.VALID) continue
                if (!lastValidEnd.isNaN() && c.startMs < lastValidEnd) continue
                lastValidEnd = c.endMs
                b++
            }
            val near = if (measured[0] >= measured[1]) 0 else 1
            val c = emitted.count { (side, cycle) -> side == near && cycle.validity == RepValidity.VALID }

            val t = totals.getOrPut(profile.id) { IntArray(5) }
            t[0] += label.reps; t[1] += harnessCount; t[2] += a; t[3] += b; t[4] += c
            if (b != harnessCount) println("MISMATCH $workout set ${label.setIndex}: engine rule re-created $b, harness $harnessCount")
        }
    }
    println("exercise  labelled  harness  A:until-P3-29  B:valid-blocks-only(engine)  C:near-side-alone")
    for ((id, t) in totals) println(String.format("%-9s %8d %8d %13d %27d %18d", id, t[0], t[1], t[2], t[3], t[4]))
}

/** The `validCount` of the set at [exportSetIndex] (1-based) in a P5 export, read without a JSON library. */
private fun validCountOf(json: String, exportSetIndex: Int): Int {
    val marker = "\"setIndex\":$exportSetIndex,"
    val at = json.indexOf(marker)
    require(at >= 0) { "no set $exportSetIndex" }
    val v = json.indexOf("\"validCount\":", at) + "\"validCount\":".length
    return json.substring(v, json.indexOf(',', v)).toInt()
}
