package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.calibration.RobustStatistics
import com.kinesis.core.analysis.deviation.AcsmTempoRule
import com.kinesis.core.analysis.deviation.DeviationPolicy
import com.kinesis.core.analysis.deviation.DeviationRules
import com.kinesis.core.analysis.deviation.IQR_PER_SD
import com.kinesis.core.analysis.deviation.PopulationReference
import com.kinesis.core.analysis.deviation.RepFeatures
import com.kinesis.core.analysis.profile.AnalysedSides
import com.kinesis.core.analysis.profile.Contraction
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustStatistic
import com.kinesis.core.domain.profile.TierAProfile
import com.kinesis.core.domain.rep.FormMeasure
import com.kinesis.core.domain.rep.FormMeasures
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import java.io.File
import java.util.Locale

/**
 * The personalisation study's data, from the development subjects only - DECISIONS.md P3-30.
 *
 * The protocol was committed before this ran. Everything analytical is the shared core's own code, as
 * P7 ran the phone's own pipeline: the app's calibrator (`Calibration.fromRepetitions`), Layer 1's
 * features and personal deviations (`RepFeatures`, `DeviationRules.deviations`), the population
 * reference (`PopulationReference`) and the ACSM rule. This decides nothing about movement; it arranges
 * MM-Fit's repetitions into the study's design and writes what the core computed. The statistics -
 * ICC, operating points, curves, convergence - are `personalisation_study.py`'s.
 *
 * Writes, under the output directory:
 * - `reps.csv`: every VALID repetition of the study's exercises, one row per feature (S1, S4);
 * - `scored.csv`: each scored repetition's features against its session's baseline and against the
 *   other subjects' population, unplanted and planted (S2, S3);
 * - `acsm.csv`: the ACSM tempo rule on each scored repetition of a Tier A exercise (S2);
 * - `sessions.csv`: which sessions calibrated, and why not;
 * - `s5.csv`: S5 - each of A's and B's calibrated sessions as the baseline, scored against every used
 *   repetition of their other sessions (P3-30's addendum, P3-40);
 * - `decision3.csv`: Decision 3 - each scored repetition's features with the baseline statistics they
 *   are judged against, unplanted and with a 30% range cut (P3-30's addendum, P3-41).
 *
 * Usage: <harness output dir, with primary/> <study output dir> [held-out]
 *
 * With `held-out` - DECISIONS.md P3-42, approved by the owner for one run - the held-out workouts
 * instead, and only `decision3.csv` and `decision3_sessions.csv`: the frozen Layer 1 setting's test.
 * Nothing else of P3-30's study is computed on them.
 */
fun main(args: Array<String>) {
    require(args.size == 2 || (args.size == 3 && args[2] == "held-out")) {
        "usage: <harness output dir> <study output dir> [held-out]"
    }
    val heldOut = args.size == 3
    val out = File(args[1]).also { it.mkdirs() }
    val subjects = Subjects.load()
    val workouts = subjects.split(if (heldOut) "held-out" else "development").toSet()
    val sets = StudyInput.read(File(args[0], "primary"), subjects, workouts)
    val study = PersonalisationStudy(sets, out)
    if (heldOut) study.runLayerOneTest() else study.run()
}

/** The study's exercises: those whose MM-Fit view matches the profile (P3-27). */
internal val STUDY_EXERCISES = listOf("shoulder_press", "bicep_curl", "lateral_raise", "pushup", "situp")

internal class StudySet(
    val subject: String,
    val workout: String,
    val mmfitSet: Int,
    val profile: RepetitionExercise,
    val reps: List<RepRecord>,
)

/** One feature of one repetition, keyed so that one key is one quantity: `rom_left`, `lift_rep`, `rom_near`... */
internal class Feature(val key: String, val value: Double, val floor: Double)

internal class PersonalisationStudy(private val sets: List<StudySet>, private val out: File) {

    private val policy = DeviationPolicy.PROVISIONAL

    /** For a near-side-only exercise, the side each subject's sets show nearer the camera most often. */
    private val nearSide: Map<Pair<String, String>, Side> = sets
        .filter { it.profile.analysedSides == AnalysedSides.NEAR_SIDE_ONLY }
        .groupBy { it.subject to it.profile.id }
        .mapValues { (_, group) ->
            val valid = group.flatMap { it.reps }.filter { it.validity == RepValidity.VALID }
            val left = valid.count { it.rangeOfMotion.left.isFinite() }
            val right = valid.count { it.rangeOfMotion.right.isFinite() }
            if (left > right) Side.LEFT else Side.RIGHT
        }

