package com.kinesis.tools.mmfit

import com.kinesis.core.analysis.profile.ExerciseLibrary
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.Landmarks
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The P7 harness's own pieces - DECISIONS.md P3-27. The engine it drives is tested in the core;
 * these check that MM-Fit's files reach it the way the phone's frames do.
 */
class MmFitHarnessTest {

    // --- Labels and exercises -------------------------------------------------------------------

    @Test
    fun labels_are_read_in_file_order_and_numbered_from_one() {
        val labels = MmFitLabels.parse("4040,4500,10,squats\n5435,5928,11,pushups\n")
        assertEquals(listOf(1, 2), labels.map { it.setIndex })
        assertEquals(MmFitLabel(2, 5435, 5928, 11, "pushups"), labels[1])
    }

    /**
     * Squats are excluded by the owner's decision (P3-27): MM-Fit films them face-on and the squat
     * profile is side-on, so a squat number would be evidence that does not transfer (CLAUDE.md
     * section 22). Two exercises have no Kinesis profile at all.
     */
    @Test
    fun every_mmfit_exercise_is_evaluated_or_excluded_with_its_reason() {
        val evaluated = mapOf(
            "pushups" to "pushup",
            "dumbbell_shoulder_press" to "shoulder_press",
            "lunges" to "lunge",
            "dumbbell_rows" to "row",
            "situps" to "situp",
            "bicep_curls" to "bicep_curl",
            "lateral_shoulder_raises" to "lateral_raise",
        )
        for ((name, id) in evaluated) {
            val mapping = assertIs<ExerciseMapping.Evaluated>(MmFitExercises.of(name), name)
            assertEquals(id, mapping.profile.id)
            assertIs<RepetitionExercise>(ExerciseLibrary.all.single { it.id == id })
        }
        val squats = assertIs<ExerciseMapping.Excluded>(MmFitExercises.of("squats"))
        assertTrue("view" in squats.reason, squats.reason)
        for (name in listOf("tricep_extensions", "jumping_jacks")) {
            val none = assertIs<ExerciseMapping.Excluded>(MmFitExercises.of(name))
            assertTrue("no Kinesis profile" in none.reason, none.reason)
        }
    }

    // --- The frame-shape correction - CONTRACTS.md section 4 -----------------------------------

    /**
     * The phone adapter's back-camera path: x as MediaPipe gives it, y times height over width, z
     * and visibility untouched. MM-Fit's video is not mirrored (its OpenPose and MediaPipe agree on
     * left and right, P3-27), so the unmirroring the front camera needs does not apply.
     */
    @Test
    fun the_correction_is_the_phone_adapters_back_camera_path_with_mmfits_frame_size() {
        val raw = FloatArray(Landmarks.PACKED_SIZE) { (it % 7) / 10f }
        val landmarks = FrameShape.toLandmarks(raw, width = 1280, height = 720)
        for (id in LandmarkId.entries) {
            val at = id.index * Landmarks.STRIDE
            assertEquals(raw[at].toDouble(), landmarks.x(id))
            assertEquals(raw[at + 1].toDouble() * 720.0 / 1280.0, landmarks.y(id), 1e-12)
            assertEquals(raw[at + 2].toDouble(), landmarks.z(id))
            assertEquals(raw[at + 3].toDouble(), landmarks.visibility(id))
        }
    }

    // --- Landmark files -------------------------------------------------------------------------

    @Test
    fun a_landmark_file_reads_back_what_was_written() {
        val dir = createTempDirectory("mmfit").toFile()
        val file = File(dir, "w99.bin")
        val seen = FloatArray(Landmarks.PACKED_SIZE) { it * 0.001f }
        val unseen = FloatArray(Landmarks.PACKED_SIZE) { Float.NaN }
        LandmarkFile.write(file, 1280, 720, listOf(LandmarkRecord(1, 40, true, seen), LandmarkRecord(1, 41, false, unseen)))

        val read = LandmarkFile.read(file)
        assertEquals(1280, read.width)
        assertEquals(720, read.height)
        assertEquals(listOf(40, 41), read.records.map { it.frame })
        assertTrue(read.records[0].posePresent)
        assertTrue(seen.contentEquals(read.records[0].values))
        assertTrue(!read.records[1].posePresent && read.records[1].values.all { it.isNaN() })
    }

    /**
     * Two sets under 4 s apart share margin frames, processed once under the earlier set (P3-27).
     * The later set must still get them - a frame is the same video frame whichever run produced it -
     * and its own run's frames must win where both have one.
     */
    @Test
    fun a_frame_not_in_a_sets_own_run_is_taken_from_its_neighbour() {
        val a = FloatArray(Landmarks.PACKED_SIZE) { 0.1f }
        val b = FloatArray(Landmarks.PACKED_SIZE) { 0.2f }
        val data = LandmarkData(1280, 720, listOf(LandmarkRecord(1, 500, true, a), LandmarkRecord(2, 501, true, b), LandmarkRecord(1, 501, true, a)))
        assertTrue(a.contentEquals(data.frame(2, 500)!!.values), "set 2 did not get frame 500 from set 1")
        assertTrue(b.contentEquals(data.frame(2, 501)!!.values), "set 2's own frame 501 did not win")
        assertEquals(null, data.frame(2, 502))
    }

