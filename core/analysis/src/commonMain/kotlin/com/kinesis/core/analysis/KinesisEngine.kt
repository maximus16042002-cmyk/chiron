@file:OptIn(ExperimentalJsExport::class)

package com.kinesis.core.analysis

import com.kinesis.core.analysis.calibration.BaselineJson
import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.calibration.PostRepAnalysis
import com.kinesis.core.analysis.live.LiveHoldAnalysis
import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.rep.RepSink
import com.kinesis.core.domain.analysis.AnalysisResult
import com.kinesis.core.domain.analysis.BesideBaseline
import com.kinesis.core.domain.analysis.HoldAnalysisResult
import com.kinesis.core.domain.analysis.RepetitionAnalysisResult
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The engine facade - CONTRACTS.md section 5, DECISIONS.md P0-2 and P3-19.
 *
 * The only `@JsExport` surface in the project. It lives in `commonMain`, not
 * `jsMain`, so that the JVM runs the very same facade as JavaScript: that is what
 * the golden-file parity suite compares (`EngineParityTest`). `@JsExport` is
 * ignored on every target but JS.
 *
 * **The boundary rule.** Everything crossing it is a primitive, a `DoubleArray`,
 * or a JSON string. No Kotlin collections, no sealed classes, no data classes,
 * no nullable rich types. A measured value that is `NaN` inside Kotlin - not
 * measured - is JSON `null`, since JSON has no `NaN`.
 *
 * **What it runs.** The same live analysers the Android app runs:
 * `LiveRepetitionAnalysis` for a repetition exercise, `LiveHoldAnalysis` for a
 * hold. It adds nothing analytical of its own - CLAUDE.md section 2.
 *
 * **Calibration (P6, DECISIONS.md P3-22).** [computeBaseline] and [analyzeRep]
 * run the core's own calibrator and post-rep analysis - the ones the app runs -
 * on records in the JSON [pushFrame] and [finishSet] already return. With them
 * the facade is complete as CONTRACTS.md section 5 specifies it.
 */
@JsExport
class KinesisEngine {

    private var repetition: LiveRepetitionAnalysis? = null
    private var hold: LiveHoldAnalysis? = null
    private var lastTimestampMs = Double.NaN

    /** Records completed by the frame or call in progress. Cleared each time. */
    private val completed = ArrayList<RepRecord>(2)
    private val sink = RepSink { completed += it }

    /**
     * Selects the exercise to analyse, as JSON: `{"id": "squat"}`, any id in the
     * exercise library, variants included. Starts a new set; anything in progress
     * is discarded without a record.
     */
    fun configure(profileJson: String) {
        val id = EngineJson.readId(profileJson)
        val profile = ExerciseLibrary.all.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("no exercise with id \"$id\" in the library")
        repetition = null
        hold = null
        lastTimestampMs = Double.NaN
        when (profile) {
            is RepetitionExercise -> repetition = LiveRepetitionAnalysis(profile, onRepetition = sink)
            is IsometricExercise -> hold = LiveHoldAnalysis(profile)
            else -> throw IllegalArgumentException("\"$id\" is neither a repetition nor a hold profile")
        }
    }

    /**
     * Feeds one frame of pose into the real-time path.
     *
     * @param timestampMs frame time in milliseconds. `Double`, not `Long`:
     *   CLAUDE.md section 5 keeps `Long` out of analysis paths where JS
     *   compatibility matters.
     * @param landmarks 99 doubles, `x, y, z` per landmark in MediaPipe order.
     *   **Anatomical already** - the caller's pose adapter has converted camera
     *   coordinates to anatomical left and right before this point, and the core
     *   neither knows nor asks which camera produced the frame.
     * @param visibility 33 doubles, one per landmark, in 0..1.
     *
     * @return `null` on the overwhelming majority of frames. On a frame that
     *   completes repetition records, a JSON **array** of them - usually one, but
     *   one frame can complete two: a half-seen repetition reported alone, then a
     *   paired one (DECISIONS.md P3-19). A hold reports only at [finishSet].
     */
    fun pushFrame(timestampMs: Double, landmarks: DoubleArray, visibility: DoubleArray): String? {
        val frame = Landmarks.fromXyzAndVisibility(landmarks, visibility)
        lastTimestampMs = timestampMs
        val analysis = repetition
        if (analysis != null) {
            analysis.onFrame(timestampMs, frame)
            return drainRecords()
        }
        (hold ?: throw IllegalStateException("configure an exercise before pushing frames")).onFrame(timestampMs, frame)
        return null
    }