    /** A repetition the study uses: VALID, and on a near-side exercise measured on the subject's near side. */
    private fun used(set: StudySet, rep: RepRecord): Boolean {
        if (rep.validity != RepValidity.VALID) return false
        val near = nearSide[set.subject to set.profile.id] ?: return true
        return rep.rangeOfMotion.on(near).isFinite()
    }

    /** The repetition's features under the study's keys - per side, never averaged. */
    private fun features(set: StudySet, rep: RepRecord): List<Feature> {
        val f = RepFeatures.of(set.profile, rep)
        val rom = policy.rangeOfMotionFloorDeg
        val time = policy.timeFloorMs
        val near = nearSide[set.subject to set.profile.id]
        return when {
            near != null -> listOf(
                Feature("rom_near", f.rangeOfMotion.on(near), rom),
                Feature("lift_near", f.liftingMs, time),
                Feature("lower_near", f.loweringMs, time),
            )
            set.profile.repetitionSemantics == RepetitionSemantics.PER_LIMB -> {
                val side = f.timingSide ?: return emptyList()
                val name = side.name.lowercase()
                listOf(
                    Feature("rom_$name", f.rangeOfMotion.on(side), rom),
                    Feature("lift_$name", f.liftingMs, time),
                    Feature("lower_$name", f.loweringMs, time),
                )
            }
            else -> listOf(
                Feature("rom_left", f.rangeOfMotion.left, rom),
                Feature("rom_right", f.rangeOfMotion.right, rom),
                Feature("lift_rep", f.liftingMs, time),
                Feature("lower_rep", f.loweringMs, time),
                Feature("asym", f.leftMinusRight, policy.asymmetryFloorDeg),
            )
        }
    }

    /** The same keys' personal deviations, from the core's Layer 1. */
    private fun personal(set: StudySet, rep: RepRecord, baseline: RepetitionBaseline): Map<String, Double> {
        val d = DeviationRules.deviations(set.profile, rep, baseline, policy)
        val near = nearSide[set.subject to set.profile.id]
        val timingSide = RepFeatures.of(set.profile, rep).timingSide
        return when {
            near != null -> mapOf("rom_near" to d.rangeOfMotion.on(near), "lift_near" to d.lifting, "lower_near" to d.lowering)
            set.profile.repetitionSemantics == RepetitionSemantics.PER_LIMB -> {
                val side = timingSide ?: return emptyMap()
                val name = side.name.lowercase()
                mapOf("rom_$name" to d.rangeOfMotion.on(side), "lift_$name" to d.lifting, "lower_$name" to d.lowering)
            }
            else -> mapOf(
                "rom_left" to d.rangeOfMotion.left, "rom_right" to d.rangeOfMotion.right,
                "lift_rep" to d.lifting, "lower_rep" to d.lowering, "asym" to d.asymmetry,
            )
        }
    }

    fun run() {
        writeReps()
        writeScored()
        writeS5()
        writeDecision3()
    }

    /** P3-42's held-out test: the Decision 3 rows alone. */
    fun runLayerOneTest() {
        writeDecision3()
    }

    // --- S1 and S4: every used repetition's features ------------------------------------------

    private fun writeReps() {
        val rows = StringBuilder("subject,workout,mmfitSet,exercise,order,repIndex,feature,value\n")
        for ((key, group) in sessions()) {
            var order = 0
            for (set in group) for (rep in set.reps.sortedBy { it.repIndex }) {
                if (!used(set, rep)) continue
                order++
                for (feature in features(set, rep)) {
                    rows.append("${set.subject},${set.workout},${set.mmfitSet},${key.second},$order,${rep.repIndex},${feature.key},${num(feature.value)}\n")
                }
            }
        }
        File(out, "reps.csv").writeText(rows.toString())
    }

    /** Each (workout, exercise)'s sets in MM-Fit's order. */
    private fun sessions(): List<Pair<Pair<String, String>, List<StudySet>>> =
        sets.groupBy { it.workout to it.profile.id }.toSortedMap(compareBy({ it.first }, { it.second }))
            .map { (key, group) -> key to group.sortedBy { it.mmfitSet } }

