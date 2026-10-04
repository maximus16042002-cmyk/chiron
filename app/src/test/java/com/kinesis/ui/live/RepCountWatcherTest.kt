package com.kinesis.ui.live

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tone, vibration or spoken count on a counted repetition fires once per repetition -
 * DECISIONS.md P3-49 and CLAUDE.md section 6. The live state is republished several times a
 * second with the same count; only a rise is a repetition.
 */
class RepCountWatcherTest {

    @Test
    fun a_republished_count_is_not_a_repetition() {
        val watcher = RepCountWatcher()
        assertEquals(listOf(false, false, false), listOf(0, 0, 0).map(watcher::counted))
        assertEquals(listOf(true, false, false), listOf(1, 1, 1).map(watcher::counted))
    }

    @Test
    fun each_rise_fires_once() {
        val watcher = RepCountWatcher()
        assertEquals(listOf(true, true, true), listOf(1, 2, 3).map(watcher::counted))
    }

    /** Two repetitions between publications fire once, for the latest count: no queue of stale cues. */
    @Test
    fun a_jump_fires_once() {
        val watcher = RepCountWatcher()
        watcher.counted(2)
        assertEquals(listOf(true, false), listOf(4, 4).map(watcher::counted))
    }

    /** A lower count is a new set starting (the camera switch starts one, P3-17), not a repetition. */
    @Test
    fun a_new_set_starting_does_not_fire_and_its_first_repetition_does() {
        val watcher = RepCountWatcher()
        watcher.counted(5)
        assertEquals(listOf(false, true), listOf(0, 1).map(watcher::counted))
    }
}