    /**
     * The discrete state after the last frame, as JSON. For a repetition exercise:
     * `repCount`, each side's phase - and the display phase, the named reduction
     * the readout shows - the frame's validity and the last record's. For a hold:
     * the hold state, the time held and the frame's validity.
     */
    fun stateJson(): String {
        val analysis = repetition
        if (analysis != null) {
            return EngineJson.obj {
                field("kind", "repetition")
                field("repCount", analysis.repCount)
                field("leftPhase", analysis.leftPhase.name)
                field("rightPhase", analysis.rightPhase.name)
                field("displayPhase", analysis.displayPhase.name)
                field("frameValidity", analysis.frameValidity.name)
                field("lastRepValidity", analysis.lastRepValidity?.name)
            }
        }
        val holding = hold ?: throw IllegalStateException("configure an exercise first")
        return EngineJson.obj {
            field("kind", "hold")
            field("holdState", holding.holdState.name)
            field("heldMs", holding.heldMs)
            field("frameValidity", holding.frameValidity.name)
        }
    }

    /**
     * Ends the set. For a repetition exercise, a JSON array of whatever it
     * completes - a repetition still under way, reported `INCOMPLETE`, or a
     * half-seen one still waiting for its other side - possibly empty. For a
     * hold, a JSON array holding its one `HoldRecord`.
     */
    fun finishSet(): String {
        val analysis = repetition
        if (analysis != null) {
            analysis.finish(lastTimestampMs)
            return drainRecords() ?: "[]"
        }
        val holding = hold ?: throw IllegalStateException("configure an exercise first")
        return "[" + EngineJson.hold(holding.finish()) + "]"
    }

    /**
     * Fits a personal baseline to a calibration set of the configured exercise -
     * DECISIONS.md P3-22.
     *
     * @param repsJson a JSON array of the records the set produced, exactly as
     *   [pushFrame] and [finishSet] returned them: repetition records for a
     *   repetition exercise, the hold record for a hold.
     * @return a JSON outcome. `{"outcome":"calibrated","baseline":{...},...}`
     *   carries the baseline; `tooFewRepetitions` and `holdNotUsable` carry the
     *   counts and the plain reason there is none. Not having a baseline is an
     *   answer, not an exception. The facade has no clock and no session, so the
     *   baseline's `createdAtEpochMs` and `sourceSessionId` are `null`; a platform
     *   that stores it stamps its own.
     */
    fun computeBaseline(repsJson: String): String {
        val profile = configuredProfile()
        val records = JsonText.parse(repsJson)
        require(records is List<*>) { "computeBaseline takes a JSON array of records" }
        val outcome = when (profile) {
            is RepetitionExercise ->
                Calibration.fromRepetitions(profile, records.map { EngineJson.readRep(it) }, Double.NaN, null)
            is IsometricExercise -> {
                val holds = records.map { EngineJson.readHold(it) }
                val hold = holds.firstOrNull { it.validity == HoldValidity.VALID } ?: holds.firstOrNull()
                Calibration.fromHold(profile, hold, Double.NaN, null)
            }
            else -> throw IllegalStateException("configure an exercise first")
        }
        return EngineJson.outcome(outcome)
    }

    /**
     * Runs the post-rep analysis on one completed record of the configured
     * exercise - DECISIONS.md P3-22.
     *
     * @param repJson one record, as [pushFrame] or [finishSet] returned it.
     * @param baselineJson a baseline, as the `baseline` member of
     *   [computeBaseline]'s outcome, or `null` for none.
     * @return a JSON `AnalysisResult`: the record's values beside the baseline's,
     *   and a `findings` array that is empty until P8's rules exist.
     */
    fun analyzeRep(repJson: String, baselineJson: String): String {
        val profile = configuredProfile()
        val record = JsonText.parse(repJson)
        val baseline = if (JsonText.parse(baselineJson) == null) null else BaselineJson.read(baselineJson)
        val result: AnalysisResult = when (profile) {
            is RepetitionExercise -> {
                require(baseline == null || baseline is RepetitionBaseline) { "a hold baseline cannot analyse a repetition" }
                PostRepAnalysis.analyse(profile, EngineJson.readRep(record), baseline as RepetitionBaseline?)
            }
            is IsometricExercise -> {
                require(baseline == null || baseline is HoldBaseline) { "a repetition baseline cannot analyse a hold" }
                PostRepAnalysis.analyse(profile, EngineJson.readHold(record), baseline as HoldBaseline?)
            }
            else -> throw IllegalStateException("configure an exercise first")
        }
        return EngineJson.analysis(result)
    }

    private fun configuredProfile() =
        repetition?.profile ?: hold?.profile ?: throw IllegalStateException("configure an exercise first")

    private fun drainRecords(): String? {
        if (completed.isEmpty()) return null
        val json = completed.joinToString(",", "[", "]") { EngineJson.rep(it) }
        completed.clear()
        return json
    }

}

/**
 * The facade's JSON, written and read by hand: the core takes no JSON library,
 * because it needs one field in and a handful of flat records out. `NaN` and the
 * infinities are written as `null`.
 */
