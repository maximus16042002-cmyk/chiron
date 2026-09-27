package com.kinesis.core.analysis.parity

import com.kinesis.core.analysis.EngineJson
import com.kinesis.core.analysis.JsonText
import com.kinesis.core.analysis.KinesisEngine
import com.kinesis.core.analysis.export.SessionExport
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The golden-file parity suite's scenarios and its comparison - DECISIONS.md
 * P3-19, CONTRACTS.md section 6.
 *
 * Each scenario is a synthetic landmark sequence fed through the
 * [KinesisEngine] facade exactly as a platform would feed it: 99 coordinates
 * and 33 visibilities a frame. What the facade says back becomes a list of
 * event lines:
 *
 * - `state|<frame>|<json>` whenever the discrete state changes - rep count,
 *   either side's phase, the frame's or the last record's validity, the hold
 *   state. The JSON carries the continuous `heldMs` too, as it stood then.
 * - `records|<frame>|<json>` whenever a frame completes records.
 * - `finish|<json>` for what ending the set reports.
 *
 * The JVM wrote the golden lines once (`EngineParityGoldenWriter`); every
 * target is compared against them. Kotlin's `Random(seed)` is the same
 * generator on every platform, so the jitter below - which makes the outlier
 * gate, the smoother and the resampler do real floating-point work - is too.
 */
object EngineParity {

    const val FRAME_MS = 33.0
    private const val STANDING = 170.0
    private const val FLAT = 180.0

    fun squat(): List<String> {
        val run = Run("""{"id": "squat"}""", seed = 7)
        run.stand(20)
        run.squatTo(125.0) // turns back above depth: ABORTED
        run.stand(15)
        repeat(3) {
            run.squatTo(70.0) // VALID
            run.stand(12)
        }
        run.squatTo(72.0, rightSeen = false) // the right knee unseen: one-sided
        run.stand(15)
        run.squatTo(70.0, dropoutAt = 22) // one unmeasurable frame mid-descent
        run.stand(15)
        run.squatTo(68.0, rightLag = 5) // the right knee five frames behind
        run.stand(15)
        run.squatTo(75.0, marginal = true) // landmarks only marginally visible
        run.stand(15)
        repeat(18) { run.legs(STANDING - 4.0 * it, STANDING - 4.0 * it) } // under way when the set ends
        return run.finish()
    }

    fun plank(): List<String> {
        val run = Run("""{"id": "plank"}""", seed = 11)
        repeat(30) { run.body(hipDegrees = 140.0 + it) } // coming up into position
        repeat(150) { run.body(hipDegrees = 178.0) } // held
        repeat(25) { run.body(hipDegrees = 150.0) } // sags out of the band
        repeat(20) { run.body(hipDegrees = 176.0) } // back into it
        repeat(6) { run.nobody() } // out of shot briefly
        repeat(120) { run.body(hipDegrees = 179.0) } // held again
        return run.finish()
    }

    // --- Calibration - DECISIONS.md P3-22 -----------------------------------------------

    /**
     * A calibration squat set through the facade: nine squats of varying depth,
     * one turned back early and one with a lagging knee. Then `computeBaseline`
     * over every record it produced, and `analyzeRep` over each record against
     * that baseline and once against none.
     */
    fun squatCalibration(): List<String> {
        val run = Run("""{"id": "squat"}""", seed = 13)
        run.stand(20)
        run.squatTo(128.0) // ABORTED: not used
        run.stand(12)
        for ((i, bottom) in listOf(72.0, 68.0, 75.0, 70.0, 66.0, 73.0, 69.0, 71.0, 74.0).withIndex()) {
            run.squatTo(bottom, rightLag = if (i == 4) 3 else 0)
            run.stand(12)
        }
        return run.calibrate()
    }

    /** Alternating curls, six on each arm: a baseline per arm, from each arm's own repetitions. */
    fun curlCalibration(): List<String> {
        val run = Run("""{"id": "bicep_curl"}""", seed = 19)
        run.arms(160.0, 160.0, frames = 20)
        for (i in 0 until 6) {
            run.curl(left = true, top = 158.0, bottom = 50.0 + i)
            run.arms(160.0, 160.0, frames = 8)
            run.curl(left = false, top = 162.0, bottom = 56.0 - i)
            run.arms(160.0, 160.0, frames = 8)
        }
        return run.calibrate()
    }

