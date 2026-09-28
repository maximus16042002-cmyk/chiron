package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.temporal.RealTimeSignalPipeline
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.domain.rep.RepRecord
import java.io.File
import java.util.Locale

/**
 * A diagnostic: one MM-Fit set through the analysis, printing the engine's state whenever it changes
 * - frame validity, each side's raw angle and phase, the rep count - and every record as it is
 * reported. For finding why a set counted what it did; it produces no result. DECISIONS.md P3-27.
 *
 * gradlew :mmfit-eval:traceSet -Pworkout=w00 -Pset=22 [-Pshift=0]
 */
fun main(args: Array<String>) {
    val (landmarkDir, mmfitRoot, workout, setArg) = args
    val shift = args.getOrNull(4)?.toInt() ?: 0
    val label = MmFitLabels.read(File(mmfitRoot, "mm-fit/$workout/${workout}_labels.csv")).single { it.setIndex == setArg.toInt() }
    val profile = (MmFitExercises.of(label.exercise) as ExerciseMapping.Evaluated).profile
    val data = LandmarkFile.read(File(landmarkDir, "$workout.bin"))
    val window = Protocol.PRIMARY.window(label, shift)
    println("$workout set ${label.setIndex} ${label.exercise} -> ${profile.id}, window $window, labelled ${label.reps}")
    println("spec ${profile.repSignal}")
    val analysis = LiveRepetitionAnalysis(profile, onRepetition = { r: RepRecord ->
        println(String.format(Locale.ROOT, "    RECORD #%d %s side=%s %.0f..%.0f ms far L %.1f R %.1f", r.repIndex, r.validity, r.attributedSide, r.startMs, r.endMs, r.troughValue.left, r.troughValue.right))
    })
    // A copy of the engine's own per-side pipeline, fed the same raw angles, to show what the
    // repetition machine received: the gated, smoothed value, NaN where the outlier gate rejected.
    val leftCopy = RealTimeSignalPipeline(TemporalConfig.PROVISIONAL, 1024)
    val rightCopy = RealTimeSignalPipeline(TemporalConfig.PROVISIONAL, 1024)
    var last = ""
    for (frame in window) {
        val record = data.frame(label.setIndex, frame)
        val at = frameTimeMs(frame)
        if (record != null && record.posePresent) analysis.onFrame(at, FrameShape.toLandmarks(record.values, data.width, data.height)) else analysis.onNothingSeen(at)
        val fedLeft = leftCopy.push(at, analysis.rawAngleLeft)
        val fedRight = rightCopy.push(at, analysis.rawAngleRight)
        val rejected = (if (fedLeft.isNaN() && !analysis.rawAngleLeft.isNaN()) " L-REJECTED" else "") +
            (if (fedRight.isNaN() && !analysis.rawAngleRight.isNaN()) " R-REJECTED" else "")
        val state = "${analysis.frameValidity} ${analysis.leftPhase}/${analysis.rightPhase} count ${analysis.repCount}"
        if (state != last || rejected.isNotEmpty() || frame % 30 == 0) {
            println(String.format(Locale.ROOT, "  %6d  raw L %6.1f R %6.1f  fed L %6.1f R %6.1f  %s%s", frame, analysis.rawAngleLeft, analysis.rawAngleRight, fedLeft, fedRight, state, rejected))
            last = state
        }
    }
    analysis.finish(frameTimeMs(window.last))
    println("end: count ${analysis.repCount}, records ${analysis.recordsReported}")
}
