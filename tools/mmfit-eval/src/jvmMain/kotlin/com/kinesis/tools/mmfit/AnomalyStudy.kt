package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.anomaly.AnomalyFeatures
import com.kinesis.core.analysis.anomaly.AnomalyKey
import com.kinesis.core.analysis.anomaly.AnomalyPolicy
import com.kinesis.core.analysis.anomaly.PersonalAnomalyModel
import com.kinesis.core.analysis.anomaly.RobustDistance
import com.kinesis.core.analysis.anomaly.nearestRank
import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.calibration.CalibrationPolicy
import com.kinesis.core.analysis.calibration.RobustStatistics
import com.kinesis.core.analysis.deviation.DeviationRules
import com.kinesis.core.analysis.deviation.PersonalReference
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * The anomaly study's data, from the development subjects only - DECISIONS.md P3-34, P3-36, P3-37.
 *
 * The protocol was committed before this ran. Every detector is the shared core's own code: the
 * forest through `PersonalAnomalyModel`, the distance through `RobustDistance`, Layer 1 through the
 * app's calibrator and `DeviationRules`, and the device rule through `nearestRank`. This arranges
 * MM-Fit's repetitions into the protocol's histories, plants the protocol's deviations, and writes
 * the scores. The statistics - equal false-alarm cuts, detection, N_min, agreement across seeds - are
 * `anomaly_study.py`'s.
 *
 * Writes, under the output directory:
 * - `keys.csv`: each subject's histories, one per exercise and anomaly key, and what each supplied;
 * - `a1.csv`: A1, the device rule on the first n and the next 20, for the forest (10 seeds) and the
 *   distance, F1 and F1 + F2;
 * - `a2.csv`: A2, every scored repetition after the first 40, unaltered and planted, under every
 *   detector - one row per repetition and plant;
 * - `models.csv`: each A2 model's history, shrinkage and device cut.
 *
 * Usage: <harness output dir, with primary/> <study output dir>
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <harness output dir> <study output dir>" }
    val out = File(args[1]).also { it.mkdirs() }
    val subjects = Subjects.load()
    val development = subjects.split("development").toSet()
    val sets = StudyInput.read(File(args[0], "primary"), subjects, development, ANOMALY_EXERCISES)
    AnomalyStudy(sets, out).run()
}

/** The seven exercises the harness evaluates (P3-34). The curl, lunge and row are view mismatches (P3-27, P3-32). */
internal val ANOMALY_EXERCISES = listOf("shoulder_press", "lateral_raise", "pushup", "situp", "bicep_curl", "lunge", "row")

/** A1's and A2's histories - P3-34, read as P3-37 records. */
internal object AnomalyWindows {
    const val HISTORY: Int = 40
    const val NEXT: Int = 20
    val A1_SIZES: List<Int> = listOf(10, 20, 40, 80)

    /** A2: fitted on the first 40, or all there are if fewer; every later repetition is scored. */
    fun a2(count: Int): Pair<IntRange, IntRange> {
        val history = minOf(HISTORY, count)
        return (0 until history) to (history until count)
    }

    /** A1: each n with a full 20 after it. */
    fun a1(count: Int): List<Int> = A1_SIZES.filter { it + NEXT <= count }
}

/**
 * Detector 2's robust z - P3-34, P3-37: Layer 1's rule on every feature, the history's median and
 * its IQR / 1.349, under the feature's floor. On F1 these are Layer 1's own z; on F2, each shape
 * point's, under P3-36's shape floor.
 */
internal class RobustZ(rows: List<DoubleArray>, private val floors: DoubleArray) {
    private val references = floors.indices.map { j ->
        PersonalReference.of(RobustStatistics.of(DoubleArray(rows.size) { rows[it][j] }))
    }

    fun z(x: DoubleArray): DoubleArray = DoubleArray(x.size) { references[it].scaled(x[it], floors[it]) }

    fun sumOfSquares(x: DoubleArray): Double = z(x).sumOf { it * it }
}

/**
 * P3-34's planted deviations, on the record and its curve together, each keeping the record
 * self-consistent. Arithmetic on records, not movements anyone made.
 */
internal object AnomalyPlanting {

