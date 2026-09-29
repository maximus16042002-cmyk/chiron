package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.live.LiveRepetitionAnalysis
import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepSignalSpec
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.temporal.TemporalConfig
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * The pieces of P7's MM-Fit harness - DECISIONS.md P3-27.
 *
 * The harness decides nothing about movement. It reads MediaPipe's landmarks (extract_landmarks.py)
 * and MM-Fit's labels, applies the phone adapter's frame-shape correction with MM-Fit's frame size,
 * and feeds the phone's own `LiveRepetitionAnalysis` frame by frame, exactly as the live screen does.
 */

/** MM-Fit's video rate. Its labels are frame numbers at this rate. */
const val MMFIT_FPS: Double = 30.0

fun frameTimeMs(frame: Int): Double = frame * 1000.0 / MMFIT_FPS

/** One labelled set of `wXX_labels.csv`: start frame, end frame, repetition count, exercise (P3-1). */
data class MmFitLabel(val setIndex: Int, val start: Int, val end: Int, val reps: Int, val exercise: String)

object MmFitLabels {

    /** In file order, numbered from 1: that number is the set's identity in everything written. */
    fun parse(text: String): List<MmFitLabel> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { i, line ->
            val fields = line.split(',').map { it.trim() }
            require(fields.size == 4) { "expected start,end,reps,exercise, got: $line" }
            MmFitLabel(i + 1, fields[0].toInt(), fields[1].toInt(), fields[2].toInt(), fields[3])
        }.toList()

    fun read(file: File): List<MmFitLabel> = parse(file.readText())
}

sealed interface ExerciseMapping {
    class Evaluated(val profile: RepetitionExercise) : ExerciseMapping
    class Excluded(val reason: String) : ExerciseMapping
}

/** MM-Fit's ten exercises against Kinesis's profiles. */
object MmFitExercises {

    private val PROFILES = mapOf(
        "pushups" to "pushup",
        "dumbbell_shoulder_press" to "shoulder_press",
        "lunges" to "lunge",
        "dumbbell_rows" to "row",
        "situps" to "situp",
        "bicep_curls" to "bicep_curl",
        "lateral_shoulder_raises" to "lateral_raise",
    )

    private val EXCLUDED = mapOf(
        "squats" to "excluded pending the squat's camera view: MM-Fit films squats face-on and the squat " +
            "profile requires side-on, so a count from this view is not evidence for it (CLAUDE.md section 22, " +
            "DECISIONS.md P3-27)",
        "tricep_extensions" to "no Kinesis profile",
        "jumping_jacks" to "no Kinesis profile",
    )

    fun of(name: String): ExerciseMapping {
        EXCLUDED[name]?.let { return ExerciseMapping.Excluded(it) }
        val id = PROFILES[name] ?: return ExerciseMapping.Excluded("unknown MM-Fit exercise: $name")
        val profile = ExerciseLibrary.all.single { it.id == id }
        check(profile is RepetitionExercise) { "$id is not a repetition exercise" }
        return ExerciseMapping.Evaluated(profile)
    }
}

/**
 * CONTRACTS.md section 4, with MM-Fit's frame size: the phone adapter's back-camera path. x as
 * MediaPipe gives it, y times height over width, z and visibility untouched. MM-Fit's video is not
 * mirrored - its OpenPose skeleton and MediaPipe agree on left and right (P3-27) - so the front
 * camera's unmirroring does not apply. `PoseAdapter.toAnatomical(raw, BACK, width, height)` is this,
 * pinned on the phone side by `AspectCorrectionRegressionTest`.
 */
object FrameShape {
    fun toLandmarks(raw: FloatArray, width: Int, height: Int): Landmarks {
        require(raw.size == Landmarks.PACKED_SIZE) { "expected ${Landmarks.PACKED_SIZE} values, got ${raw.size}" }
        require(width > 0 && height > 0) { "frame size must be positive, got ${width}x$height" }
        val heightInWidths = height.toDouble() / width
        val packed = DoubleArray(Landmarks.PACKED_SIZE) { raw[it].toDouble() }
        for (index in 0 until LandmarkId.COUNT) packed[index * Landmarks.STRIDE + 1] *= heightInWidths
        return Landmarks.fromPacked(packed)
    }
}

class LandmarkRecord(val setIndex: Int, val frame: Int, val posePresent: Boolean, val values: FloatArray)

class LandmarkData(val width: Int, val height: Int, val records: List<LandmarkRecord>) {
    private val bySet: Map<Int, Map<Int, LandmarkRecord>> =
        records.groupBy { it.setIndex }.mapValues { (_, list) -> list.associateBy { it.frame } }

