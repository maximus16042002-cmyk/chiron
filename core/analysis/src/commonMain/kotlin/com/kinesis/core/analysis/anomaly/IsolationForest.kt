package com.kinesis.core.analysis.anomaly

import kotlin.math.ln
import kotlin.math.pow

/**
 * The Isolation Forest of Liu, Ting and Zhou ("Isolation Forest", IEEE ICDM 2008): Layer 4's
 * model - CLAUDE.md section 13, DECISIONS.md P3-34.
 *
 * Each tree splits a random subsample on a random feature at a random value between that
 * feature's minimum and maximum, until every point stands alone or the height limit is reached.
 * A point that random splits isolate quickly is unusual. Its score is
 * `2^(-E[h(x)] / c(psi))`: near 1 for the easily isolated, about 0.5 when nothing stands out.
 *
 * ### Why this model, and written here
 *
 * - **No scaling.** Splits are uniform within each feature's own range, so degrees and
 *   milliseconds need no common unit.
 * - **No library and no solver.** A one-class SVM would need both.
 * - **Deterministic.** Every random choice comes from [SplitMix64] with the caller's seed, so JVM,
 *   JS and Android fit the same trees from the same history.
 *
 * A feature that does not vary within a node cannot be split there, so it is never chosen. A
 * history that never varied in some feature cannot judge a repetition by it: there is no
 * evidence of what is usual.
 *
 * Post-rep path only: fitted after a set, scored once per repetition. Never per frame (CLAUDE.md
 * section 6).
 */