    /**
     * Range of motion cut by [fraction] of [medianRange] on that side, on [only] that side or on every
     * measured side. The trough moves towards the start as in P3-30, and the curve is scaled towards
     * its own start by the same share, so its shape (F2) is unchanged. `null` if a side would be left
     * with no range.
     */
    fun cutRange(profile: RepetitionExercise, rep: RepRecord, medianRange: BilateralSignal, fraction: Double, only: Side?): RepRecord? {
        fun cuts(side: Side) = rep.rangeOfMotion.on(side).isFinite() && (only == null || only == side)
        fun cut(side: Side): Double {
            val rom = rep.rangeOfMotion.on(side)
            return if (cuts(side)) rom - fraction * medianRange.on(side) else rom
        }
        val rom = BilateralSignal(cut(Side.LEFT), cut(Side.RIGHT))
        if (Side.entries.any { cuts(it) && !(rom.on(it) > 0.0) }) return null
        val sign = if (profile.repSignal.topIsGreater) 1.0 else -1.0
        val trough = BilateralSignal(rep.peakValue.left - sign * rom.left, rep.peakValue.right - sign * rom.right)
        fun curve(side: Side, curve: DoubleArray): DoubleArray {
            if (!cuts(side)) return curve
            val share = rom.on(side) / rep.rangeOfMotion.on(side)
            val start = curve[0]
            return DoubleArray(curve.size) { start + (curve[it] - start) * share }
        }
        return rebuilt(
            rep, rangeOfMotion = rom, troughValue = trough,
            left = curve(Side.LEFT, rep.resampledLeft), right = curve(Side.RIGHT, rep.resampledRight),
        )
    }

    /**
     * A stall at the far position for [share] of the repetition: each measured curve reaches its far
     * value early and holds it. The curve only; range, peak, trough and phase times are unchanged.
     */
    fun stall(profile: RepetitionExercise, rep: RepRecord, share: Double): RepRecord {
        val towardsFar = if (profile.repSignal.topIsGreater) -1.0 else 1.0
        fun curve(side: Side, curve: DoubleArray) =
            if (rep.rangeOfMotion.on(side).isFinite()) stalled(curve, towardsFar, share) else curve
        return rebuilt(rep, left = curve(Side.LEFT, rep.resampledLeft), right = curve(Side.RIGHT, rep.resampledRight))
    }

    /** Each half of the smallest single plant of its kind: range 10% and tempo 1.25x. */
    fun combined(profile: RepetitionExercise, rep: RepRecord, medianRange: BilateralSignal): RepRecord? =
        cutRange(profile, rep, medianRange, 0.1, null)?.let { Planting.speedUp(it, 1.25) }

    /**
     * [curve] with a plateau at its far point lasting [share] of the repetition. The plateau takes
     * [share] of the time before the far point and [share] of the time after it, so the far point stays
     * inside it and the phase times it divides still hold; the rest of the curve is compressed to fit.
     */
    fun stalled(curve: DoubleArray, towardsFar: Double, share: Double): DoubleArray {
        val n = curve.size
        if (n < 2 || curve.any { !it.isFinite() }) return curve.copyOf()
        val start = curve[0]
        var far = 0
        for (i in 1 until n) if ((curve[i] - start) * towardsFar > (curve[far] - start) * towardsFar) far = i
        val farTime = far.toDouble() / (n - 1)
        val plateauStart = farTime * (1.0 - share)
        val plateauEnd = farTime + share * (1.0 - farTime)
        return DoubleArray(n) { i ->
            val t = i.toDouble() / (n - 1)
            when {
                t < plateauStart -> sample(curve, t * farTime / plateauStart)
                t > plateauEnd -> sample(curve, farTime + (t - plateauEnd) * (1.0 - farTime) / (1.0 - plateauEnd))
                else -> curve[far]
            }
        }
    }

    /** The curve at normalised time [u], linearly between its points. */
    private fun sample(curve: DoubleArray, u: Double): Double {
        val x = u.coerceIn(0.0, 1.0) * (curve.size - 1)
        val i = floor(x).toInt().coerceAtMost(curve.size - 2)
        val f = x - i
        return curve[i] + (curve[i + 1] - curve[i]) * f
    }

    fun rebuilt(
        rep: RepRecord,
        repIndex: Int = rep.repIndex,
        rangeOfMotion: BilateralSignal = rep.rangeOfMotion,
        troughValue: BilateralSignal = rep.troughValue,
        left: DoubleArray = rep.resampledLeft,
        right: DoubleArray = rep.resampledRight,
    ) = RepRecord(
        repIndex = repIndex, attributedSide = rep.attributedSide, startMs = rep.startMs, endMs = rep.endMs,
        validity = rep.validity, rangeOfMotion = rangeOfMotion, peakValue = rep.peakValue, troughValue = troughValue,
        descentMs = rep.descentMs, ascentMs = rep.ascentMs, resampledLeft = left, resampledRight = right,
    )
}