internal object EngineJson {

    fun rep(record: RepRecord): String = obj {
        field("repIndex", record.repIndex)
        field("attributedSide", record.attributedSide?.name)
        field("startMs", record.startMs)
        field("endMs", record.endMs)
        field("validity", record.validity.name)
        bilateral("rangeOfMotion", record.rangeOfMotion)
        bilateral("peakValue", record.peakValue)
        bilateral("troughValue", record.troughValue)
        field("descentMs", record.descentMs)
        field("ascentMs", record.ascentMs)
        array("resampledLeft", record.resampledLeft)
        array("resampledRight", record.resampledRight)
    }

    fun hold(record: HoldRecord): String = obj {
        field("startMs", record.startMs)
        field("endMs", record.endMs)
        field("validity", record.validity.name)
        field("totalHoldMs", record.totalHoldMs)
        field("longestUnbrokenMs", record.longestUnbrokenMs)
        field("breakCount", record.breakCount)
        field("meanAbsDeviation", record.meanAbsDeviation)
        field("maxAbsDeviation", record.maxAbsDeviation)
        field("coverage", record.coverage)
        // DECISIONS.md P3-22: where the hold was held, and how steadily.
        field("meanHeldValue", record.meanHeldValue)
        field("heldValueSd", record.heldValueSd)
    }

    // --- Reading back what the facade writes - DECISIONS.md P3-22 -----------------

    /** A repetition record, as [rep] writes it. */
    fun readRep(value: Any?): RepRecord {
        val map = JsonText.obj(value, "a repetition record")
        return RepRecord(
            repIndex = JsonText.int(map, "repIndex"),
            attributedSide = JsonText.stringOrNull(map, "attributedSide")?.let { named(Side.entries, it) },
            startMs = JsonText.double(map, "startMs"),
            endMs = JsonText.double(map, "endMs"),
            validity = named(RepValidity.entries, JsonText.string(map, "validity")),
            rangeOfMotion = readBilateral(map, "rangeOfMotion"),
            peakValue = readBilateral(map, "peakValue"),
            troughValue = readBilateral(map, "troughValue"),
            descentMs = JsonText.double(map, "descentMs"),
            ascentMs = JsonText.double(map, "ascentMs"),
            resampledLeft = JsonText.doubles(map, "resampledLeft"),
            resampledRight = JsonText.doubles(map, "resampledRight"),
        )
    }

    /** A hold record, as [hold] writes it. */
    fun readHold(value: Any?): HoldRecord {
        val map = JsonText.obj(value, "a hold record")
        return HoldRecord(
            startMs = JsonText.double(map, "startMs"),
            endMs = JsonText.double(map, "endMs"),
            validity = named(HoldValidity.entries, JsonText.string(map, "validity")),
            totalHoldMs = JsonText.double(map, "totalHoldMs"),
            longestUnbrokenMs = JsonText.double(map, "longestUnbrokenMs"),
            breakCount = JsonText.int(map, "breakCount"),
            meanAbsDeviation = JsonText.double(map, "meanAbsDeviation"),
            maxAbsDeviation = JsonText.double(map, "maxAbsDeviation"),
            coverage = JsonText.double(map, "coverage"),
            meanHeldValue = JsonText.double(map, "meanHeldValue"),
            heldValueSd = JsonText.double(map, "heldValueSd"),
        )
    }

    private fun readBilateral(map: Map<String, Any?>, key: String): BilateralSignal {
        val sides = JsonText.obj(JsonText.field(map, key), key)
        return BilateralSignal(JsonText.double(sides, "left"), JsonText.double(sides, "right"))
    }

    /**
     * An enum constant by name, or an [IllegalArgumentException]. Not `valueOf`,
     * whose failure is not the same exception on every target.
     */
    private fun <E : Enum<E>> named(entries: List<E>, name: String): E =
        entries.firstOrNull { it.name == name } ?: throw IllegalArgumentException("unknown value: " + name)

    // --- Calibration outcomes and analysis results --------------------------------

    fun outcome(outcome: CalibrationOutcome): String = obj {
        when (outcome) {
            is CalibrationOutcome.Calibrated -> {
                field("outcome", "calibrated")
                raw("baseline", BaselineJson.write(outcome.baseline))
            }
            is CalibrationOutcome.TooFewRepetitions -> {
                field("outcome", "tooFewRepetitions")
                field("validLeft", outcome.validLeft)
                field("validRight", outcome.validRight)
                field("requiredPerSide", outcome.requiredPerSide)
                field("oneSideSuffices", outcome.oneSideSuffices)
            }
            is CalibrationOutcome.HoldNotUsable -> {
                field("outcome", "holdNotUsable")
                field("validity", outcome.validity?.name)
                field("heldMs", outcome.heldMs)
                field("requiredMs", outcome.requiredMs)
            }
        }
        field("displayText", outcome.displayText)
    }

