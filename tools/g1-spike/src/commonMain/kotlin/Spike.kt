@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package kinesis.spike

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * G1 multiplatform viability spike.
 *
 * This is NOT the production core. It is a structurally representative slice:
 * bilateral signal shape, a three-point joint angle computed through the
 * trigonometric functions FAD section 3 flags as JS/JVM divergence risks
 * (sin, cos, sqrt, atan2, acos), and a hysteresis repetition FSM.
 *
 * Purpose: prove that the same Kotlin compiles to JVM and JS and produces
 * identical discrete outputs with continuous metrics inside tolerance.
 */

// ---------------------------------------------------------------------------
// Bilateral signal — left and right never collapse implicitly.
// ---------------------------------------------------------------------------

data class BilateralSignal(val left: Double, val right: Double) {
    /** Explicit, named reducers only. There is deliberately no implicit mean. */
    fun meanExplicit(): Double = (left + right) / 2.0
    fun asymmetry(): Double = left - right
}

// ---------------------------------------------------------------------------
// Geometry
// ---------------------------------------------------------------------------

data class Point2(val x: Double, val y: Double)

/**
 * Interior angle at [b] formed by a-b-c, in degrees, via atan2 of the
 * cross/dot pair. Preferred over acos for conditioning near 0 and 180.
 */
fun jointAngleAtan2(a: Point2, b: Point2, c: Point2): Double {
    val v1x = a.x - b.x
    val v1y = a.y - b.y
    val v2x = c.x - b.x
    val v2y = c.y - b.y
    val cross = v1x * v2y - v1y * v2x
    val dot = v1x * v2x + v1y * v2y
    val rad = atan2(abs(cross), dot)
    return rad * 180.0 / kotlin.math.PI
}

/** Deliberate second path through acos+sqrt to widen the numeric surface under test. */
fun jointAngleAcos(a: Point2, b: Point2, c: Point2): Double {
    val v1x = a.x - b.x
    val v1y = a.y - b.y
    val v2x = c.x - b.x
    val v2y = c.y - b.y
    val n1 = sqrt(v1x * v1x + v1y * v1y)
    val n2 = sqrt(v2x * v2x + v2y * v2y)
    val cosine = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1.0, 1.0)
    return acos(cosine) * 180.0 / kotlin.math.PI
}

// ---------------------------------------------------------------------------
// Repetition FSM — hysteresis band, causal, O(1) per frame.
// ---------------------------------------------------------------------------

enum class RepPhase { TOP, DESCENDING, BOTTOM, ASCENDING }

class RepFsm(
    private val bottomEnter: Double,
    private val topEnter: Double,
) {
    var phase: RepPhase = RepPhase.TOP
        private set
    var repCount: Int = 0
        private set

    private val transitions = mutableListOf<RepPhase>()
    fun phaseSequence(): List<RepPhase> = transitions.toList()

    /** One frame in, O(1), no history iteration, no allocation beyond the transition log. */
    fun step(angle: Double) {
        val next = when (phase) {
            RepPhase.TOP -> if (angle < topEnter) RepPhase.DESCENDING else RepPhase.TOP
            RepPhase.DESCENDING -> if (angle <= bottomEnter) RepPhase.BOTTOM else RepPhase.DESCENDING
            RepPhase.BOTTOM -> if (angle > bottomEnter) RepPhase.ASCENDING else RepPhase.BOTTOM
            RepPhase.ASCENDING -> if (angle >= topEnter) RepPhase.TOP else RepPhase.ASCENDING
        }
        if (next != phase) {
            if (phase == RepPhase.ASCENDING && next == RepPhase.TOP) repCount++
            phase = next
            transitions.add(next)
        }
    }
}

// ---------------------------------------------------------------------------
// Synthetic landmark generator — deterministic, shared by both targets.
// ---------------------------------------------------------------------------

/**
 * Builds hip/knee/ankle positions whose interior knee angle traces [targetDeg].
 * Exercises sin and cos on the way in, atan2/acos on the way out.
 */
fun legLandmarks(targetDeg: Double): Triple<Point2, Point2, Point2> {
    val knee = Point2(0.0, 0.0)
    val ankle = Point2(0.0, -1.0)
    val rad = targetDeg * kotlin.math.PI / 180.0
    // Rotate the thigh vector away from the shank by the target interior angle.
    val hip = Point2(sin(rad) * 1.0, -cos(rad) * 1.0)
    return Triple(hip, knee, ankle)
}

/** Deterministic squat-like knee-angle track: [reps] cycles, [perRep] frames each. */
fun syntheticSquatTrack(reps: Int, perRep: Int): DoubleArray {
    val out = DoubleArray(reps * perRep)
    var i = 0
    for (r in 0 until reps) {
        for (f in 0 until perRep) {
            val t = f.toDouble() / perRep.toDouble()
            // 170 deg standing down to 70 deg bottom and back.
            val a = 120.0 - 50.0 * cos(2.0 * kotlin.math.PI * t)
            out[i++] = a
        }
    }
    return out
}

// ---------------------------------------------------------------------------
// The facade shape FAD section 3 specifies: primitives, DoubleArray, JSON only.
// ---------------------------------------------------------------------------

@JsExport
class SpikeEngine {
    private val fsm = RepFsm(bottomEnter = 100.0, topEnter = 150.0)
    private var lastAngle: Double = 0.0

    /** landmarks = [hipX, hipY, kneeX, kneeY, ankleX, ankleY] */
    fun pushFrame(landmarks: DoubleArray): Int {
        val hip = Point2(landmarks[0], landmarks[1])
        val knee = Point2(landmarks[2], landmarks[3])
        val ankle = Point2(landmarks[4], landmarks[5])
        lastAngle = jointAngleAtan2(hip, knee, ankle)
        fsm.step(lastAngle)
        return fsm.repCount
    }

    fun currentAngle(): Double = lastAngle
    fun repCount(): Int = fsm.repCount
    fun phaseName(): String = fsm.phase.name
}

/** Runs the whole deterministic pipeline and returns a compact result string. */
fun runGoldenPipeline(): String {
    val track = syntheticSquatTrack(reps = 5, perRep = 40)
    val fsm = RepFsm(bottomEnter = 100.0, topEnter = 150.0)
    var angleSum = 0.0
    var acosSum = 0.0
    for (target in track) {
        val (hip, knee, ankle) = legLandmarks(target)
        val a1 = jointAngleAtan2(hip, knee, ankle)
        val a2 = jointAngleAcos(hip, knee, ankle)
        angleSum += a1
        acosSum += a2
        fsm.step(a1)
    }
    val phases = fsm.phaseSequence().joinToString(",") { it.name.first().toString() }
    return "reps=${fsm.repCount}|phases=$phases|atan2Sum=$angleSum|acosSum=$acosSum"
}