/** One subject's repetitions of one exercise and anomaly key, in time order. */
internal class KeyHistory(val subject: String, val profile: RepetitionExercise, val key: AnomalyKey, val reps: List<RepRecord>) {
    val label: String get() = (key.attributedSide?.name ?: "-") + "/" + (if (key.measuresLeft) "L" else "") + (if (key.measuresRight) "R" else "")
}

internal class AnomalyStudy(private val sets: List<StudySet>, private val out: File) {

    /** The policy's seed first, then the next nine: A4's ten. */
    private val seeds = (0 until 10).map { AnomalyPolicy.PROVISIONAL.seed + it }
    private val featureSets = listOf(false to "F1", true to "F1F2")

    /** VALID repetitions with every feature of F1 + F2, so every detector sees the same repetitions. */
    private val dropped = HashMap<Pair<String, String>, Int>()

    /**
     * Per subject and exercise: workouts in MM-Fit's numbering, sets in order, repetitions in time,
     * renumbered in that order so the calibrator's own ordering follows time across sets.
     */
    private val histories: List<KeyHistory> by lazy {
        val result = ArrayList<KeyHistory>()
        val bySubject = sets.groupBy { it.subject to it.profile.id }.toSortedMap(compareBy({ it.first }, { it.second }))
        for ((id, group) in bySubject) {
            val profile = group.first().profile
            val ordered = group.sortedWith(compareBy({ it.workout }, { it.mmfitSet }))
                .flatMap { set -> set.reps.sortedBy { it.repIndex } }
                .filter { it.validity == RepValidity.VALID }
            val usable = ordered.filter { AnomalyFeatures.of(profile, it, withShape = true) != null }
            dropped[id] = ordered.size - usable.size
            val renumbered = usable.mapIndexed { i, rep -> AnomalyPlanting.rebuilt(rep, repIndex = i + 1) }
            for ((key, reps) in renumbered.groupBy { AnomalyFeatures.keyOf(profile, it) }) {
                result += KeyHistory(id.first, profile, key, reps)
            }
        }
        result.sortedWith(compareBy({ it.subject }, { it.profile.id }, { it.label }))
    }

    private var layerOneChecks = 0
    private var layerOneMismatches = 0

    fun run() {
        writeKeys()
        writeA1()
        writeA2()
        println("Layer 1 against detector 2's F1 z: $layerOneChecks repetitions checked, $layerOneMismatches differed by more than 1e-9")
        check(layerOneMismatches == 0) { "detector 2's F1 z is not Layer 1's own z" }
    }

    private fun writeKeys() {
        val rows = StringBuilder("subject,exercise,key,used,droppedForExercise,a1Sizes,a2History,a2Scored\n")
        for (h in histories) {
            val (history, scored) = AnomalyWindows.a2(h.reps.size)
            rows.append("${h.subject},${h.profile.id},${h.label},${h.reps.size},${dropped[h.subject to h.profile.id]},")
            rows.append("\"${AnomalyWindows.a1(h.reps.size).joinToString(" ")}\",${history.count()},${scored.count()}\n")
        }
        File(out, "keys.csv").writeText(rows.toString())
    }

    // --- A1: the device rule as the history grows ------------------------------------------------

    private fun writeA1() {
        val rows = StringBuilder("subject,exercise,key,n,detector,features,seed,cut,j,score,flagged\n")
        for (h in histories) for (n in AnomalyWindows.a1(h.reps.size)) {
            val train = h.reps.subList(0, n)
            val next = h.reps.subList(n, n + AnomalyWindows.NEXT)
            for ((withShape, name) in featureSets) {
                val prefix = "${h.subject},${h.profile.id},${h.label},$n"
                for (seed in seeds) {
                    val model = forest(h, train, withShape, seed)
                    next.forEachIndexed { j, rep ->
                        val score = model.score(rep)
                        rows.append("$prefix,forest,$name,$seed,${num(model.cut)},${j + 1},${num(score)},${flag(score > model.cut)}\n")
                    }
                }
                val distance = Distance(h, train, withShape)
                next.forEachIndexed { j, rep ->
                    val score = distance.score(rep)
                    rows.append("$prefix,distance,$name,,${num(distance.cut)},${j + 1},${num(score)},${flag(score > distance.cut)}\n")
                }
            }
        }
        File(out, "a1.csv").writeText(rows.toString())
    }

