package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.verdict.RepOutcome
import com.kinesis.core.analysis.verdict.RepVerdicts
import com.kinesis.core.domain.rep.RepValidity
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * What DECISIONS.md P3-78's verdict does to the count on MM-Fit's labelled sets, development
 * subjects only.
 *
 * Per set: MM-Fit's labelled count, the movement counter's (every VALID record - the figure P3-27 to
 * P3-71 report), and the verdict's (what the app now shows). Per exercise: totals, mean absolute
 * error against the label, exact matches, and which verdict rejected the VALID records it did not
 * count. MM-Fit's people were asked to exercise normally and carry no fault labels, so a rejection
 * here is a cost measured on repetitions MM-Fit counted, not an error rate of the rules.
 *
 * Usage: <harness output dir, with primary/> <out.csv>
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <harness output dir> <out.csv>" }
    val protocol = File(args[0], "primary")
    val subjects = Subjects.load()
    val development = subjects.split("development").toSet()

    // MM-Fit's own count per (workout, set), from the harness's sets.csv.
    val lines = File(protocol, "sets.csv").readLines()
    val header = lines.first().split(",")
    val labelled = HashMap<Pair<String, Int>, Int>()
    val exercises = HashSet<String>()
    for (line in lines.drop(1)) {
        val row = header.zip(line.split(",")).toMap()
        if (row["status"] != "evaluated" || row.getValue("workout") !in development) continue
        labelled[row.getValue("workout") to row.getValue("mmfitSetIndex").toInt()] = row.getValue("labelledReps").toInt()
        exercises += row.getValue("profileId")
    }
    val sets = StudyInput.read(protocol, subjects, development, exercises)

    val rows = StringBuilder("subject,workout,mmfitSet,exercise,labelled,counter,verdict\n")
    class Totals {
        var sets = 0
        var labelled = 0
        var counter = 0
        var verdict = 0
        var counterError = 0
        var verdictError = 0
        var counterExact = 0
        var verdictExact = 0
        val rejected = sortedMapOf<String, Int>()
    }
    val byExercise = sortedMapOf<String, Totals>()
    for (set in sets) {
        val label = labelled.getValue(set.workout to set.mmfitSet)
        val counter = set.reps.count { it.validity == RepValidity.VALID }
        val verdicts = RepVerdicts.judgeSet(set.profile, set.reps)
        val verdict = verdicts.count { it.outcome.counts }
        rows.append("${set.subject},${set.workout},${set.mmfitSet},${set.profile.id},$label,$counter,$verdict\n")
        val t = byExercise.getOrPut(set.profile.id) { Totals() }
        t.sets++
        t.labelled += label
        t.counter += counter
        t.verdict += verdict
        t.counterError += abs(counter - label)
        t.verdictError += abs(verdict - label)
        if (counter == label) t.counterExact++
        if (verdict == label) t.verdictExact++
        set.reps.forEachIndexed { i, rep ->
            val v = verdicts[i]
            if (rep.validity != RepValidity.VALID || v.outcome.counts) return@forEachIndexed
            val why = if (v.outcome == RepOutcome.FORM_FAULT) "form:" + v.faultId else v.outcome.name
            t.rejected[why] = (t.rejected[why] ?: 0) + 1
        }
    }
    File(args[1]).writeText(rows.toString())

    println("P3-78's verdict on MM-Fit, development subjects (A, B, C, S19): counted against MM-Fit's labels")
    println(String.format(Locale.ROOT, "  %-15s %4s %8s %15s %15s %13s %13s", "exercise", "sets", "labelled", "counter (MAE)", "verdict (MAE)", "counter exact", "verdict exact"))
    for ((exercise, t) in byExercise) {
        println(
            String.format(
                Locale.ROOT, "  %-15s %4d %8d %7d (%5.2f) %7d (%5.2f) %8d of %2d %8d of %2d",
                exercise, t.sets, t.labelled,
                t.counter, t.counterError.toDouble() / t.sets,
                t.verdict, t.verdictError.toDouble() / t.sets,
                t.counterExact, t.sets, t.verdictExact, t.sets,
            ),
        )
    }
    println("\nVALID records the verdict did not count, by reason:")
    for ((exercise, t) in byExercise) {
        if (t.rejected.isEmpty()) continue
        println("  $exercise: " + t.rejected.entries.joinToString(", ") { "${it.key} ${it.value}" })
    }
}
