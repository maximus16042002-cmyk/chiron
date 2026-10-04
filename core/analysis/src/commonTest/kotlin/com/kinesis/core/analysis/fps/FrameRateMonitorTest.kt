package com.kinesis.core.analysis.fps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameRateMonitorTest {

    @Test
    fun initialStateIsHealthyWithNaNFps() {
        val monitor = FrameRateMonitor()
        assertEquals(FrameRateHealth.HEALTHY, monitor.health)
        assertTrue(monitor.currentFps.isNaN())
        assertFalse(monitor.isDegraded)
        assertNull(monitor.warningMessage)
    }

    @Test
    fun steadyThirtyFpsStreamRemainsHealthy() {
        val monitor = FrameRateMonitor()
        var time = 1000.0
        // 33.33ms per frame = 30 FPS
        repeat(30) {
            monitor.onFrame(time)
            time += 33.333333333
        }

        assertEquals(FrameRateHealth.HEALTHY, monitor.health)
        assertTrue(monitor.currentFps in 29.0..31.0)
        assertFalse(monitor.isDegraded)
        assertNull(monitor.warningMessage)
    }

    @Test
    fun hysteresisPreventsInstantDegradationOnIsolatedSlowFrames() {
        val monitor = FrameRateMonitor()
        var time = 1000.0

        // 20 frames at 30 FPS
        repeat(20) {
            monitor.onFrame(time)
            time += 33.333333333
        }
        assertEquals(FrameRateHealth.HEALTHY, monitor.health)

        // 5 slow frames at 8 FPS (125ms interval) - less than HYSTERESIS_FRAMES (10)
        repeat(5) {
            monitor.onFrame(time)
            time += 125.0
        }

        // Must still NOT be DEGRADED because hysteresis requires 10 frames
        assertFalse(monitor.isDegraded)
    }

    @Test
    fun sustainedSubTwelveFpsTransitionsToDegradedAndExposesWarning() {
        val monitor = FrameRateMonitor()
        var time = 1000.0

        // 30 frames at 8 FPS (125ms per frame)
        repeat(30) {
            monitor.onFrame(time)
            time += 125.0
        }

        assertEquals(FrameRateHealth.DEGRADED, monitor.health)
        assertTrue(monitor.isDegraded)
        assertTrue(monitor.currentFps < 12.0)
        assertNotNull(monitor.warningMessage)
        assertTrue(monitor.warningMessage!!.contains("tracking accuracy reduced"))
    }

    @Test
    fun recoveryFromDegradedRequiresSustainedFastFrames() {
        val monitor = FrameRateMonitor()
        var time = 1000.0

        // Drive into degraded state
        repeat(30) {
            monitor.onFrame(time)
            time += 125.0
        }
        assertTrue(monitor.isDegraded)

        // 5 frames at 30 FPS (less than hysteresis recovery frames)
        repeat(5) {
            monitor.onFrame(time)
            time += 33.333333333
        }
        // Still degraded during debounce
        assertTrue(monitor.isDegraded)

        // 20 more frames at 30 FPS
        repeat(20) {
            monitor.onFrame(time)
            time += 33.333333333
        }

        // Recovered!
        assertFalse(monitor.isDegraded)
        assertEquals(FrameRateHealth.HEALTHY, monitor.health)
        assertNull(monitor.warningMessage)
    }

    @Test
    fun resetClearsState() {
        val monitor = FrameRateMonitor()
        var time = 1000.0
        repeat(30) {
            monitor.onFrame(time)
            time += 125.0
        }
        assertTrue(monitor.isDegraded)

        monitor.reset()
        assertEquals(FrameRateHealth.HEALTHY, monitor.health)
        assertTrue(monitor.currentFps.isNaN())
        assertFalse(monitor.isDegraded)
    }
}