    // --- S2 and S3: scored repetitions against a session baseline and a population ------------

    /** Per subject, exercise and feature key: the median and robust SD of all their used repetitions. */
    private val subjectStatistics: Map<Triple<String, String, String>, Pair<Double, Double>> by lazy {
        val values = HashMap<Triple<String, String, String>, MutableList<Double>>()
        for (set in sets) for (rep in set.reps) {
            if (!used(set, rep)) continue
            for (feature in features(set, rep)) {
                if (feature.value.isFinite()) values.getOrPut(Triple(set.subject, set.profile.id, feature.key)) { ArrayList() } += feature.value
            }
        }
        values.mapValues { (_, v) ->
            val statistic = RobustStatistics.of(v)
            statistic.median to statistic.interquartileRange / IQR_PER_SD
        }
    }

    /** The population a subject is scored against: the other development subjects, each counted once. */
    private fun population(subject: String, exercise: String, key: String): PopulationReference? {
        val others = subjectStatistics.filterKeys { it.first != subject && it.second == exercise && it.third == key }.values
        if (others.isEmpty()) return null
        return PopulationReference.fromSubjects(
            medians = others.map { it.first }.toDoubleArray(),
            withinSpreads = others.map { it.second }.toDoubleArray(),
            provenance = "derived from MM-Fit development subjects other than $subject, P3-30",
        )
    }

    private fun writeScored() {
        val scored = StringBuilder("subject,workout,exercise,repKey,order,deviation,feature,value,zPersonal,zPopulation\n")
        val acsm = StringBuilder("subject,workout,exercise,repKey,flagged\n")
        val sessionRows = StringBuilder("subject,workout,exercise,calibrationSet,outcome,scoredReps\n")
        for ((key, group) in sessions()) {
            val first = group.first()
            val outcome = Calibration.fromRepetitions(first.profile, first.reps, 0.0, null, com.kinesis.core.analysis.calibration.CalibrationPolicy.PROVISIONAL)
            val baseline = (outcome as? CalibrationOutcome.Calibrated)?.baseline as? RepetitionBaseline
            var scoredCount = 0
            if (baseline != null) {
                var order = 0
                for (set in group.drop(1)) for (rep in set.reps.sortedBy { it.repIndex }) {
                    if (!used(set, rep)) continue
                    order++
                    scoredCount++
                    val repKey = "${set.workout}|${set.mmfitSet}|${rep.repIndex}"
                    fun write(deviation: String, record: RepRecord, only: Set<String>? = null) {
                        val z = personal(set, record, baseline)
                        for (feature in features(set, record)) {
                            if (only != null && feature.key !in only) continue
                            val zPop = population(set.subject, key.second, feature.key)?.scaled(feature.value, feature.floor) ?: Double.NaN
                            scored.append("${set.subject},${set.workout},${key.second},$repKey,$order,$deviation,${feature.key},${num(feature.value)},${num(z[feature.key] ?: Double.NaN)},${num(zPop)}\n")
                        }
                    }
                    write("none", rep)
                    for (fraction in listOf(0.2, 0.3, 0.4)) {
                        write("rom${(fraction * 100).toInt()}", Planting.cutRange(set.profile, rep, baseline, fraction, null),
                            only = features(set, rep).filter { it.key.startsWith("rom_") }.map { it.key }.toSet())
                    }
                    for (factor in listOf(1.5, 2.0)) {
                        write("tempo$factor", Planting.speedUp(rep, factor),
                            only = features(set, rep).filter { it.key.startsWith("lift_") || it.key.startsWith("lower_") }.map { it.key }.toSet())
                    }
                    if (features(set, rep).any { it.key == "asym" }) {
                        val side = if (order % 2 == 1) Side.LEFT else Side.RIGHT
                        for (fraction in listOf(0.2, 0.3, 0.4)) {
                            write("side${(fraction * 100).toInt()}", Planting.cutRange(set.profile, rep, baseline, fraction, side),
                                only = setOf("asym", "rom_" + side.name.lowercase()))
                        }
                    }
                    val authority = (set.profile as? TierAProfile)?.techniqueAuthority
                    if (authority != null) {
                        val flagged = AcsmTempoRule.evaluate(authority, set.profile, rep) != null
                        acsm.append("${set.subject},${set.workout},${key.second},$repKey,${if (flagged) 1 else 0}\n")
                    }
                }
            }
            val why = when (outcome) {
                is CalibrationOutcome.Calibrated -> "calibrated"
                is CalibrationOutcome.TooFewRepetitions -> "too few valid repetitions (left ${outcome.validLeft}, right ${outcome.validRight})"
                is CalibrationOutcome.HoldNotUsable -> "not a repetition set"
            }
            sessionRows.append("${first.subject},${first.workout},${key.second},${first.mmfitSet},\"$why\",$scoredCount\n")
        }
        File(out, "scored.csv").writeText(scored.toString())
        File(out, "acsm.csv").writeText(acsm.toString())
        File(out, "sessions.csv").writeText(sessionRows.toString())
    }

