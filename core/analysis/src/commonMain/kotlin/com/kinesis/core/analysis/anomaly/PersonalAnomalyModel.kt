package com.kinesis.core.analysis.anomaly

import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.finding.Deviation
import com.kinesis.core.domain.finding.DeviationDirection
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Layer 4's settings - DECISIONS.md P3-34.
 *
 * **Provisional, and supporting no claim:**
 * - [minHistory] waits for the anomaly study's A3;
 * - [withShape] waits for A2.
 *
 * The forest's settings are the paper's. The cut is fixed by the protocol: above the 95th
 * percentile of the history's own scores.
 */
data class AnomalyPolicy(
    val version: String,
    val trees: Int,
    val maxSubsample: Int,
    /** The most recent VALID repetitions a model is fitted on. */
    val historyLimit: Int,
    /** Silent until the history holds this many. */
    val minHistory: Int,
    /** A repetition scoring above this quantile of the history's own scores is unusual. */
    val cutQuantile: Double,
    /** F1 and F2, or F1 alone. */
    val withShape: Boolean,
    val seed: Long,
) {
    init {
        require(trees >= 1) { "trees must be at least 1, got $trees" }
        require(maxSubsample >= 2) { "maxSubsample must be at least 2, got $maxSubsample" }
        require(minHistory >= 2) { "minHistory must be at least 2, got $minHistory" }
        require(historyLimit >= minHistory) { "historyLimit ($historyLimit) must hold minHistory ($minHistory)" }
        require(cutQuantile > 0.0 && cutQuantile < 1.0) { "cutQuantile must be in (0, 1), got $cutQuantile" }
    }

    companion object {
        val PROVISIONAL: AnomalyPolicy = AnomalyPolicy(
            version = "p9-l4-1",
            trees = IsolationForest.DEFAULT_TREES,
            maxSubsample = IsolationForest.DEFAULT_MAX_SUBSAMPLE,
            historyLimit = 200,
            minHistory = 20,
            cutQuantile = 0.95,
            withShape = true,
            seed = 20260930L,
        )
    }
}

/**
 * Layer 4 for one history: personal anomaly detection - CLAUDE.md sections 12a and 13,
 * DECISIONS.md P3-34.
 *
 * An [IsolationForest] fitted on the user's own most recent VALID repetitions of one [AnomalyKey].
 * A repetition scoring above the 95th percentile of the history's own scores is "unusual for
 * you". The rule needs no one else's data.
 *
 * **Level 2 only:** a [Deviation] with [DeviationDirection.UNUSUAL_FOR_YOU]. It names no fault,
 * and never says a repetition was wrong.
 *
 * **Not wired into the app.** Wiring is its own decision (P3-34), taken after the anomaly study
 * says whether this adds anything over Layer 1.
 *
 * Post-rep path: fitted after a set, then one score per repetition.
 */
class PersonalAnomalyModel private constructor(
    val profile: RepetitionExercise,
    val key: AnomalyKey,
    val policy: AnomalyPolicy,
    private val forest: IsolationForest,
    /** How many repetitions it was fitted on. */
    val historySize: Int,
    /** The score above which a repetition is unusual. */
    val cut: Double,
) {

    /** This repetition's score, or `NaN` when one of its features was not measured. */
    fun score(rep: RepRecord): Double {
        require(AnomalyFeatures.keyOf(profile, rep) == key) {
            "this model is for $key, not ${AnomalyFeatures.keyOf(profile, rep)}"
        }
        val features = AnomalyFeatures.of(profile, rep, policy.withShape) ?: return Double.NaN
        return forest.score(features)
    }

    /** "Unusual for you", or `null`. Only a VALID repetition is judged. */
    fun assess(rep: RepRecord): Deviation? {
        val score = score(rep)
        if (rep.validity != RepValidity.VALID || !(score > cut)) return null
        return Deviation(
            metric = "unusual_for_you",
            direction = DeviationDirection.UNUSUAL_FOR_YOU,
            displayText = "Unusual for you: this repetition was unlike your last $historySize " +
                "(score ${hundredths(score)}; your own repetitions reach ${hundredths(cut)}).",
            ruleVersion = policy.version,
        )
    }

    companion object {

        /**
         * A model over [history], in time order, of the repetitions that are VALID, belong to
         * [key] and have every feature. `null` while fewer than [AnomalyPolicy.minHistory].
         */
        fun fit(
            profile: RepetitionExercise,
            key: AnomalyKey,
            history: List<RepRecord>,
            policy: AnomalyPolicy = AnomalyPolicy.PROVISIONAL,
        ): PersonalAnomalyModel? {
            require(key.exerciseId == profile.id) { "$key is not a ${profile.id} history" }
            val rows = history
                .filter { it.validity == RepValidity.VALID && AnomalyFeatures.keyOf(profile, it) == key }
                .mapNotNull { AnomalyFeatures.of(profile, it, policy.withShape) }
                .takeLast(policy.historyLimit)
            if (rows.size < policy.minHistory) return null
            val forest = IsolationForest.fit(rows, policy.seed, policy.trees, policy.maxSubsample)
            val scores = rows.map { forest.score(it) }.sorted()
            // Nearest rank: the smallest score with at least cutQuantile of the history at or below it.
            val cut = scores[ceil(policy.cutQuantile * rows.size).toInt() - 1]
            return PersonalAnomalyModel(profile, key, policy, forest, rows.size, cut)
        }
    }
}

/** A value to two decimal places, written from integers so every target prints it the same. */
internal fun hundredths(value: Double): String {
    val scaled = (abs(value) * 100.0).roundToLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val cents = scaled % 100
    return sign + (scaled / 100).toString() + "." + (if (cents < 10) "0" else "") + cents.toString()
}