    fun analysis(result: AnalysisResult): String = obj {
        field("kind", if (result is RepetitionAnalysisResult) "repetition" else "hold")
        field("exerciseId", result.exerciseId)
        if (result is RepetitionAnalysisResult) field("repIndex", result.repIndex)
        field("analysisVersion", result.analysisVersion)
        val reference = result.baseline
        if (reference == null) {
            field("baseline", null as String?)
        } else {
            raw(
                "baseline",
                obj {
                    field("calibrationId", reference.calibrationId)
                    field("estimatorVersion", reference.estimatorVersion)
                    field("createdAtEpochMs", reference.createdAtEpochMs)
                },
            )
        }
        when (result) {
            is RepetitionAnalysisResult -> {
                val comparison = result.comparison
                if (comparison == null) {
                    field("comparison", null as String?)
                } else {
                    raw(
                        "comparison",
                        obj {
                            raw("rangeOfMotionLeft", beside(comparison.rangeOfMotionLeft))
                            raw("rangeOfMotionRight", beside(comparison.rangeOfMotionRight))
                            field("timingSide", comparison.timingSide?.name)
                            raw("descentMs", beside(comparison.descentMs))
                            raw("ascentMs", beside(comparison.ascentMs))
                        },
                    )
                }
            }
            is HoldAnalysisResult -> {
                val comparison = result.comparison
                if (comparison == null) {
                    field("comparison", null as String?)
                } else {
                    raw(
                        "comparison",
                        obj {
                            raw("meanHeldValue", beside(comparison.meanHeldValue))
                            raw("meanAbsDeviation", beside(comparison.meanAbsDeviation))
                            raw("sway", beside(comparison.sway))
                            raw("heldMs", beside(comparison.heldMs))
                        },
                    )
                }
            }
        }
        // Empty until P8: findings arrive with their rules. Each will carry its
        // claim level and rule version (FINAL_ARCHITECTURE section 8).
        raw("findings", "[]")
    }

    private fun beside(value: BesideBaseline): String = obj {
        field("measured", value.measured)
        field("baseline", value.baseline)
        field("baselineSpread", value.baselineSpread)
    }

    /** The `id` of `{"id": "..."}`; nothing else is read, and nothing else is accepted. */
    fun readId(json: String): String {
        val match = ID.matchEntire(json.trim())
            ?: throw IllegalArgumentException("expected {\"id\": \"<exercise id>\"}, got: $json")
        return match.groupValues[1]
    }

    // Both braces escaped: Kotlin/JS compiles regexes with JavaScript's `u` flag,
    // under which a bare `}` is a syntax error the JVM would accept. The parity
    // suite found it - configure() failed on JS before a frame. DECISIONS.md P3-19.
    private val ID = Regex("""\{\s*"id"\s*:\s*"([a-z0-9_]+)"\s*\}""")

    fun obj(body: Writer.() -> Unit): String {
        val writer = Writer()
        writer.body()
        return writer.close()
    }

    class Writer {
        private val out = StringBuilder("{")
        private var first = true

        private fun key(name: String) {
            if (!first) out.append(',')
            first = false
            out.append('"').append(name).append("\":")
        }

        fun field(name: String, value: String?) {
            key(name)
            if (value == null) out.append("null") else string(value)
        }

        /** An already-written JSON value - an object or array from another writer. */
        fun raw(name: String, json: String) {
            key(name)
            out.append(json)
        }

        fun field(name: String, value: Boolean) {
            key(name)
            out.append(if (value) "true" else "false")
        }

        /**
         * Quotes and backslashes escaped, so a string cannot break the document,
         * and control characters as `\u00XX` (P3-23 export). Ids, enum names and
         * session UUIDs contain none of these, so every output written before
         * either change is unchanged.
         */
        private fun string(value: String) {
            out.append('"')
            for (c in value) {
                when {
                    c == '"' -> out.append("\\\"")
                    c == '\\' -> out.append("\\\\")
                    c < ' ' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else -> out.append(c)
                }
            }
            out.append('"')
        }

        fun field(name: String, value: Int) {
            key(name)
            out.append(value)
        }

        fun field(name: String, value: Double) {
            key(name)
            number(value)
        }

        fun bilateral(name: String, value: BilateralSignal) {
            key(name)
            out.append("{\"left\":")
            number(value.left)
            out.append(",\"right\":")
            number(value.right)
            out.append('}')
        }

        fun array(name: String, values: DoubleArray) {
            key(name)
            out.append('[')
            for (i in values.indices) {
                if (i > 0) out.append(',')
                number(values[i])
            }
            out.append(']')
        }

        private fun number(value: Double) {
            if (value.isNaN() || value.isInfinite()) out.append("null") else out.append(value.toString())
        }

        fun close(): String = out.append('}').toString()
    }
}
