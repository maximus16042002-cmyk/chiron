@file:OptIn(ExperimentalJsExport::class)

package com.kinesis.core.analysis

import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.calibration.PersonalBands
import com.kinesis.core.analysis.live.LiveHoldAnalysis
import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.rep.RepSink
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.calibration.AnalysisResult
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldAnalysisResult
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepAnalysisResult
import com.kinesis.core.domain.calibration.RepTiming
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustSummary
import com.kinesis.core.domain.calibration.TimingBaseline
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.RepRecord
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
 * **Calibration (P6).** [computeBaseline] fits a personal baseline from
 * records, and [analyzeRep] places one record against it - DECISIONS.md P3-21.
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
     * Fits a personal baseline from calibration records - DECISIONS.md P3-21.
     *
     * [repsJson] is a JSON array of records in the shape [pushFrame] and
     * [finishSet] return them - repetitions for a repetition exercise, holds for
     * a hold - for the exercise last passed to [configure]. Invalid records are
     * accepted and left out of the fit, as `Calibration` does.
     *
     * @return `{"status":"fitted","baseline":{...}}`, or
     *   `{"status":"insufficient", ...}` with the counts, when no side has enough
     *   valid records. Never a weak baseline presented as an established one.
     */
    fun computeBaseline(repsJson: String): String {
        val analysis = repetition
        val outcome = if (analysis != null) {
            Calibration.fitRepetitions(analysis.profile, EngineJsonReader.reps(repsJson))
        } else {
            val holding = hold ?: throw IllegalStateException("configure an exercise first")
            Calibration.fitHolds(holding.profile, EngineJsonReader.holds(repsJson))
        }
        return when (outcome) {
            is CalibrationOutcome.Fitted -> EngineJson.obj {
                field("status", "fitted")
                raw("baseline", EngineJson.baseline(outcome.baseline))
            }
            is CalibrationOutcome.Insufficient -> EngineJson.obj {
                field("status", "insufficient")
                field("recordsOffered", outcome.recordsOffered)
                field("bestSideSamples", outcome.bestSideSamples)
                field("minimumSamples", outcome.minimumSamples)
            }
        }
    }

    /**
     * Places one completed record against a personal baseline, as a JSON
     * `AnalysisResult` - DECISIONS.md P3-21.
     *
     * [repJson] is one record as [pushFrame] returns it - or, for a hold, as
     * [finishSet] does: the facade keeps CONTRACTS.md section 5's name for both.
     * [baselineJson] is the `baseline` object [computeBaseline] returned. The
     * result places each metric in, below or above the user's own band; it
     * writes no sentences - that is P8's deviation rules.
     *
     * @throws IllegalArgumentException if the baseline is for another exercise
     *   than the configured one. A variant's parent's baseline is its own.
     */
    fun analyzeRep(repJson: String, baselineJson: String): String {
        val baseline = EngineJsonReader.baseline(baselineJson)
        val analysis = repetition
        if (analysis != null) {
            val repBaseline = baseline as? RepetitionBaseline
                ?: throw IllegalArgumentException("a repetition needs a repetition baseline")
            return EngineJson.analysis(PersonalBands.analyseRep(analysis.profile, EngineJsonReader.rep(repJson), repBaseline))
        }
        val holding = hold ?: throw IllegalStateException("configure an exercise first")
        val holdBaseline = baseline as? HoldBaseline
            ?: throw IllegalArgumentException("a hold needs a hold baseline")
        return EngineJson.analysis(PersonalBands.analyseHold(holding.profile, EngineJsonReader.hold(repJson), holdBaseline))
    }

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
    }

    fun baseline(baseline: CalibrationBaseline): String = obj {
        field("kind", if (baseline is RepetitionBaseline) "repetition" else "hold")
        field("exerciseId", baseline.exerciseId)
        field("methodVersion", baseline.methodVersion)
        field("minimumSamples", baseline.minimumSamples)
        field("recordsOffered", baseline.recordsOffered)
        field("recordsUsed", baseline.recordsUsed)
        when (baseline) {
            is RepetitionBaseline -> {
                raw("rangeOfMotion", obj {
                    raw("left", summary(baseline.rangeOfMotion.left))
                    raw("right", summary(baseline.rangeOfMotion.right))
                })
                raw("timing", when (val timing = baseline.timing) {
                    is TimingBaseline.Together -> obj {
                        field("kind", "together")
                        raw("both", timing(timing.both))
                    }
                    is TimingBaseline.PerSide -> obj {
                        field("kind", "perSide")
                        raw("left", timing(timing.left))
                        raw("right", timing(timing.right))
                    }
                })
                raw("habitualOffset", summary(baseline.habitualOffset))
            }
            is HoldBaseline -> {
                raw("totalHoldMs", summary(baseline.totalHoldMs))
                raw("longestUnbrokenMs", summary(baseline.longestUnbrokenMs))
                raw("meanAbsDeviation", summary(baseline.meanAbsDeviation))
            }
        }
    }

    fun analysis(result: AnalysisResult): String = obj {
        when (result) {
            is RepAnalysisResult -> {
                field("kind", "repetition")
                field("repIndex", result.repIndex)
            }
            is HoldAnalysisResult -> {
                field("kind", "hold")
                field("startMs", result.startMs)
            }
        }
        field("exerciseId", result.exerciseId)
        field("baselineVersion", result.baselineVersion)
        field("ruleVersion", result.ruleVersion)
        raw("placements", result.placements.joinToString(",", "[", "]") { placement ->
            obj {
                field("metric", placement.metric)
                field("side", placement.side?.name)
                field("value", placement.value)
                field("lower", placement.lower)
                field("upper", placement.upper)
                field("position", placement.position.name)
            }
        })
    }

    private fun summary(summary: RobustSummary): String = obj {
        field("n", summary.n)
        field("median", summary.median)
        field("q1", summary.q1)
        field("q3", summary.q3)
    }

    private fun timing(timing: RepTiming): String = obj {
        raw("descentMs", summary(timing.descentMs))
        raw("ascentMs", summary(timing.ascentMs))
        raw("durationMs", summary(timing.durationMs))
        raw("tempoRatio", summary(timing.tempoRatio))
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
            if (value == null) out.append("null") else out.append('"').append(value).append('"')
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

        /** [json] written as is: a nested object or array this writer produced. */
        fun raw(name: String, json: String) {
            key(name)
            out.append(json)
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