    private fun forest(h: KeyHistory, train: List<RepRecord>, withShape: Boolean, seed: Long): PersonalAnomalyModel {
        // The study asks when the model may speak, so it is never silent here.
        val policy = AnomalyPolicy.PROVISIONAL.copy(minHistory = 2, withShape = withShape, seed = seed)
        return checkNotNull(PersonalAnomalyModel.fit(h.profile, h.key, train, policy)) { "${h.subject} ${h.profile.id} ${h.label}: no model" }
    }

    /** Detector 4 with the device rule: above the nearest-rank 95th percentile of its own history. */
    private inner class Distance(private val h: KeyHistory, train: List<RepRecord>, private val withShape: Boolean) {
        private val model: RobustDistance
        val cut: Double

        init {
            val rows = train.map { features(h, it, withShape) }
            model = RobustDistance.fit(rows, AnomalyFeatures.floorsOf(h.key, withShape))
            cut = nearestRank(rows.map { model.distanceSquared(it) }, AnomalyPolicy.PROVISIONAL.cutQuantile)
        }

        val shrinkage: Double get() = model.shrinkage

        fun score(rep: RepRecord): Double = model.distanceSquared(features(h, rep, withShape))
    }

    private fun features(h: KeyHistory, rep: RepRecord, withShape: Boolean): DoubleArray =
        checkNotNull(AnomalyFeatures.of(h.profile, rep, withShape)) { "${h.subject} ${h.profile.id}: a repetition lost a feature" }

    // --- A2: planted deviations after the first 40 ---------------------------------------------

    private fun writeA2() {
        val detectors = listOf("layer1", "layer1c", "sumsqF1", "sumsqF1F2", "distanceF1", "distanceF1F2") +
            featureSets.flatMap { (_, name) -> seeds.indices.map { "forest${name}_s$it" } }
        val rows = StringBuilder("subject,exercise,key,order,plant,size," + detectors.joinToString(",") + "\n")
        val models = StringBuilder("subject,exercise,key,history,scored,layerOneBaseline,features,distanceShrinkage,distanceCut,forestCut\n")
        val skipped = StringBuilder("subject,exercise,key,order,plant,size\n")
        for (h in histories) {
            val (historyRange, scoredRange) = AnomalyWindows.a2(h.reps.size)
            val history = h.reps.subList(historyRange.first, historyRange.last + 1)
            val scored = if (scoredRange.isEmpty()) emptyList() else h.reps.subList(scoredRange.first, scoredRange.last + 1)
            val baseline = layerOneBaseline(h)
            val medianRange = BilateralSignal(
                RobustStatistics.of(history.map { it.rangeOfMotion.left }).median,
                RobustStatistics.of(history.map { it.rangeOfMotion.right }).median,
            )
            val robustZ = featureSets.associate { (withShape, _) ->
                withShape to RobustZ(history.map { features(h, it, withShape) }, AnomalyFeatures.floorsOf(h.key, withShape))
            }
            val distances = featureSets.associate { (withShape, _) -> withShape to Distance(h, history, withShape) }
            val forests = featureSets.associate { (withShape, _) -> withShape to seeds.map { forest(h, history, withShape, it) } }
            for ((withShape, name) in featureSets) {
                models.append("${h.subject},${h.profile.id},${h.label},${history.size},${scored.size},${baseline != null},$name,")
                models.append("${num(distances.getValue(withShape).shrinkage)},${num(distances.getValue(withShape).cut)},${num(forests.getValue(withShape).first().cut)}\n")
            }
            if (scored.isEmpty()) continue

            scored.forEachIndexed { index, rep ->
                val order = index + 1
                val plants = ArrayList<Triple<String, String, RepRecord?>>()
                plants += Triple("none", "", rep)
                for (fraction in listOf(0.2, 0.3, 0.4)) {
                    plants += Triple("rom", pct(fraction), AnomalyPlanting.cutRange(h.profile, rep, medianRange, fraction, null))
                }
                for (factor in listOf(1.5, 2.0)) plants += Triple("tempo", factor.toString(), Planting.speedUp(rep, factor))
                if (h.key.measuresLeft && h.key.measuresRight) {
                    val side = if (order % 2 == 1) Side.LEFT else Side.RIGHT
                    for (fraction in listOf(0.2, 0.3, 0.4)) {
                        plants += Triple("side", pct(fraction), AnomalyPlanting.cutRange(h.profile, rep, medianRange, fraction, side))
                    }
                }
                for (share in listOf(0.15, 0.25)) plants += Triple("stall", pct(share), AnomalyPlanting.stall(h.profile, rep, share))
                plants += Triple("combined", "rom10+tempo1.25", AnomalyPlanting.combined(h.profile, rep, medianRange))

                for ((plant, size, record) in plants) {
                    val prefix = "${h.subject},${h.profile.id},${h.label},$order,$plant,$size"
                    if (record == null) {
                        skipped.append("$prefix\n")
                        continue
                    }
                    val scores = ArrayList<Double>()
                    val layerOne = layerOneZ(h, record, baseline)
                    scores += largest(layerOne.first)
                    scores += largest(layerOne.first + layerOne.second)
                    if (baseline != null) checkAgainstLayerOne(layerOne.first, robustZ.getValue(false).z(features(h, record, false)))
                    for ((withShape, _) in featureSets) scores += robustZ.getValue(withShape).sumOfSquares(features(h, record, withShape))
                    for ((withShape, _) in featureSets) scores += distances.getValue(withShape).score(record)
                    for ((withShape, _) in featureSets) for (model in forests.getValue(withShape)) scores += model.score(record)
                    rows.append(prefix).append(',').append(scores.joinToString(",") { num(it) }).append('\n')
                }
            }
        }
        File(out, "a2.csv").writeText(rows.toString())
        File(out, "models.csv").writeText(models.toString())
        File(out, "a2_skipped.csv").writeText(skipped.toString())
    }

