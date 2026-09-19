package com.kinesis.core.analysis.temporal

/**
 * Bridges short runs of missing data by linear interpolation.
 *
 * **Post-rep path only.** Interpolating needs a measured sample on each side of
 * the gap, and the far side has not happened yet on a live stream. This runs
 * once over a repetition that has already finished, so it may walk the whole
 * signal and may allocate.
 *
 * ### What is not bridged, and why that matters
 *
 * A gap longer than `maxGapMs` is left missing. This is the line between
 * repairing a signal and inventing one: two frames of a wrist lost behind the
 * torso can be reconstructed with reasonable confidence, and a second of it
 * cannot. Filling the second would produce a smooth, plausible, entirely
 * fabricated trajectory, and every measurement downstream would treat it as
 * observation. CLAUDE.md section 22 forbids stating measurements the system
 * never made; this is where that could happen silently.
 *
 * Leading and trailing gaps are never bridged - there is nothing on one side to
 * interpolate from.
 */
object GapInterpolator {

    /**
     * Returns a copy of [values] with short gaps filled.
     *
     * @param timestampsMs sample times, strictly increasing.
     * @param values samples, with [Double.NaN] marking a missing one.
     * @param maxGapMs longest gap that may be bridged, measured between the
     *   surrounding measured samples.
     */
    fun interpolate(
        timestampsMs: DoubleArray,
        values: DoubleArray,
        maxGapMs: Double,
    ): DoubleArray {
        require(timestampsMs.size == values.size) {
            "timestamps and values differ in length: " + timestampsMs.size + " vs " + values.size
        }
        val filled = values.copyOf()
        if (filled.size < 3) return filled

        var index = 0
        while (index < filled.size) {
            if (!filled[index].isNaN()) {
                index++
                continue
            }

            val before = index - 1
            if (before < 0 || filled[before].isNaN()) {
                index++
                continue
            }

            var after = index + 1
            while (after < filled.size && filled[after].isNaN()) after++
            if (after >= filled.size) break

            val spanMs = timestampsMs[after] - timestampsMs[before]
            if (spanMs > 0.0 && spanMs <= maxGapMs) {
                val startValue = filled[before]
                val valueSpan = filled[after] - startValue
                for (gap in index until after) {
                    val fraction = (timestampsMs[gap] - timestampsMs[before]) / spanMs
                    filled[gap] = startValue + fraction * valueSpan
                }
            }

            index = after
        }

        return filled
    }
}