    // --- Windows --------------------------------------------------------------------------------

    /**
     * MM-Fit's frame numbers run ahead of or behind this video's decoded frames by a shift measured
     * per set (P3-27): MM-Fit frame N is decoded frame N - shift.
     */
    @Test
    fun a_label_window_maps_to_decoded_frames_by_the_measured_shift() {
        val label = MmFitLabel(3, 1000, 1300, 10, "bicep_curls")
        assertEquals(968..1268, Protocol.PRIMARY.window(label, shift = 32))
        assertEquals(1026..1326, Protocol.PRIMARY.window(label, shift = -26))
        assertEquals(938..1298, Protocol.WIDENED.window(label, shift = 32))
        assertEquals(1000..1300, Protocol.UNSHIFTED.window(label, shift = 32))
    }

    // --- End to end -----------------------------------------------------------------------------

    /**
     * Six alternating curls, built in square pixels and written as a 1280x720 camera's raw output
     * (y divided by height over width): the harness must undo that exactly and count all six, or the
     * correction or the plumbing is wrong.
     */
    @Test
    fun synthetic_curls_through_the_harness_are_counted() {
        val dir = createTempDirectory("mmfit").toFile()
        val frames = ArrayList<LandmarkRecord>()
        var frame = 100
        fun add(left: Double, right: Double) { frames += LandmarkRecord(1, frame++, true, SyntheticPose.arms(left, right, 1280, 720)) }
        repeat(30) { add(160.0, 160.0) }
        repeat(3) {
            for (arm in 0..1) {
                for (step in 0 until 50) {
                    val angle = if (step < 25) 160.0 - 110.0 * (step + 1) / 25.0 else 50.0 + 110.0 * (step - 24) / 25.0
                    if (arm == 0) add(angle, 160.0) else add(160.0, angle)
                }
                repeat(10) { add(160.0, 160.0) }
            }
        }
        repeat(30) { add(160.0, 160.0) }
        val landmarks = File(dir, "w99.bin")
        LandmarkFile.write(landmarks, 1280, 720, frames)
        val label = MmFitLabel(1, 100, frame - 1, 6, "bicep_curls")

        val result = Harness.runSet(LandmarkFile.read(landmarks), label, shift = 0, protocol = Protocol.PRIMARY)
        val evaluated = assertIs<SetResult.Evaluated>(result)
        assertEquals(6, evaluated.set.validReps.size, "counted ${evaluated.set.reps.map { it.validity }}")
        assertEquals(frames.size, evaluated.framesFed)
        assertEquals(0, evaluated.framesMissing)
        // The diagnostic counter sees the same three cycles per arm the engine counted.
        assertEquals(3, evaluated.left.thresholdCycles)
        assertEquals(3, evaluated.right.thresholdCycles)
        assertEquals(1.0, evaluated.left.measuredFraction)
    }
}

/** Upright, arms hanging, elbows bent to the given angles - in square pixels, returned as raw camera output. */
private object SyntheticPose {
    fun arms(leftElbow: Double, rightElbow: Double, width: Int, height: Int): FloatArray {
        val out = FloatArray(Landmarks.PACKED_SIZE)
        fun put(id: LandmarkId, x: Double, y: Double) {
            out[id.index * Landmarks.STRIDE] = x.toFloat()
            // Square pixels in, raw camera output out: undo the correction the harness will apply.
            out[id.index * Landmarks.STRIDE + 1] = (y * width / height).toFloat()
            out[id.index * Landmarks.STRIDE + 3] = 0.95f
        }
        for (id in LandmarkId.entries) put(id, 0.5, 0.3)
        fun arm(shoulder: LandmarkId, elbow: LandmarkId, wrist: LandmarkId, x: Double, outward: Double, degrees: Double) {
            val radians = degrees * PI / 180.0
            put(shoulder, x, 0.16)
            put(elbow, x, 0.26)
            put(wrist, x + outward * 0.1 * sin(radians), 0.26 - 0.1 * cos(radians))
        }
        arm(LandmarkId.LEFT_SHOULDER, LandmarkId.LEFT_ELBOW, LandmarkId.LEFT_WRIST, 0.46, -1.0, leftElbow)
        arm(LandmarkId.RIGHT_SHOULDER, LandmarkId.RIGHT_ELBOW, LandmarkId.RIGHT_WRIST, 0.54, 1.0, rightElbow)
        put(LandmarkId.LEFT_HIP, 0.47, 0.34)
        put(LandmarkId.RIGHT_HIP, 0.53, 0.34)
        return out
    }
}