    // --- S5: one session's baseline against the same person's other sessions --------------------

    /**
     * S5, as fixed in P3-30's addendum: for subjects A and B and the three interpretable exercises,
     * each session whose first set calibrates serves in turn as the baseline, and every used
     * repetition of every set of each of that subject's other sessions is scored against it. The
     * same-session figure is S2's own (`scored.csv`). "Sessions of unknown spacing": MM-Fit gives no
     * dates.
     */
    private fun writeS5() {
        val rows = StringBuilder("subject,exercise,baselineWorkout,otherWorkout,mmfitSet,repIndex,feature,value,zPersonal\n")
        val bySession = sessions().filter { (key, group) -> group.first().subject in S5_SUBJECTS && key.second in S5_EXERCISES }
        for ((key, group) in bySession) {
            val first = group.first()
            val outcome = Calibration.fromRepetitions(first.profile, first.reps, 0.0, null, com.kinesis.core.analysis.calibration.CalibrationPolicy.PROVISIONAL)
            val baseline = (outcome as? CalibrationOutcome.Calibrated)?.baseline as? RepetitionBaseline ?: continue
            for ((otherKey, otherGroup) in bySession) {
                if (otherKey.second != key.second || otherKey.first == key.first || otherGroup.first().subject != first.subject) continue
                for (set in otherGroup) for (rep in set.reps.sortedBy { it.repIndex }) {
                    if (!used(set, rep)) continue
                    val z = personal(set, rep, baseline)
                    for (feature in features(set, rep)) {
                        rows.append("${first.subject},${key.second},${key.first},${otherKey.first},${set.mmfitSet},${rep.repIndex},")
                        rows.append("${feature.key},${num(feature.value)},${num(z[feature.key] ?: Double.NaN)}\n")
                    }
                }
            }
        }
        File(out, "s5.csv").writeText(rows.toString())
    }

    // --- Decision 3: the scored repetitions with their baseline statistics -------------------

    /**
     * P3-30's Decision 3, as its addendum fixed the rule (P3-41): S2's design - each session's first
     * set calibrates, its later sets are scored - with, for every scored repetition and feature, the
     * value, the baseline statistic it is judged against (median, IQR, count) and the core's own z
     * under today's policy. Unplanted, and with S3's 30% range cut on every measured side. The
     * candidates' z are `decision3.py`'s, from these numbers; left - right and consistency carry the
     * core's z only, since no candidate changes their spread or floor.
     */
    private fun writeDecision3() {
        val rows = StringBuilder("subject,workout,exercise,repKey,deviation,feature,value,centre,iqr,count,zCore\n")
        val calibrations = StringBuilder("subject,workout,exercise,calibrated,laterSets\n")
        for ((key, group) in sessions()) {
            val first = group.first()
            val outcome = Calibration.fromRepetitions(first.profile, first.reps, 0.0, null, com.kinesis.core.analysis.calibration.CalibrationPolicy.PROVISIONAL)
            calibrations.append("${first.subject},${first.workout},${key.second},${outcome is CalibrationOutcome.Calibrated},${group.size - 1}\n")
            val baseline = (outcome as? CalibrationOutcome.Calibrated)?.baseline as? RepetitionBaseline ?: continue
            for (set in group.drop(1)) for (rep in set.reps.sortedBy { it.repIndex }) {
                if (!used(set, rep)) continue
                val repKey = "${set.workout}|${set.mmfitSet}|${rep.repIndex}"
                for ((deviation, record) in listOf("none" to rep, "rom30" to Planting.cutRange(set.profile, rep, baseline, 0.3, null))) {
                    val z = personal(set, record, baseline)
                    val consistency = DeviationRules.deviations(set.profile, record, baseline, policy).consistency
                    for (feature in features(set, record)) {
                        val statistic = statisticOf(set, record, baseline, feature.key)
                        rows.append("${set.subject},${set.workout},${key.second},$repKey,$deviation,${feature.key},${num(feature.value)},")
                        if (statistic != null) {
                            rows.append("${num(statistic.median)},${num(statistic.interquartileRange)},${statistic.count},")
                        } else {
                            rows.append(",,,")
                        }
                        rows.append("${num(z[feature.key] ?: Double.NaN)}\n")
                    }
                    for (side in Side.entries) {
                        if (!record.rangeOfMotion.on(side).isFinite()) continue
                        val name = if (nearSide[set.subject to set.profile.id] != null) "near" else side.name.lowercase()
                        rows.append("${set.subject},${set.workout},${key.second},$repKey,$deviation,cons_$name,,,,,${num(consistency.on(side))}\n")
                    }
                }
            }
        }
        File(out, "decision3.csv").writeText(rows.toString())
        File(out, "decision3_sessions.csv").writeText(calibrations.toString())
    }