    /** Every frame in the file, whichever set's run produced it. */
    private val byFrame: Map<Int, LandmarkRecord> = records.associateBy { it.frame }

    fun frames(setIndex: Int): Map<Int, LandmarkRecord> = bySet[setIndex].orEmpty()

    /**
     * A frame for a set: from that set's own run if it has it, otherwise from a neighbouring set's.
     * Where two sets are under 4 s apart their margins overlap, and extract_landmarks.py processes the
     * shared frames once, under the earlier set (P3-27). A frame is the same video frame either way.
     */
    fun frame(setIndex: Int, frame: Int): LandmarkRecord? = bySet[setIndex]?.get(frame) ?: byFrame[frame]
}

/** extract_landmarks.py's file: big-endian, `KMFL`, version 1. */
object LandmarkFile {
    private const val MAGIC = "KMFL"
    private const val VERSION = 1

    fun write(file: File, width: Int, height: Int, records: List<LandmarkRecord>) {
        DataOutputStream(BufferedOutputStream(file.outputStream())).use { out ->
            out.writeBytes(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(width)
            out.writeInt(height)
            out.writeInt(records.size)
            for (r in records) {
                out.writeInt(r.setIndex)
                out.writeInt(r.frame)
                out.writeByte(if (r.posePresent) 1 else 0)
                for (v in r.values) out.writeFloat(v)
            }
        }
    }

    fun read(file: File): LandmarkData =
        DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 20)).use { input ->
            val magic = ByteArray(4).also { input.readFully(it) }.decodeToString()
            require(magic == MAGIC) { "${file.name} is not a landmark file" }
            val version = input.readInt()
            require(version == VERSION) { "${file.name}: version $version, expected $VERSION" }
            val width = input.readInt()
            val height = input.readInt()
            val count = input.readInt()
            val records = ArrayList<LandmarkRecord>(count)
            repeat(count) {
                val setIndex = input.readInt()
                val frame = input.readInt()
                val present = input.readByte().toInt() == 1
                val values = FloatArray(Landmarks.PACKED_SIZE) { input.readFloat() }
                records += LandmarkRecord(setIndex, frame, present, values)
            }
            LandmarkData(width, height, records)
        }
}

/**
 * Which frames a labelled set is evaluated over - fixed before any result was seen (P3-27).
 *
 * MM-Fit frame N is this video's decoded frame N - shift, the shift measured inside each set
 * (alignment_by_set.py).
 */
enum class Protocol {
    /** The label window, moved onto the decoded frames. The reported result. */
    PRIMARY,

    /** The same, a second wider at each end: how much the edges matter. */
    WIDENED,

    /** The label's numbers used as decoded frames, no shift: what ignoring the drift would give. */
    UNSHIFTED;

    fun window(label: MmFitLabel, shift: Int): IntRange = when (this) {
        PRIMARY -> (label.start - shift)..(label.end - shift)
        WIDENED -> (label.start - shift - WIDEN_FRAMES)..(label.end - shift + WIDEN_FRAMES)
        UNSHIFTED -> label.start..label.end
    }

    companion object {
        const val WIDEN_FRAMES: Int = 30
    }
}

sealed interface SetResult {
    class Evaluated(
        val set: RepetitionSetRecord,
        val window: IntRange,
        val framesFed: Int,
        val framesWithPose: Int,
        /** Frames of the window the extraction did not cover: fed as nothing, and reported. */
        val framesMissing: Int,
        /**
         * Diagnostics, not results: each side's driving angle as the engine measured it frame by
         * frame (`rawAngleLeft` / `rawAngleRight`, NaN where that side was unmeasurable), so a set
         * that failed can be told apart as unseen or as not moving far enough.
         */
        val left: SideReading = SideReading(DoubleArray(0)),
        val right: SideReading = SideReading(DoubleArray(0)),
    ) : SetResult

    class Excluded(val reason: String) : SetResult
}

/** One side's raw angles over a window: how often it was measurable, and the range it covered. */
class SideReading(angles: DoubleArray, spec: RepSignalSpec? = null) {
    val frames: Int = angles.size
    private val measured: DoubleArray = angles.filter { !it.isNaN() }.sorted().toDoubleArray()
    val measuredFraction: Double = if (frames == 0) Double.NaN else measured.size.toDouble() / frames
    val p5: Double = percentile(0.05)
    val p95: Double = percentile(0.95)