    /**
     * A plank held long enough to calibrate - about fourteen seconds with a
     * little sway - and, first, the short plank scenario's hold, which is not.
     */
    fun plankCalibration(): List<String> {
        val short = Run("""{"id": "plank"}""", seed = 11)
        repeat(150) { short.body(hipDegrees = 178.0) }
        val shortLines = short.calibrate().map { "short-" + it }

        val run = Run("""{"id": "plank"}""", seed = 17)
        repeat(30) { run.body(hipDegrees = 140.0 + it) } // coming up into position
        repeat(420) { run.body(hipDegrees = 176.0 + 1.5 * sin(it / 9.0)) } // held, swaying
        repeat(20) { run.body(hipDegrees = 150.0) } // a sag
        repeat(60) { run.body(hipDegrees = 177.0) } // held again
        return shortLines + run.calibrate()
    }

    // --- Export - DECISIONS.md P3-25 ---------------------------------------------------

    /**
     * P5's export over records the facade produced on this target: a squat set -
     * three valid, one turned back, one with a knee unseen - and a plank hold, in
     * one session, as the JSON document and as every CSV row.
     */
    fun sessionExport(): List<String> {
        val squats = Run("""{"id": "squat"}""", seed = 23)
        squats.stand(20)
        repeat(3) {
            squats.squatTo(70.0)
            squats.stand(12)
        }
        squats.squatTo(125.0) // turns back above depth: ABORTED
        squats.stand(12)
        squats.squatTo(72.0, rightSeen = false) // the right knee unseen: one-sided
        squats.stand(12)
        val reps = squats.allRecords().map { EngineJson.readRep(JsonText.parse(it)) }

        val plank = Run("""{"id": "plank"}""", seed = 29)
        repeat(30) { plank.body(hipDegrees = 140.0 + it) }
        repeat(200) { plank.body(hipDegrees = 177.0) }
        val holds = plank.allRecords().map { EngineJson.readHold(JsonText.parse(it)) }

        val session = SessionRecord(
            sessionId = "parity-export",
            startedAtEpochMs = 1_790_537_401_000.0,
            utcOffsetMinutes = 330,
            sets = listOf(
                RepetitionSetRecord("squat", 1_790_537_445_250.0, reps),
                HoldSetRecord("plank", 1_790_537_800_000.0, holds),
            ),
        )
        val csv = SessionExport.csv(listOf(session))
        return listOf("export|" + SessionExport.json(listOf(session), emptyList(), 1_790_600_000_000.0)) +
            csvLines("csv-sets", csv.sets) + csvLines("csv-reps", csv.reps) + csvLines("csv-holds", csv.holds)
    }

    /**
     * Each CSV row as a JSON array, so the comparison holds its numbers to the
     * same tolerance as the JSON's. The export quotes nothing here: no id in
     * this scenario has a comma or a quote.
     */
    private fun csvLines(kind: String, table: String): List<String> =
        table.removeSuffix("\r\n").split("\r\n").withIndex().map { (i, row) ->
            "$kind|$i|" + row.split(',').joinToString(",", "[", "]") { cell ->
                when {
                    cell.isEmpty() -> "null"
                    cell.toDoubleOrNull() != null -> cell
                    else -> "\"" + cell + "\""
                }
            }
        }

    /** One scenario in flight: the facade, the jitter and the event lines. */
    private class Run(profile: String, seed: Int) {
        private val engine = KinesisEngine().also { it.configure(profile) }
        private val random = Random(seed)
        private val lines = ArrayList<String>()
        private var frame = 0
        private var lastSignature: String? = null
        private val xyz = DoubleArray(3 * LandmarkId.COUNT)
        private val visibility = DoubleArray(LandmarkId.COUNT)

        fun stand(frames: Int) = repeat(frames) { legs(STANDING, STANDING) }

        fun squatTo(
            bottom: Double,
            rightSeen: Boolean = true,
            dropoutAt: Int = -1,
            rightLag: Int = 0,
            marginal: Boolean = false,
        ) {
            val steps = 60 + rightLag
            for (step in 0 until steps) {
                val left = cycle(step, bottom)
                val right = cycle(step - rightLag, bottom)
                legs(
                    if (step == dropoutAt) Double.NaN else left,
                    if (!rightSeen || step == dropoutAt) Double.NaN else right,
                    seen = if (marginal) 0.55 else 0.95,
                )
            }
        }

