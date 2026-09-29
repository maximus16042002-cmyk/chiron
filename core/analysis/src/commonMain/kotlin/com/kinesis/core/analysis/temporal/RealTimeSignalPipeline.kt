package com.kinesis.core.analysis.temporal

/**
 * The per-frame half of CONTRACTS.md section 7, assembled: one scalar signal
 * goes in per frame, a filtered value comes out, and a bounded window of recent
 * history is kept for whatever reads it next.
 *
 *     raw sample -> outlier gate -> EMA -> ring buffer
 *
 * The EMA is weighted by the time since the last sample, in intervals of
 * [TemporalConfig.smoothingIntervalMs], not per sample - DECISIONS.md P3-33.
 *
 * ### Why this order, and why it is not the prototype's order
 *
 * The old prototype ran outlier removal, then gap interpolation, then smoothing,
 * then resampling, and it documented why: outliers first so a spike cannot
 * pollute the smoother or the interpolator, and interpolation before smoothing
 * so a short gap does not jerk the filter state. Both reasons are sound and both
 * are kept.
 *
 * What changed is where the chain is cut. Interpolation and resampling need to
 * see both sides of a gap, so neither can run on a live stream - they belong to
 * the post-rep path, over a repetition that has finished. See [GapInterpolator]
 * and [SignalResampler]. What remains here is the part that is genuinely causal.
 *
 * The prototype ran its whole chain over the entire session on every frame.
 * DECISIONS.md P2-2 records why that is not ported; RealTimeBudgetTest and
 * PerFrameAllocationTest keep it from returning.
 *
 * ### One signal per pipeline
 *
 * A pipeline filters a single scalar - one joint angle on one side, say. A
 * bilateral measurement needs two, which is deliberate: it keeps the left and
 * right filter states genuinely independent, so an occlusion on one side cannot
 * perturb the other.
 */
class RealTimeSignalPipeline(
    val config: TemporalConfig,
    bufferCapacity: Int,
) {

    /** Recent filtered history, bounded. What the P3 repetition FSM will read. */
    val buffer: DoubleRingBuffer = DoubleRingBuffer(bufferCapacity)

    private val outlierGate = MedianOutlierGate(
        windowSamples = config.outlierWindowSamples,
        threshold = config.outlierThreshold,
        readmitSamples = config.outlierReadmitSamples,
    )

    private val smoother = EmaSmoother(config.smoothingAlpha)

    /** When the last sample was pushed, measured or not; [Double.NaN] before the first. */
    private var lastPushMs: Double = Double.NaN

    /**
     * Samples examined by the last [push]. Instrumentation, not behaviour:
     * "this frame did no work proportional to the session" is the invariant
     * CLAUDE.md section 6 turns on, and without a counter there is no way for a
     * test to check it. One integer per frame is a fair price for making the
     * rule enforceable rather than aspirational.
     */
    var lastPushSamplesVisited: Int = 0
        private set

    /** How many samples the outlier gate has rejected. Diagnostic. */
    val rejectedSampleCount: Long get() = outlierGate.rejectedCount

    /** The most recent filtered value, or [Double.NaN]. */
    val current: Double get() = smoother.current

    /**
     * Feeds one frame through the chain and records the result.
     *
     * @param rawValue the measurement for this frame, or [Double.NaN] if it could
     *   not be measured. An unmeasurable sample is buffered as [Double.NaN]
     *   rather than dropped, so the gap keeps its place in time and the post-rep
     *   path can see how long it lasted.
     * @return the filtered value, or [Double.NaN].
     */
    fun push(timestampMs: Double, rawValue: Double): Double {
        val gated = outlierGate.accept(rawValue)
        // After a NaN the smoother starts afresh, so only the time since a measured
        // sample ever weighs anything.
        val smoothed = smoother.update(gated, (timestampMs - lastPushMs) / config.smoothingIntervalMs)
        lastPushMs = timestampMs
        buffer.push(timestampMs, smoothed)
        lastPushSamplesVisited = outlierGate.lastCallSamplesVisited
        return smoothed
    }

    /** Returns the pipeline to its initial state. Storage is kept and reused. */
    fun reset() {
        outlierGate.reset()
        smoother.reset()
        lastPushMs = Double.NaN
        buffer.clear()
        lastPushSamplesVisited = 0
    }
}
