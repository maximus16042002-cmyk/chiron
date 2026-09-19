package com.kinesis.core.analysis.temporal

/**
 * Resamples a completed repetition onto a fixed number of evenly spaced points.
 *
 * **Post-rep path only.** Runs once per repetition, over a signal that has
 * finished, so it may allocate.
 *
 * ### Why fixed length rather than fixed interval
 *
 * The old prototype resampled onto a fixed time grid - one sample every 33 ms -
 * which normalises the sample rate but leaves a slow repetition longer than a
 * fast one. CLAUDE.md section 15 asks for fixed-*length* signals for completed
 * reps, and CONTRACTS.md section 7 names 64, because the comparisons downstream
 * need repetitions of different durations to line up: every rep becomes 64
 * points from start to finish, so point 32 is the midpoint of the movement
 * whether it took one second or four.
 *
 * Duration is not lost by this - it is measured separately, from the timestamps,
 * and is one of the Level 1 outputs. What resampling removes is duration's
 * interference with *shape* comparison.
 *
 * Camera jitter is handled in passing: the output grid is uniform in time
 * regardless of how irregularly the input arrived.
 */
object SignalResampler {

    /**
     * Resamples [values] to exactly [length] points, evenly spaced in time from
     * the first timestamp to the last.
     *
     * Each output point is linearly interpolated between the measured samples on
     * either side of it. An output point is [Double.NaN] when the surrounding
     * measured samples are further apart than [maxGapMs] - the same refusal to
     * invent data as [GapInterpolator], applied to the grid.
     *
     * @param timestampsMs sample times, strictly increasing, at least two.
     */
    fun resampleToFixedLength(
        timestampsMs: DoubleArray,
        values: DoubleArray,
        length: Int,
        maxGapMs: Double,
    ): DoubleArray {
        require(timestampsMs.size == values.size) {
            "timestamps and values differ in length: " + timestampsMs.size + " vs " + values.size
        }
        require(timestampsMs.size >= 2) {
            "resampling needs at least two samples, got " + timestampsMs.size
        }
        require(length >= 2) { "length must be at least 2, got " + length }

        val startMs = timestampsMs[0]
        val endMs = timestampsMs[timestampsMs.size - 1]
        require(endMs > startMs) {
            "timestamps must increase: first " + startMs + ", last " + endMs
        }

        val output = DoubleArray(length)
        val step = (endMs - startMs) / (length - 1)

        // Walks forward with the output grid rather than searching per point, so
        // the whole resample is one pass over the input.
        var left = 0
        for (point in 0 until length) {
            val targetMs = if (point == length - 1) endMs else startMs + point * step

            while (left < timestampsMs.size - 2 && timestampsMs[left + 1] <= targetMs) left++

            val right = left + 1
            val leftValue = values[left]
            val rightValue = values[right]

            if (leftValue.isNaN() || rightValue.isNaN()) {
                output[point] = Double.NaN
                continue
            }

            val spanMs = timestampsMs[right] - timestampsMs[left]
            if (spanMs <= 0.0 || spanMs > maxGapMs) {
                output[point] = Double.NaN
                continue
            }

            val fraction = (targetMs - timestampsMs[left]) / spanMs
            output[point] = leftValue + fraction * (rightValue - leftValue)
        }

        return output
    }
}
