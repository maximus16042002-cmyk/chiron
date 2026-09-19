package kinesis.spike

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoldenTest {

    @Test
    fun goldenPipelineProducesExpectedDiscreteOutputs() {
        val result = runGoldenPipeline()
        // Printed so the JVM and JS runs can be diffed byte for byte.
        println("GOLDEN_RESULT :: $result")

        val reps = result.substringAfter("reps=").substringBefore("|").toInt()
        assertEquals(5, reps, "rep count must be exactly 5 on every target")

        val phases = result.substringAfter("phases=").substringBefore("|")
        // 5 reps x 4 phase transitions, minus the initial TOP we start in.
        assertEquals(19, phases.split(",").size, "phase transition count must match")
    }

    @Test
    fun continuousMetricsArePrintedAtFullPrecision() {
        val track = syntheticSquatTrack(reps = 1, perRep = 8)
        val angles = track.map { target ->
            val (h, k, a) = legLandmarks(target)
            jointAngleAtan2(h, k, a)
        }
        println("ANGLES_ATAN2 :: " + angles.joinToString(",") { it.toString() })

        val acosAngles = track.map { target ->
            val (h, k, a) = legLandmarks(target)
            jointAngleAcos(h, k, a)
        }
        println("ANGLES_ACOS :: " + acosAngles.joinToString(",") { it.toString() })

        // Round-trip fidelity: the recovered angle must match the target we built from.
        track.forEachIndexed { i, target ->
            assertTrue(
                kotlin.math.abs(angles[i] - target) < 1e-9,
                "angle round-trip failed at $i: got ${angles[i]} expected $target"
            )
        }
    }

    @Test
    fun bilateralSignalDoesNotCollapseImplicitly() {
        val s = BilateralSignal(left = 92.5, right = 88.25)
        assertEquals(4.25, s.asymmetry(), "asymmetry must preserve sign and magnitude")
        assertEquals(90.375, s.meanExplicit(), "reduction must be explicit and named")
        // There is no implicit conversion from BilateralSignal to Double by design.
    }

    @Test
    fun facadeCountsRepsThroughPrimitiveBoundary() {
        val engine = SpikeEngine()
        val track = syntheticSquatTrack(reps = 3, perRep = 40)
        for (target in track) {
            val (h, k, a) = legLandmarks(target)
            engine.pushFrame(doubleArrayOf(h.x, h.y, k.x, k.y, a.x, a.y))
        }
        println("FACADE_REPS :: ${engine.repCount()} phase=${engine.phaseName()}")
        assertEquals(3, engine.repCount(), "facade rep count must match across targets")
    }
}
