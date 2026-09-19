package com.kinesis.core.analysis.temporal

/**
 * A fixed-capacity circular buffer of timestamped samples.
 *
 * The boundary object of CONTRACTS.md section 7's real-time path. Frames go in
 * one at a time and the oldest falls out; the buffer never grows, never
 * reallocates, and a push costs the same on frame one hundred thousand as on
 * frame one.
 *
 * That constancy is the whole point. The old prototype kept every sample of the
 * session in a list and copied it on each frame, which is fine for the first
 * minute and quietly ruinous by the tenth. DECISIONS.md P2-2 has the detail, and
 * RealTimeBudgetTest and PerFrameAllocationTest keep it from coming back.
 *
 * Two parallel `DoubleArray`s rather than an array of sample objects: no boxing,
 * no per-sample allocation, and the storage is laid out contiguously. Timestamps
 * are `Double` milliseconds, not `Long`, per CLAUDE.md section 5 - `Long` does
 * not survive the JS boundary cleanly.
 *
 * Not thread-safe. It is owned by the analysis thread and touched by nothing
 * else.
 */
class DoubleRingBuffer(val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive, got " + capacity }
    }

    private val values = DoubleArray(capacity)
    private val timestamps = DoubleArray(capacity)

    /** Where the next push writes. */
    private var writeIndex: Int = 0

    /** How many slots hold real samples. Rises to [capacity] and stops. */
    var size: Int = 0
        private set

    /** Total pushes since construction or [clear]. Diagnostic; never drives logic. */
    var totalPushes: Long = 0L
        private set

    val isFull: Boolean get() = size == capacity

    val isEmpty: Boolean get() = size == 0

    /**
     * Appends one sample, discarding the oldest if the buffer is full.
     *
     * O(1), and allocates nothing.
     */
    fun push(timestampMs: Double, value: Double) {
        values[writeIndex] = value
        timestamps[writeIndex] = timestampMs
        writeIndex = writeIndex + 1
        if (writeIndex == capacity) writeIndex = 0
        if (size < capacity) size++
        totalPushes++
    }

    /**
     * The value [indexFromOldest] places after the oldest sample held, where 0 is
     * the oldest and `size - 1` is the newest.
     */
    fun valueAt(indexFromOldest: Int): Double = values[slotOf(indexFromOldest)]

    /** The timestamp of the sample at [indexFromOldest]. See [valueAt]. */
    fun timestampAt(indexFromOldest: Int): Double = timestamps[slotOf(indexFromOldest)]

    /** The most recently pushed value. [Double.NaN] when empty. */
    fun newestValue(): Double = if (size == 0) Double.NaN else valueAt(size - 1)

    /** The oldest value still held. [Double.NaN] when empty. */
    fun oldestValue(): Double = if (size == 0) Double.NaN else valueAt(0)

    /** The most recent timestamp. [Double.NaN] when empty. */
    fun newestTimestamp(): Double = if (size == 0) Double.NaN else timestampAt(size - 1)

    /** The oldest timestamp still held. [Double.NaN] when empty. */
    fun oldestTimestamp(): Double = if (size == 0) Double.NaN else timestampAt(0)

    /**
     * Copies the buffer's contents, oldest first, into [destination].
     *
     * For the post-rep path, which may allocate. Not for per-frame use.
     *
     * @return how many samples were written.
     */
    fun copyValuesInto(destination: DoubleArray): Int {
        val count = if (destination.size < size) destination.size else size
        for (index in 0 until count) destination[index] = valueAt(index)
        return count
    }

    /** As [copyValuesInto], for timestamps. */
    fun copyTimestampsInto(destination: DoubleArray): Int {
        val count = if (destination.size < size) destination.size else size
        for (index in 0 until count) destination[index] = timestampAt(index)
        return count
    }

    /** Empties the buffer. The backing arrays are kept and reused. */
    fun clear() {
        writeIndex = 0
        size = 0
        totalPushes = 0L
    }

    private fun slotOf(indexFromOldest: Int): Int {
        require(indexFromOldest in 0 until size) {
            "index " + indexFromOldest + " outside 0 until " + size
        }
        val oldestSlot = if (size < capacity) 0 else writeIndex
        val slot = oldestSlot + indexFromOldest
        return if (slot >= capacity) slot - capacity else slot
    }
}
