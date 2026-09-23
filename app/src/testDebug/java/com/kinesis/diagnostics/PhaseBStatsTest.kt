package com.kinesis.diagnostics

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.pose.CameraFacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The debug-only Phase B readout - DECISIONS.md P3-17. It exists to measure, on
 * the device, the things P3-15 and P3-16 could not: the frame rate actually
 * sustained, how far an angle moves between frames (against the outlier gate's
 * 20 degrees), and how visible the profile's landmarks really are.
 */
class PhaseBStatsTest {

    private val required = RequiredLandmarks.of(LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE)

    private fun frame(leftVisibility: Double, rightVisibility: Double): Landmarks {
        val packed = DoubleArray(Landmarks.PACKED_SIZE)
        packed[LandmarkId.LEFT_KNEE.index * Landmarks.STRIDE + 3] = leftVisibility
        packed[LandmarkId.RIGHT_KNEE.index * Landmarks.STRIDE + 3] = rightVisibility
        packed[LandmarkId.NOSE.index * Landmarks.STRIDE + 3] = 0.01 // not required, must be ignored
        return Landmarks.fromPacked(packed)
    }

    @Test
    fun frames_per_second_is_measured_over_the_last_second() {
        val stats = PhaseBStats(required)
        val still = frame(0.9, 0.9)
        for (i in 0..90) stats.onFrame(i * 33L, still, 90.0, 90.0) // ~30 fps for 3 s
        assertEquals(1000.0 / 33.0, stats.framesPerSecond, 0.5)
    }

    @Test
    fun the_largest_angle_change_is_the_worst_side_over_the_last_second() {
        val stats = PhaseBStats(required)
        val still = frame(0.9, 0.9)
        stats.onFrame(0, still, 90.0, 90.0)
        stats.onFrame(33, still, 95.0, 118.0) // right moved 28 degrees in one frame
        stats.onFrame(66, still, 97.0, 119.0)
        assertEquals(28.0, stats.largestAngleChange, 1e-9)

        // A second later that frame has left the window.
        for (i in 3..40) stats.onFrame(i * 33L, still, 97.0, 119.0)
        assertEquals(0.0, stats.largestAngleChange, 1e-9)
    }

    /** An unseen frame breaks the chain: the next change is not measured across the gap. */
    @Test
    fun a_frame_with_nobody_in_it_breaks_the_change_chain() {
        val stats = PhaseBStats(required)
        val still = frame(0.9, 0.9)
        stats.onFrame(0, still, 90.0, 90.0)
        stats.onNoPose(33)
        stats.onFrame(66, still, 150.0, 150.0)
        assertEquals(0.0, stats.largestAngleChange.takeIf { !it.isNaN() } ?: 0.0, 1e-9)
    }

    @Test
    fun visibility_spans_only_the_profiles_required_landmarks() {
        val stats = PhaseBStats(required)
        stats.onFrame(0, frame(0.35, 0.92), 90.0, 90.0)
        assertEquals(0.35, stats.minVisibility, 1e-12)
        assertEquals(0.92, stats.maxVisibility, 1e-12)
        stats.onNoPose(33)
        assertTrue(stats.minVisibility.isNaN() && stats.maxVisibility.isNaN())
    }

    @Test
    fun the_readout_is_published_about_once_a_second_and_names_the_camera() {
        val published = ArrayList<String>()
        val probe = DebugPhaseBProbe(required, CameraFacing.BACK) { published += it }
        val still = frame(0.9, 0.9)
        for (i in 0 until 300) probe.onFrame(i * 33L, still, 90.0, 90.0) // ~10 s

        assertTrue("published " + published.size + " times in 10 s", published.size in 9..11)
        assertTrue(published.last(), published.last().contains("BACK"))
        assertTrue(published.last(), published.last().contains("fps"))
    }

    /** CLAUDE.md section 6: the per-frame path allocates nothing. */
    @Test
    fun a_frame_allocates_nothing() {
        val stats = PhaseBStats(required)
        val still = frame(0.9, 0.9)
        var t = 0L
        val frames = {
            for (i in 0 until 50_000) {
                stats.onFrame(t, still, 90.0 + (i % 7), 91.0)
                t += 33
            }
        }
        frames() // warm up

        // One 16-byte object per frame would be 800,000 bytes over these 50,000
        // frames. The counter's own reflective reads cost a few bytes and vary a
        // little between calls (40 to 128 measured), so the bound is a total under
        // 1 KB: decisive against any per-frame allocation, tolerant of the counter.
        val measured = allocatedDuring(frames) ?: return
        assertTrue("50,000 frames allocated $measured bytes", measured < 1024)
    }

    /**
     * Bytes this thread allocated while running [body], from the JVM's own
     * counter, or `null` where the JVM has none. Reached by reflection because
     * `java.lang.management` is not on an Android unit test's compile classpath.
     */
    private fun allocatedDuring(body: () -> Unit): Long? {
        val bean = try {
            Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        } catch (error: ReflectiveOperationException) {
            return null
        }
        val sun = try {
            Class.forName("com.sun.management.ThreadMXBean")
        } catch (error: ClassNotFoundException) {
            return null
        }
        if (!sun.isInstance(bean)) return null
        sun.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType).invoke(bean, true)
        val read = sun.getMethod("getCurrentThreadAllocatedBytes")
        repeat(10) { read.invoke(bean) }

        val before = read.invoke(bean) as Long
        body()
        val after = read.invoke(bean) as Long
        return after - before
    }
}