    /** The baseline statistic a feature is judged against, as `DeviationRules` chooses it; `null` for left - right. */
    private fun statisticOf(set: StudySet, rep: RepRecord, baseline: RepetitionBaseline, key: String): RobustStatistic? {
        val timingSide = RepFeatures.of(set.profile, rep).timingSide
        fun phase(contraction: Contraction): RobustStatistic {
            val timing = if (contraction == set.profile.awayFromStart) baseline.descentMs else baseline.ascentMs
            return if (timingSide == null) timing.perRepetition else timing.perSide.on(timingSide)
        }
        return when {
            key == "rom_near" -> baseline.rangeOfMotion.on(nearSide.getValue(set.subject to set.profile.id))
            key == "rom_left" -> baseline.rangeOfMotion.left
            key == "rom_right" -> baseline.rangeOfMotion.right
            key.startsWith("lift_") -> phase(Contraction.CONCENTRIC)
            key.startsWith("lower_") -> phase(Contraction.ECCENTRIC)
            else -> null
        }
    }

    private fun num(value: Double): String = if (value.isFinite()) String.format(Locale.ROOT, "%.6f", value) else ""
}

/** S5's subjects and exercises - P3-30's addendum: the two with six sessions, the three interpretable exercises. */
internal val S5_SUBJECTS = setOf("A", "B")
internal val S5_EXERCISES = setOf("shoulder_press", "bicep_curl", "lateral_raise")

/**
 * S3's planted deviations, on the record's own fields - DECISIONS.md P3-30. Arithmetic on records, not
 * movements anyone made: the curves are left as they were, and whether the engine would still count
 * such a repetition is not tested.
 */
internal object Planting {

    /**
     * Range of motion cut by [fraction] of the baseline's own median range, on [only] that side or on
     * every measured side, the trough moved towards the start to match.
     */
    fun cutRange(profile: RepetitionExercise, rep: RepRecord, baseline: RepetitionBaseline, fraction: Double, only: Side?): RepRecord {
        fun cut(side: Side): Double {
            val rom = rep.rangeOfMotion.on(side)
            if (!rom.isFinite() || (only != null && side != only)) return rom
            return rom - fraction * baseline.rangeOfMotion.on(side).median
        }
        val rom = BilateralSignal(cut(Side.LEFT), cut(Side.RIGHT))
        val sign = if (profile.repSignal.topIsGreater) 1.0 else -1.0
        val trough = BilateralSignal(
            rep.peakValue.left - sign * rom.left,
            rep.peakValue.right - sign * rom.right,
        )
        return copy(rep, rangeOfMotion = rom, troughValue = trough)
    }

    /** Both phases [factor] times faster. */
    fun speedUp(rep: RepRecord, factor: Double): RepRecord =
        copy(rep, descentMs = rep.descentMs / factor, ascentMs = rep.ascentMs / factor)

