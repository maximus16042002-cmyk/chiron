package com.kinesis.core.analysis.anomaly

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Detector 4 of the anomaly study: a robust covariance distance - DECISIONS.md P3-36.
 *
 * Deterministic statistics, not ML (CLAUDE.md section 12), added to A2 by the owner so that the
 * forest is compared against good statistics. For one history of feature rows:
 *
 * 1. **Location** is each feature's median; **scale** is its MAD x 1.4826, floored per feature
 *    (Layer 1's floors, and 0.02 for a shape point - [AnomalyFeatures.floorsOf]).
 * 2. **z** = (x - location) / scale.
 * 3. **R** is the Spearman rank correlation of the history: ranks with ties averaged, then the
 *    Pearson correlation of the ranks. A feature whose ranks never vary correlates with nothing.
 * 4. **R_lambda** = (1 - lambda) R + lambda I, with lambda = min(1, p / n): fixed, not tuned.
 * 5. **d^2** = z' R_lambda^-1 z, by Cholesky.
 *
 * What it sees that P3-35 measured the forest cannot: d^2 keeps growing beyond the history's
 * range, and R_lambda^-1 weighs a combination against how the features went together.
 *
 * Post-rep path: fitted after a set, one distance per repetition. Not wired into the app.
 */
class RobustDistance private constructor(
    val dimensions: Int,
    private val location: DoubleArray,
    private val scale: DoubleArray,
    /** L of R_lambda = L L', row-major, lower triangle. */
    private val lower: DoubleArray,
    /** lambda, the weight on the identity. */
    val shrinkage: Double,
    val historySize: Int,
) {

    /** d^2 = z' R_lambda^-1 z = |L^-1 z|^2, by forward substitution. */
    fun distanceSquared(x: DoubleArray): Double {
        require(x.size == dimensions) { "expected $dimensions features, got ${x.size}" }
        require(x.all { it.isFinite() }) { "every feature must be measured" }
        val p = dimensions
        val y = DoubleArray(p)
        var sum = 0.0
        for (i in 0 until p) {
            var v = (x[i] - location[i]) / scale[i]
            for (k in 0 until i) v -= lower[i * p + k] * y[k]
            y[i] = v / lower[i * p + i]
            sum += y[i] * y[i]
        }
        return sum
    }

    companion object {

        /** The MAD of a normal distribution is its standard deviation divided by this. */
        const val MAD_TO_SIGMA: Double = 1.4826

        fun fit(rows: List<DoubleArray>, floors: DoubleArray): RobustDistance {
            require(rows.size >= 2) { "a distance needs at least two rows, got ${rows.size}" }
            val p = rows.first().size
            require(p >= 1) { "rows need at least one feature" }
            require(rows.all { it.size == p }) { "every row must have $p features" }
            require(floors.size == p) { "expected $p floors, got ${floors.size}" }
            require(floors.all { it > 0.0 && it.isFinite() }) { "every floor must be positive" }
            require(rows.all { row -> row.all { it.isFinite() } }) { "every feature of every row must be measured" }

            val n = rows.size
            val location = DoubleArray(p)
            val scale = DoubleArray(p)
            val centredRanks = Array(p) { DoubleArray(n) }
            val norms = DoubleArray(p)
            val column = DoubleArray(n)
            for (j in 0 until p) {
                for (i in 0 until n) column[i] = rows[i][j]
                val centre = median(column)
                location[j] = centre
                scale[j] = max(median(DoubleArray(n) { abs(column[it] - centre) }) * MAD_TO_SIGMA, floors[j])
                val ranked = ranks(column)
                val mean = ranked.sum() / n
                var squares = 0.0
                for (i in 0 until n) {
                    centredRanks[j][i] = ranked[i] - mean
                    squares += centredRanks[j][i] * centredRanks[j][i]
                }
                norms[j] = sqrt(squares)
            }

            val lambda = min(1.0, p.toDouble() / n)
            val shrunk = DoubleArray(p * p)
            for (a in 0 until p) {
                for (b in 0 until p) {
                    val correlation = when {
                        a == b -> 1.0
                        norms[a] == 0.0 || norms[b] == 0.0 -> 0.0
                        else -> {
                            var s = 0.0
                            for (i in 0 until n) s += centredRanks[a][i] * centredRanks[b][i]
                            s / (norms[a] * norms[b])
                        }
                    }
                    shrunk[a * p + b] = (1.0 - lambda) * correlation + (if (a == b) lambda else 0.0)
                }
            }
            return RobustDistance(p, location, scale, cholesky(shrunk, p), lambda, n)
        }

        /** 1-based ranks, ties given the mean of the ranks they span. */
        internal fun ranks(values: DoubleArray): DoubleArray {
            val order = values.indices.sortedBy { values[it] }
            val result = DoubleArray(values.size)
            var start = 0
            while (start < order.size) {
                var end = start
                while (end + 1 < order.size && values[order[end + 1]] == values[order[start]]) end++
                val rank = (start + end) / 2.0 + 1.0
                for (k in start..end) result[order[k]] = rank
                start = end + 1
            }
            return result
        }

        /** The median; for an even count, the mean of the middle two. */
        internal fun median(values: DoubleArray): Double {
            val sorted = values.sortedArray()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
        }

        /** L with L L' = [a], p x p row-major; [a] must be positive definite, as R_lambda is. */
        private fun cholesky(a: DoubleArray, p: Int): DoubleArray {
            val l = DoubleArray(p * p)
            for (i in 0 until p) {
                for (j in 0..i) {
                    var s = a[i * p + j]
                    for (k in 0 until j) s -= l[i * p + k] * l[j * p + k]
                    if (i == j) {
                        check(s > 0.0) { "the shrunk correlation is not positive definite" }
                        l[i * p + i] = sqrt(s)
                    } else {
                        l[i * p + j] = s / l[j * p + j]
                    }
                }
            }
            return l
        }
    }
}

/**
 * The device rule's cut - DECISIONS.md P3-34, P3-36: the nearest-rank [quantile] of [scores], the
 * smallest score with at least that share of the scores at or below it.
 */
internal fun nearestRank(scores: List<Double>, quantile: Double): Double {
    require(scores.isNotEmpty()) { "no scores" }
    require(quantile > 0.0 && quantile < 1.0) { "quantile must be in (0, 1), got $quantile" }
    val sorted = scores.sorted()
    return sorted[ceil(quantile * sorted.size).toInt() - 1]
}
