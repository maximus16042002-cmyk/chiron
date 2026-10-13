package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.deviation.PersonalStartZone
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.verdict.RepVerdicts
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.rep.RepValidity
import java.io.File
import java.util.Locale

/**
 * DECISIONS.md P3-82: the push-up's personal start zone, end to end, by the protocol fixed there
 * before any result. Arm A counts every push-up set with the profile as it is; arm B calibrates on
 * each workout's first push-up set (counted with the floor zone) and counts the later sets with the
 * personal zone. Both are compared on the sets after the first.
 *
 * Usage: <landmark dir> <mm-fit root> <split name> <out.csv>
 */
fun main(args: Array<String>) {
    require(args.size == 4) { "usage: <landmark dir> <mm-fit root> <split> <out.csv>" }
    val landmarkDir = File(args[0])
    val mmfitRoot = File(args[1])
    val subjects = Subjects.load()
    val workouts = subjects.split(args[2])
    val rows = StringBuilder("subject,workout,mmfitSet,labelled,armA,armB,armBZone,armAVerdict,armBVerdict\n")
    data class Tot(var labelled: Int = 0, var a: Int = 0, var b: Int = 0, var overA: Int = 0, var overB: Int = 0, var sets: Int = 0)
    val bySubject = sortedMapOf<String, Tot>()
    var calibrated = 0
    var workoutsWithPushups = 0
    for (workout in workouts) {
        val labels = MmFitLabels.read(File(mmfitRoot, "mm-fit/$workout/${workout}_labels.csv")).filter { it.exercise == "pushups" }
        if (labels.isEmpty()) continue
        workoutsWithPushups++
        val shifts = Shifts.read(File(landmarkDir, "$workout.shifts.csv"))
        val data = LandmarkFile.read(File(landmarkDir, "$workout.bin"))
        val base = (MmFitExercises.of("pushups") as ExerciseMapping.Evaluated).profile
        // Arm B's calibration set: the first push-up set, counted with the floor zone.
        val first = labels.first()
        val calibrationSet = Harness.runSet(data, first, shifts.getValue(first.setIndex).value, Protocol.PRIMARY, PersonalStartZone.forCalibration(base)) as SetResult.Evaluated
        val outcome = Calibration.fromRepetitions(base, calibrationSet.set.reps, 0.0, null, StudyPolicy.P3_22)
        val baseline = (outcome as? CalibrationOutcome.Calibrated)?.baseline as? RepetitionBaseline
        if (baseline != null) calibrated++
        val zone = PersonalStartZone.of(base, baseline)
        val armBProfile: RepetitionExercise = if (baseline != null) PersonalStartZone.profileWith(base, zone) else base
        val subject = subjects.subjectOf(workout)
        for (label in labels.drop(1)) {
            val shift = shifts.getValue(label.setIndex).value
            val a = Harness.runSet(data, label, shift, Protocol.PRIMARY) as SetResult.Evaluated
            val b = Harness.runSet(data, label, shift, Protocol.PRIMARY, armBProfile) as SetResult.Evaluated
            val aCount = a.set.reps.count { it.validity == RepValidity.VALID }
            val bCount = b.set.reps.count { it.validity == RepValidity.VALID }
            val aVerdict = RepVerdicts.counted(base, a.set.reps)
            val bVerdict = RepVerdicts.counted(base, b.set.reps)
            rows.append("$subject,$workout,${label.setIndex},${label.reps},$aCount,$bCount,")
                .append(if (baseline != null) String.format(Locale.ROOT, "%s %.1f", zone.source, zone.edge) else "uncalibrated")
                .append(",$aVerdict,$bVerdict\n")
            val t = bySubject.getOrPut(subject) { Tot() }
            t.sets++
            t.labelled += label.reps
            t.a += aCount
            t.b += bCount
            if (aCount > label.reps) t.overA++
            if (bCount > label.reps) t.overB++
        }
        println(String.format(Locale.ROOT, "%s (%s): calibration %s, zone %s %.1f", workout, subject, if (baseline != null) "ok" else "failed", zone.source, zone.edge))
    }
    File(args[3]).writeText(rows.toString())
    println("\nPush-ups, sets after each workout's first (split ${args[2]}): workouts $workoutsWithPushups, calibrated $calibrated")
    println(String.format(Locale.ROOT, "  %-8s %4s %8s %10s %10s %7s %7s", "subject", "sets", "labelled", "arm A", "arm B", "overA", "overB"))
    val all = Tot()
    for ((s, t) in bySubject) {
        println(String.format(Locale.ROOT, "  %-8s %4d %8d %10d %10d %7d %7d", s, t.sets, t.labelled, t.a, t.b, t.overA, t.overB))
        all.sets += t.sets; all.labelled += t.labelled; all.a += t.a; all.b += t.b; all.overA += t.overA; all.overB += t.overB
    }
    println(String.format(Locale.ROOT, "  %-8s %4d %8d %10d %10d %7d %7d", "TOTAL", all.sets, all.labelled, all.a, all.b, all.overA, all.overB))
    val pass1 = all.overB == 0
    val pass2 = all.b > all.a
    val pass3 = bySubject.values.all { it.b >= it.a }
    println("\nPass rule: (1) no set over its label in arm B: $pass1; (2) arm B total higher: $pass2; (3) no subject lower: $pass3")
    println("RESULT: " + if (pass1 && pass2 && pass3) "PASS" else "FAIL")
}