    private fun copy(
        rep: RepRecord,
        rangeOfMotion: BilateralSignal = rep.rangeOfMotion,
        troughValue: BilateralSignal = rep.troughValue,
        descentMs: Double = rep.descentMs,
        ascentMs: Double = rep.ascentMs,
    ) = RepRecord(
        repIndex = rep.repIndex, attributedSide = rep.attributedSide, startMs = rep.startMs, endMs = rep.endMs,
        validity = rep.validity, rangeOfMotion = rangeOfMotion, peakValue = rep.peakValue, troughValue = troughValue,
        descentMs = descentMs, ascentMs = ascentMs, resampledLeft = rep.resampledLeft, resampledRight = rep.resampledRight,
    )
}

/** The harness's P5 exports and sets.csv, as the study's sets. */
internal object StudyInput {

    fun read(protocolDir: File, subjects: Subjects, development: Set<String>, exercises: Collection<String> = STUDY_EXERCISES): List<StudySet> {
        val lines = File(protocolDir, "sets.csv").readLines()
        val header = csv(lines.first())
        val exports = HashMap<String, List<Any?>>()
        val out = ArrayList<StudySet>()
        for (line in lines.drop(1)) {
            val row = header.zip(csv(line)).toMap()
            val workout = row.getValue("workout")
            if (workout !in development) continue // the stop point: development subjects only
            if (row["status"] != "evaluated" || row["profileId"] !in exercises) continue
            val sessionSets = exports.getOrPut(workout) {
                @Suppress("UNCHECKED_CAST")
                val doc = MiniJson(File(protocolDir, "$workout.export.json").readText()).value() as Map<String, Any?>
                check(doc["format"] == "kinesis-export") { "$workout: not a P5 export" }
                @Suppress("UNCHECKED_CAST")
                ((doc["sessions"] as List<Any?>).single() as Map<String, Any?>)["sets"] as List<Any?>
            }
            @Suppress("UNCHECKED_CAST")
            val set = sessionSets[row.getValue("exportSetIndex").toInt() - 1] as Map<String, Any?>
            val profile = ExerciseLibrary.all.first { it.id == row["profileId"] } as RepetitionExercise
            check(set["exerciseId"] == profile.id) { "$workout set ${row["mmfitSetIndex"]}: export set is ${set["exerciseId"]}" }
            @Suppress("UNCHECKED_CAST")
            val reps = (set["reps"] as List<Any?>).map { rep(it as Map<String, Any?>) }
            out += StudySet(subjects.subjectOf(workout), workout, row.getValue("mmfitSetIndex").toInt(), profile, reps)
        }
        return out
    }

    private fun rep(m: Map<String, Any?>): RepRecord = RepRecord(
        repIndex = (m["repIndex"] as Double).toInt(),
        attributedSide = (m["attributedSide"] as String?)?.let { Side.valueOf(it) },
        startMs = d(m["startMs"]),
        endMs = d(m["endMs"]),
        validity = RepValidity.valueOf(m["validity"] as String),
        rangeOfMotion = bilateral(m["rangeOfMotion"]),
        peakValue = bilateral(m["peakValue"]),
        troughValue = bilateral(m["troughValue"]),
        descentMs = d(m["descentMs"]),
        ascentMs = d(m["ascentMs"]),
        resampledLeft = curve(m["resampledLeft"]),
        resampledRight = curve(m["resampledRight"]),
        form = form(m["form"]),
    )

    /** DECISIONS.md P3-44: the form summary, absent from exports written before it. */
    @Suppress("UNCHECKED_CAST")
    private fun form(value: Any?): FormMeasures {
        val map = value as? Map<String, Any?> ?: return FormMeasures.NONE
        val values = LinkedHashMap<FormMeasure, BilateralSignal>()
        for ((id, sides) in map) {
            val measure = FormMeasure.ofId(id) ?: continue
            values[measure] = bilateral(sides)
        }
        return FormMeasures(values)
    }

    private fun d(value: Any?): Double = (value as Double?) ?: Double.NaN

    @Suppress("UNCHECKED_CAST")
    private fun bilateral(value: Any?): BilateralSignal {
        val sides = value as Map<String, Any?>
        return BilateralSignal(d(sides["left"]), d(sides["right"]))
    }

    private fun curve(value: Any?): DoubleArray = (value as List<*>).map { d(it) }.toDoubleArray()

    /** One CSV line, with double-quoted fields. */
    fun csv(line: String): List<String> {
        val fields = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { fields += field.toString(); field.clear() }
                else -> field.append(c)
            }
            i++
        }
        fields += field.toString()
        return fields
    }
}