class IsolationForest private constructor(
    val dimensions: Int,
    /** ψ: points per tree, the smaller of the history and the cap. */
    val subsampleSize: Int,
    private val roots: IntArray,
    /** The feature a node splits on, or -1 at a leaf. */
    private val feature: IntArray,
    private val split: DoubleArray,
    /** The child for values below the split. */
    private val below: IntArray,
    /** The child for values at or above it. */
    private val atOrAbove: IntArray,
    /** How many subsample points reached each node. */
    private val size: IntArray,
    private val depth: IntArray,
) {

    val treeCount: Int get() = roots.size

    /** The depth of the deepest leaf in any tree. */
    val deepestLeaf: Int = feature.indices.filter { feature[it] < 0 }.maxOf { depth[it] }

    /** `E[h(x)]`: the path length averaged over the trees, each leaf adjusted by `c(size)`. */
    fun meanPathLength(x: DoubleArray): Double {
        require(x.size == dimensions) { "expected $dimensions features, got ${x.size}" }
        require(x.all { it.isFinite() }) { "every feature must be measured" }
        var total = 0.0
        for (root in roots) {
            var node = root
            var edges = 0
            while (feature[node] >= 0) {
                node = if (x[feature[node]] < split[node]) below[node] else atOrAbove[node]
                edges++
            }
            total += edges + averagePathLength(size[node])
        }
        return total / roots.size
    }

    /** The paper's anomaly score, in (0, 1]. */
    fun score(x: DoubleArray): Double = 2.0.pow(-meanPathLength(x) / averagePathLength(subsampleSize))

    companion object {

        /** The paper's settings: 100 trees, subsamples of at most 256. */
        const val DEFAULT_TREES: Int = 100
        const val DEFAULT_MAX_SUBSAMPLE: Int = 256

        /** The paper's estimate of the harmonic number's constant: `H(i) ≈ ln(i) + 0.5772156649`. */
        private const val EULER_GAMMA: Double = 0.5772156649

        /**
         * `c(n)`: the average path length of an unsuccessful search in a binary search tree of
         * [n] points. It is what a leaf holding [n] points adds, and what normalises the score.
         */
        fun averagePathLength(n: Int): Double = when {
            n <= 1 -> 0.0
            n == 2 -> 1.0
            else -> 2.0 * (ln((n - 1).toDouble()) + EULER_GAMMA) - 2.0 * (n - 1) / n
        }

        fun fit(
            rows: List<DoubleArray>,
            seed: Long,
            trees: Int = DEFAULT_TREES,
            maxSubsample: Int = DEFAULT_MAX_SUBSAMPLE,
        ): IsolationForest {
            require(rows.size >= 2) { "a forest needs at least two rows, got ${rows.size}" }
            val dimensions = rows.first().size
            require(dimensions >= 1) { "rows need at least one feature" }
            require(rows.all { it.size == dimensions }) { "every row must have $dimensions features" }
            require(rows.all { row -> row.all { it.isFinite() } }) { "every feature of every row must be measured" }
            require(trees >= 1) { "trees must be at least 1, got $trees" }
            require(maxSubsample >= 2) { "maxSubsample must be at least 2, got $maxSubsample" }

            val subsample = minOf(maxSubsample, rows.size)
            val builder = Builder(rows, dimensions, SplitMix64(seed), heightLimit(subsample))
            val roots = IntArray(trees)
            val order = IntArray(rows.size)
            for (tree in 0 until trees) {
                // A subsample without replacement: the first ψ of a partial Fisher-Yates shuffle.
                for (i in order.indices) order[i] = i
                for (i in 0 until subsample) {
                    val j = i + builder.rng.nextInt(rows.size - i)
                    val swap = order[i]
                    order[i] = order[j]
                    order[j] = swap
                }
                roots[tree] = builder.node(order.copyOf(subsample), 0, subsample, 0)
            }
            return IsolationForest(
                dimensions, subsample, roots,
                builder.feature.toIntArray(), builder.split.toDoubleArray(), builder.below.toIntArray(),
                builder.atOrAbove.toIntArray(), builder.size.toIntArray(), builder.depth.toIntArray(),
            )
        }

        /** The paper's height limit, `ceil(log2 ψ)`, in integers. */
        private fun heightLimit(subsample: Int): Int {
            var height = 0
            while ((1 shl height) < subsample) height++
            return height
        }
    }

    private class Builder(val rows: List<DoubleArray>, val dimensions: Int, val rng: SplitMix64, val heightLimit: Int) {
        val feature = ArrayList<Int>()
        val split = ArrayList<Double>()
        val below = ArrayList<Int>()
        val atOrAbove = ArrayList<Int>()
        val size = ArrayList<Int>()
        val depth = ArrayList<Int>()

        private val varying = IntArray(dimensions)
        private val lows = DoubleArray(dimensions)
        private val highs = DoubleArray(dimensions)

        /** Grows the subtree over `points[from until to]` and returns its root's index. */
        fun node(points: IntArray, from: Int, to: Int, level: Int): Int {
            val index = feature.size
            feature += -1
            split += Double.NaN
            below += -1
            atOrAbove += -1
            size += to - from
            depth += level
            if (level >= heightLimit || to - from <= 1) return index

            // Only a feature that still varies here can split it.
            var candidates = 0
            for (q in 0 until dimensions) {
                var low = Double.POSITIVE_INFINITY
                var high = Double.NEGATIVE_INFINITY
                for (i in from until to) {
                    val v = rows[points[i]][q]
                    if (v < low) low = v
                    if (v > high) high = v
                }
                if (high > low) {
                    varying[candidates] = q
                    lows[candidates] = low
                    highs[candidates] = high
                    candidates++
                }
            }
            if (candidates == 0) return index

            val pick = rng.nextInt(candidates)
            val q = varying[pick]
            val low = lows[pick]
            val at = low + rng.nextDouble() * (highs[pick] - low)
            var middle = from
            for (i in from until to) {
                if (rows[points[i]][q] < at) {
                    val swap = points[i]
                    points[i] = points[middle]
                    points[middle] = swap
                    middle++
                }
            }
            feature[index] = q
            split[index] = at
            val lower = node(points, from, middle, level + 1)
            val upper = node(points, middle, to, level + 1)
            below[index] = lower
            atOrAbove[index] = upper
            return index
        }
    }
}