    /**
     * The cycles an ideal threshold machine counts on these raw angles: from the start zone to the
     * far zone and back, with `RepFsm`'s own zones, but no outlier gate, no smoothing, no timing
     * limits, and an unmeasured frame simply skipped. Not a count the app could give - a diagnostic,
     * so what the engine's pipeline and state machine lose can be told apart from what the movement
     * never did. -1 without a spec.
     */
    val thresholdCycles: Int = if (spec == null) -1 else countCycles(angles, spec)

    private fun percentile(q: Double): Double =
        if (measured.isEmpty()) Double.NaN else measured[((measured.size - 1) * q).toInt()]

    private companion object {
        fun countCycles(angles: DoubleArray, spec: RepSignalSpec): Int {
            fun atTop(v: Double) = if (spec.topIsGreater) v >= spec.topThreshold - spec.hysteresis else v <= spec.topThreshold + spec.hysteresis
            fun atFar(v: Double) = if (spec.topIsGreater) v <= spec.bottomThreshold + spec.hysteresis else v >= spec.bottomThreshold - spec.hysteresis
            var cycles = 0
            var started = false
            var far = false
            for (v in angles) {
                if (v.isNaN()) continue
                when {
                    !started -> if (atTop(v)) started = true
                    !far -> if (atFar(v)) far = true
                    atTop(v) -> {
                        cycles++
                        far = false
                    }
                }
            }
            return cycles
        }
    }
}

object Harness {

    /**
     * One labelled set through the phone's analysis: a fresh `LiveRepetitionAnalysis`, as the phone
     * starts one per set; every frame of the window in order, stamped in video time; `onNothingSeen`
     * where MediaPipe found nobody, as the live screen does; `finish` at the window's end.
     */
    fun runSet(data: LandmarkData, label: MmFitLabel, shift: Int, protocol: Protocol): SetResult {
        val mapping = MmFitExercises.of(label.exercise)
        if (mapping is ExerciseMapping.Excluded) return SetResult.Excluded(mapping.reason)
        val profile = (mapping as ExerciseMapping.Evaluated).profile
        val window = protocol.window(label, shift)
        val reps = ArrayList<RepRecord>()
        val analysis = LiveRepetitionAnalysis(profile, temporalConfig = TEMPORAL, onRepetition = { reps += it })
        var fed = 0
        var withPose = 0
        var missing = 0
        val leftAngles = DoubleArray(window.count())
        val rightAngles = DoubleArray(window.count())
        for (frame in window) {
            // DECISIONS.md P3-32: every STRIDE-th frame only, to see the engine at the phone's
            // lower result rates (30 fps / 2 = 15, / 5 = 6). 1, the default, feeds every frame.
            if ((frame - window.first) % STRIDE != 0) continue
            val record = data.frame(label.setIndex, frame)
            val at = frameTimeMs(frame)
            when {
                record == null -> {
                    missing++
                    analysis.onNothingSeen(at)
                }
                record.posePresent -> {
                    withPose++
                    analysis.onFrame(at, FrameShape.toLandmarks(record.values, data.width, data.height))
                }
                else -> analysis.onNothingSeen(at)
            }
            leftAngles[fed] = analysis.rawAngleLeft
            rightAngles[fed] = analysis.rawAngleRight
            fed++
        }
        analysis.finish(frameTimeMs(window.last))
        val set = RepetitionSetRecord(profile.id, frameTimeMs(window.first), reps)
        return SetResult.Evaluated(
            set, window, fed, withPose, missing,
            SideReading(leftAngles.copyOf(fed), profile.repSignal), SideReading(rightAngles.copyOf(fed), profile.repSignal),
        )
    }

    /** Feed every STRIDE-th frame; `-Pstride=N` on `runHarness`. 1 unless asked. */
    private val STRIDE: Int = (System.getProperty("kinesis.stride") ?: "1").toInt().also {
        require(it >= 1) { "kinesis.stride must be at least 1, got $it" }
    }

    /**
     * The phone's temporal settings unless a diagnostic asks otherwise (DECISIONS.md P3-33):
     * `-Pgate=<degrees>` the outlier gate's threshold, `-Pbridge=<ms>` the longest bridged gap.
     */
    private val TEMPORAL: TemporalConfig = TemporalConfig.PROVISIONAL.let { base ->
        base.copy(
            outlierThreshold = System.getProperty("kinesis.gate")?.toDouble() ?: base.outlierThreshold,
            maxBridgedGapMs = System.getProperty("kinesis.bridge")?.toDouble() ?: base.maxBridgedGapMs,
        )
    }
}
