package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.calibration.PostRepAnalysis
import com.kinesis.core.analysis.export.SessionExport
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.verdict.RepVerdicts
import com.kinesis.core.domain.finding.TechniqueFault
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.RepetitionSetRecord
import java.io.File
import java.util.Locale

/**
 * The owner's real repetitions, read back through the shared core - DECISIONS.md P3-78.
 *
 * Reads the export `owner_db_export.py` writes from a copy of the phone's database, runs every
 * repetition through [PostRepAnalysis] - the app's own post-rep path - and writes one row per
 * repetition: the exercise, its validity, its duration, each side's peak and trough, and the cited
 * technique rules that fire on it, and its P3-78 verdict. Descriptive only: the owner's repetitions
 * carry no labels, so nothing here is an accuracy figure.
 *
 * Usage: <owner.export.json> <out.csv>
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <owner.export.json> <out.csv>" }
    val sessions = SessionExport.readSessions(File(args[0]).readText())
    val byId = ExerciseLibrary.all.associateBy { it.id }
    val rows = StringBuilder(
        "session,set,exercise,repIndex,validity,durationMs,descentMs,ascentMs," +
            "peakLeft,troughLeft,peakRight,troughRight,faults,verdict\n",
    )
    fun n(v: Double) = if (v.isFinite()) String.format(Locale.ROOT, "%.1f", v) else ""
    var count = 0
    for (session in sessions) {
        session.sets.forEachIndexed { setIndex, set ->
            if (set !is RepetitionSetRecord) return@forEachIndexed
            val profile = byId[set.exerciseId] as? RepetitionExercise ?: return@forEachIndexed
            // DECISIONS.md P3-78: each record's verdict, as the app now judges the stored set.
            val verdicts = RepVerdicts.judgeSet(profile, set.reps)
            set.reps.forEachIndexed { i, rep ->
                val faults = if (rep.validity == RepValidity.VALID) {
                    PostRepAnalysis.analyse(profile, rep, null).findings
                        .filterIsInstance<TechniqueFault>()
                        .joinToString("|") { it.faultId }
                } else ""
                rows.append(
                    "${session.sessionId},${setIndex + 1},${set.exerciseId},${rep.repIndex},${rep.validity}," +
                        "${n(rep.endMs - rep.startMs)},${n(rep.descentMs)},${n(rep.ascentMs)}," +
                        "${n(rep.peakValue.left)},${n(rep.troughValue.left)},${n(rep.peakValue.right)},${n(rep.troughValue.right)}," +
                        "$faults,${verdicts[i].outcome}\n",
                )
                count++
            }
        }
    }
    File(args[1]).writeText(rows.toString())
    println("wrote ${args[1]}: $count repetitions")
}
