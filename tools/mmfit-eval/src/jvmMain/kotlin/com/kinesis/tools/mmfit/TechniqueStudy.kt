package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.technique.TechniqueRules
import com.kinesis.core.domain.profile.TierAProfile
import com.kinesis.core.domain.rep.RepValidity
import java.io.File
import java.util.Locale

/**
 * How often Layer 2's step A rules speak on MM-Fit's development subjects - DECISIONS.md P3-43.
 *
 * Descriptive only. MM-Fit's repetitions carry no fault labels, so this is no accuracy figure and
 * no false-alarm rate: it says how often each rule would speak on repetitions assumed normal. The
 * curl is filmed face-on in MM-Fit and side-on in the app, and is labelled so.
 *
 * Writes `technique.csv`, one row per VALID repetition and fault, and prints the rates per subject.
 *
 * Usage: <harness output dir, with primary/> <study output dir>
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <harness output dir> <study output dir>" }
    val out = File(args[1]).also { it.mkdirs() }
    val subjects = Subjects.load()
    val development = subjects.split("development").toSet()
    val exercises = listOf("lateral_raise", "bicep_curl", "pushup")
    val sets = StudyInput.read(File(args[0], "primary"), subjects, development, exercises)

    val rows = StringBuilder("subject,workout,mmfitSet,exercise,repIndex,faultId,displayText\n")
    // (exercise, subject) -> [valid repetitions, repetitions with at least one fault, faults]
    val counts = sortedMapOf<String, IntArray>()
    for (set in sets) {
        val profile = set.profile as TierAProfile
        for (rep in set.reps) {
            if (rep.validity != RepValidity.VALID) continue
            val faults = TechniqueRules.evaluate(profile.techniqueAuthority, set.profile, rep)
            val c = counts.getOrPut("${set.profile.id}|${set.subject}") { IntArray(3) }
            c[0]++
            if (faults.isNotEmpty()) c[1]++
            c[2] += faults.size
            for (f in faults) rows.append("${set.subject},${set.workout},${set.mmfitSet},${set.profile.id},${rep.repIndex},${f.faultId},\"${f.displayText.replace("\"", "\"\"")}\"\n")
        }
    }
    File(out, "technique.csv").writeText(rows.toString())
    println("Layer 2, step A, on MM-Fit's development subjects: VALID repetitions with at least one message (descriptive, not accuracy)")
    for ((key, c) in counts) {
        val (exercise, subject) = key.split("|")
        val view = if (exercise == "bicep_curl") "  (MM-Fit face-on; the app is side-on)" else ""
        println(String.format(Locale.ROOT, "  %-14s %-4s %4d of %4d  %5.1f%%   messages %d%s", exercise, subject, c[1], c[0], 100.0 * c[1] / c[0], c[2], view))
    }
}