        /** 30 frames down to [bottom], 30 back up; standing outside that. */
        private fun cycle(step: Int, bottom: Double): Double = when {
            step < 0 -> STANDING
            step < 30 -> STANDING - (STANDING - bottom) * (step + 1) / 30.0
            step < 60 -> bottom + (STANDING - bottom) * (step - 29) / 30.0
            else -> STANDING
        }

        /** A standing body with exactly these knee angles; `NaN` hides that leg. */
        fun legs(leftKnee: Double, rightKnee: Double, seen: Double = 0.95) {
            clear(0.9)
            put(LandmarkId.NOSE, 0.50, 0.12, 0.9)
            put(LandmarkId.LEFT_SHOULDER, 0.42, 0.28, 0.95)
            put(LandmarkId.RIGHT_SHOULDER, 0.58, 0.28, 0.95)
            put(LandmarkId.LEFT_HIP, 0.44, 0.50, seen)
            put(LandmarkId.RIGHT_HIP, 0.56, 0.50, seen)
            leg(LandmarkId.LEFT_KNEE, LandmarkId.LEFT_ANKLE, 0.44, leftKnee, seen)
            leg(LandmarkId.RIGHT_KNEE, LandmarkId.RIGHT_ANKLE, 0.56, rightKnee, seen)
            push()
        }

        private fun leg(knee: LandmarkId, ankle: LandmarkId, x: Double, degrees: Double, seen: Double) {
            val hidden = degrees.isNaN()
            val radians = (if (hidden) STANDING else degrees) * PI / 180.0
            put(knee, x, 0.70, if (hidden) 0.0 else seen)
            put(ankle, x + 0.2 * sin(radians), 0.70 - 0.2 * cos(radians), if (hidden) 0.0 else seen)
        }

        /** A side-on plank whose hip angle - shoulder, hip, knee - is exactly [hipDegrees]. */
        fun body(hipDegrees: Double) {
            clear(0.9)
            for ((side, dy) in listOf("LEFT" to 0.0, "RIGHT" to 0.01)) {
                val hip = 0.50 to 0.55 + dy
                val radians = hipDegrees * PI / 180.0
                put(id(side, "HIP"), hip.first, hip.second, 0.95)
                put(id(side, "KNEE"), hip.first + 0.2, hip.second, 0.95)
                put(id(side, "ANKLE"), hip.first + 0.4, hip.second, 0.95)
                put(id(side, "SHOULDER"), hip.first + 0.3 * cos(radians), hip.second - 0.3 * sin(radians), 0.95)
            }
            push()
        }

        fun nobody() {
            for (i in xyz.indices) xyz[i] = Double.NaN
            for (i in visibility.indices) visibility[i] = 0.0
            push()
        }

        private fun id(side: String, part: String) = LandmarkId.valueOf(side + "_" + part)

        private fun clear(seen: Double) {
            for (id in LandmarkId.entries) put(id, 0.5, 0.5, seen)
        }

        /** Places a landmark with a little seeded jitter, as a pose estimator would. */
        private fun put(id: LandmarkId, x: Double, y: Double, seen: Double) {
            xyz[id.index * 3] = x + (random.nextDouble() - 0.5) * JITTER
            xyz[id.index * 3 + 1] = y + (random.nextDouble() - 0.5) * JITTER
            xyz[id.index * 3 + 2] = 0.0
            visibility[id.index] = seen
        }

        private fun push() {
            val completed = engine.pushFrame(frame * FRAME_MS, xyz, visibility)
            if (completed != null) {
                lines += "records|$frame|$completed"
                records += topLevelElements(completed)
            }
            val state = engine.stateJson()
            val signature = state.replace(HELD_MS, "")
            if (signature != lastSignature) {
                lines += "state|$frame|$state"
                lastSignature = signature
            }
            frame++
        }

        /** Every record the facade has returned, one JSON object each, for calibration. */
        private val records = ArrayList<String>()

        /** An upright body whose elbows bend to exactly these angles, for a front-on curl. */
        fun arms(leftElbow: Double, rightElbow: Double, frames: Int = 1) = repeat(frames) {
            clear(0.9)
            put(LandmarkId.NOSE, 0.50, 0.12, 0.9)
            arm(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST, 0.42, -1.0, leftElbow)
            arm(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST, 0.58, 1.0, rightElbow)
            put(LandmarkId.LEFT_HIP, 0.44, 0.55, 0.95)
            put(LandmarkId.RIGHT_HIP, 0.56, 0.55, 0.95)
            push()
        }