    /**
     * Layer 1's baseline over exactly the A2 history: the app's calibrator, taking every history
     * repetition. A per-limb exercise calibrates both arms at once, so its two keys' histories go in
     * together, each side taking its own.
     */
    private fun layerOneBaseline(h: KeyHistory): RepetitionBaseline? {
        fun historyOf(k: KeyHistory) = k.reps.subList(0, AnomalyWindows.a2(k.reps.size).first.count())
        val reps = if (h.profile.repetitionSemantics == RepetitionSemantics.PER_LIMB) {
            histories.filter { it.subject == h.subject && it.profile.id == h.profile.id }.flatMap { historyOf(it) }
        } else {
            historyOf(h)
        }
        val policy = CalibrationPolicy(
            minValidRepsPerSide = CalibrationPolicy.PROVISIONAL.minValidRepsPerSide,
            maxValidRepsPerSide = AnomalyWindows.HISTORY,
            minHoldMs = CalibrationPolicy.PROVISIONAL.minHoldMs,
            version = "p3-37-a2-history",
        )
        val outcome = Calibration.fromRepetitions(h.profile, reps.sortedBy { it.repIndex }, 0.0, null, policy)
        return (outcome as? CalibrationOutcome.Calibrated)?.baseline as? RepetitionBaseline
    }

    /**
     * Layer 1's z in F1's order (range of motion per measured side, lifting, lowering, left minus
     * right), then its consistency per measured side - the exploratory detector's extra.
     */
    private fun layerOneZ(h: KeyHistory, rep: RepRecord, baseline: RepetitionBaseline?): Pair<List<Double>, List<Double>> {
        if (baseline == null) return emptyList<Double>() to emptyList()
        val d = DeviationRules.deviations(h.profile, rep, baseline)
        val f1 = ArrayList<Double>()
        val consistency = ArrayList<Double>()
        if (h.key.measuresLeft) f1 += d.rangeOfMotion.left
        if (h.key.measuresRight) f1 += d.rangeOfMotion.right
        f1 += d.lifting
        f1 += d.lowering
        if (h.key.measuresLeft && h.key.measuresRight) f1 += d.asymmetry
        if (h.key.measuresLeft) consistency += d.consistency.left
        if (h.key.measuresRight) consistency += d.consistency.right
        return f1 to consistency
    }

    private fun checkAgainstLayerOne(layerOne: List<Double>, ours: DoubleArray) {
        layerOneChecks++
        val same = layerOne.size == ours.size && layerOne.indices.all { abs(layerOne[it] - ours[it]) <= 1e-9 * max(1.0, abs(layerOne[it])) }
        if (!same) layerOneMismatches++
    }

    /** The largest |z| over the measured ones; NaN if none was measured. */
    private fun largest(z: List<Double>): Double = z.filter { it.isFinite() }.maxOfOrNull { abs(it) } ?: Double.NaN

    private fun pct(fraction: Double) = (fraction * 100).toInt().toString()

    private fun flag(value: Boolean) = if (value) "1" else "0"

    private fun num(value: Double): String = if (value.isFinite()) String.format(Locale.ROOT, "%.12g", value) else ""
}
