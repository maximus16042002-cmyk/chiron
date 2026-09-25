package com.kinesis.camera

import java.util.concurrent.atomic.AtomicLongArray

/**
 * When each of the last few frames was handed to MediaPipe, by frame timestamp -
 * the Phase B readout's MediaPipe latency, DECISIONS.md P3-19.
 *
 * Written on the analysis thread, read on MediaPipe's. Lock-free and
 * allocation-free: two fixed arrays. A slot is only reused after [capacity] more
 * frames have been handed over, far more than are ever in flight, and a read
 * re-checks the timestamp after taking the time, so a slot rewritten mid-read is
 * reported as unknown rather than as another frame's time.
 */
class HandOverTimes(private val capacity: Int = 16) {

    private val stamps = AtomicLongArray(capacity).apply { for (i in 0 until capacity) set(i, EMPTY) }
    private val nanos = AtomicLongArray(capacity)

    // Writer only.
    private var next = 0

    /** Analysis thread. */
    fun record(timestampMs: Long, atNanos: Long) {
        val slot = next
        next = (next + 1) % capacity
        stamps.set(slot, EMPTY)
        nanos.set(slot, atNanos)
        stamps.set(slot, timestampMs)
    }

    /** When the frame stamped [timestampMs] was handed over, or [UNKNOWN]. Any thread. */
    fun at(timestampMs: Long): Long {
        for (slot in 0 until capacity) {
            if (stamps.get(slot) != timestampMs) continue
            val time = nanos.get(slot)
            if (stamps.get(slot) == timestampMs) return time
        }
        return UNKNOWN
    }

    companion object {
        const val UNKNOWN: Long = Long.MIN_VALUE
        private const val EMPTY: Long = Long.MIN_VALUE
    }
}