        private fun arm(shoulder: LandmarkId, elbow: LandmarkId, wrist: LandmarkId, x: Double, outward: Double, degrees: Double) {
            val radians = degrees * PI / 180.0
            put(shoulder, x, 0.28, 0.95)
            put(elbow, x, 0.45, 0.95)
            // The forearm at [degrees] from the upper arm, which hangs straight down.
            put(wrist, x + outward * 0.17 * sin(radians), 0.45 - 0.17 * cos(radians), 0.95)
        }

        /** One arm curls from [top] to [bottom] and back while the other rests at [top]. */
        fun curl(left: Boolean, top: Double, bottom: Double) {
            for (step in 0 until 50) {
                val angle = if (step < 25) top - (top - bottom) * (step + 1) / 25.0 else bottom + (top - bottom) * (step - 24) / 25.0
                if (left) arms(angle, 160.0) else arms(160.0, angle)
            }
        }

        /**
         * Ends the set, then fits a baseline to everything the set produced and
         * analyses each record against it, and one against no baseline.
         */
        fun calibrate(): List<String> {
            val finished = engine.finishSet()
            lines += "finish|$finished"
            records += topLevelElements(finished)
            val outcome = engine.computeBaseline(records.joinToString(",", "[", "]"))
            lines += "baseline|$outcome"
            val baseline = member(outcome, "baseline")
            if (baseline != null) {
                records.forEachIndexed { i, record -> lines += "analysis|$i|" + engine.analyzeRep(record, baseline) }
            }
            if (records.isNotEmpty()) lines += "analysis|none|" + engine.analyzeRep(records.first(), "null")
            return lines
        }

        fun finish(): List<String> {
            lines += "finish|" + engine.finishSet()
            return lines
        }

        /** Ends the set and returns every record it produced, one JSON object each. */
        fun allRecords(): List<String> {
            records += topLevelElements(engine.finishSet())
            return records
        }
    }

    /**
     * The top-level elements of a JSON array, as text. Test-only and independent
     * of the facade's own reader: it counts brackets, which is enough for records
     * whose strings never contain one.
     */
    fun topLevelElements(array: String): List<String> {
        val elements = ArrayList<String>()
        var depth = 0
        var start = -1
        for (i in array.indices) {
            when (array[i]) {
                '{', '[' -> {
                    depth++
                    if (depth == 2 && start < 0) start = i
                }
                '}', ']' -> {
                    depth--
                    if (depth == 1 && start >= 0) {
                        elements += array.substring(start, i + 1)
                        start = -1
                    }
                }
            }
        }
        return elements
    }

    /** The value of [key] in a JSON object, as text, or `null` if absent or null. */
    fun member(json: String, key: String): String? {
        val marker = "\"" + key + "\":"
        val at = json.indexOf(marker)
        if (at < 0) return null
        val start = at + marker.length
        if (json.startsWith("null", start)) return null
        return topLevelElements("[" + json.substring(start) + "]").firstOrNull()
    }

    private const val JITTER = 0.002
    private val HELD_MS = Regex(""""heldMs":[^,}]*""")

    // --- Comparison -----------------------------------------------------------------

    /**
     * Keys whose values are counts or indices: compared exactly, per CONTRACTS.md
     * section 6. Every other number is continuous and compared within
     * [RELATIVE_TOLERANCE].
     */
    private val DISCRETE_NUMBERS = setOf(
        "repIndex", "repCount", "breakCount",
        // DECISIONS.md P3-22: calibration counts are discrete too.
        "count", "validLeft", "validRight", "requiredPerSide",
        // DECISIONS.md P3-25: the export's counts and positions.
        "formatVersion", "utcOffsetMinutes", "setIndex", "recordCount", "validCount",
    )

    /**
     * CONTRACTS.md section 6: 1e-9 relative. Values that should be zero get an
     * absolute floor of 1e-12, far below anything an angle or a millisecond
     * could meaningfully differ by.
     */
    const val RELATIVE_TOLERANCE = 1e-9
    private const val ABSOLUTE_FLOOR = 1e-12

    /** Every difference between [expected] and [actual], or none. */
    fun differences(scenario: String, expected: List<String>, actual: List<String>): List<String> {
        val found = ArrayList<String>()
        if (expected.size != actual.size) {
            found += "$scenario: ${expected.size} event lines expected, ${actual.size} produced"
        }
        for (i in 0 until minOf(expected.size, actual.size)) {
            val (eKind, eFrame, eJson) = split(expected[i])
            val (aKind, aFrame, aJson) = split(actual[i])
            val where = "$scenario line $i"
            if (eKind != aKind || eFrame != aFrame) {
                found += "$where: expected $eKind at frame $eFrame, got $aKind at frame $aFrame"
                continue
            }
            compare(where, null, MiniJson.parse(eJson), MiniJson.parse(aJson), found)
        }
        return found
    }

    private fun split(line: String): Triple<String, String, String> {
        val kind = line.substringBefore('|')
        val rest = line.substringAfter('|')
        // `finish|` and `baseline|` lines carry no frame number; the others do.
        // No JSON the facade writes contains a '|'.
        return if ('|' !in rest) Triple(kind, "", rest) else Triple(kind, rest.substringBefore('|'), rest.substringAfter('|'))
    }

    private fun compare(where: String, key: String?, expected: Any?, actual: Any?, found: MutableList<String>) {
        when {
            expected is Map<*, *> && actual is Map<*, *> -> {
                if (expected.keys != actual.keys) found += "$where: keys ${expected.keys} vs ${actual.keys}"
                for (k in expected.keys) compare("$where.$k", k as String, expected[k], actual[k], found)
            }
            expected is List<*> && actual is List<*> -> {
                if (expected.size != actual.size) {
                    found += "$where: ${expected.size} elements vs ${actual.size}"
                } else {
                    for (i in expected.indices) compare("$where[$i]", key, expected[i], actual[i], found)
                }
            }
            expected is Double && actual is Double -> {
                if (key in DISCRETE_NUMBERS) {
                    if (expected != actual) found += "$where: $actual, expected exactly $expected"
                } else {
                    val scale = max(abs(expected), abs(actual))
                    if (abs(expected - actual) > max(RELATIVE_TOLERANCE * scale, ABSOLUTE_FLOOR)) {
                        found += "$where: $actual, expected $expected (relative difference ${abs(expected - actual) / scale})"
                    }
                }
            }
            else -> if (expected != actual) found += "$where: $actual, expected $expected"
        }
    }
}

/**
 * Just enough JSON to read the facade's output back: objects, arrays, strings
 * without escapes, numbers, `null`, `true` and `false`. Test-only.
 */
object MiniJson {

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipSpace()
        require(reader.done) { "trailing text at ${reader.at}: $text" }
        return value
    }

    private class Reader(val text: String) {
        var at = 0
        val done get() = at >= text.length

        fun skipSpace() {
            while (!done && text[at].isWhitespace()) at++
        }

        fun value(): Any? {
            skipSpace()
            return when (text[at]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                'n' -> literal("null", null)
                't' -> literal("true", true)
                'f' -> literal("false", false)
                else -> num()
            }
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            at++
            skipSpace()
            if (text[at] == '}') { at++; return map }
            while (true) {
                skipSpace()
                val key = str()
                skipSpace()
                require(text[at] == ':') { "expected : at $at" }
                at++
                map[key] = value()
                skipSpace()
                if (text[at] == ',') { at++; continue }
                require(text[at] == '}') { "expected } at $at" }
                at++
                return map
            }
        }

        private fun arr(): List<Any?> {
            val list = ArrayList<Any?>()
            at++
            skipSpace()
            if (text[at] == ']') { at++; return list }
            while (true) {
                list += value()
                skipSpace()
                if (text[at] == ',') { at++; continue }
                require(text[at] == ']') { "expected ] at $at" }
                at++
                return list
            }
        }

        private fun str(): String {
            require(text[at] == '"') { "expected a string at $at" }
            val end = text.indexOf('"', at + 1)
            val value = text.substring(at + 1, end)
            at = end + 1
            return value
        }

        private fun num(): Double {
            val start = at
            while (!done && (text[at].isDigit() || text[at] in "+-.eE")) at++
            return text.substring(start, at).toDouble()
        }

        private fun <T> literal(word: String, value: T): T {
            require(text.startsWith(word, at)) { "expected $word at $at" }
            at += word.length
            return value
        }
    }
}
