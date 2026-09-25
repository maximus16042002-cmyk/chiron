package com.kinesis.core.analysis

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Permanent guard tests - CLAUDE.md sections 3, 4, 18 and 22.
 *
 * Two of the architecture's hard rules are about code that must not exist
 * anywhere, and no type signature can express an absence. These are checked by
 * reading the source tree.
 *
 *  1. Nothing silently averages a bilateral signal. BilateralIntegrityTest pins
 *     the type; this pins the call sites.
 *  2. Nothing in the shared core knows which camera produced a frame.
 *     MirroringRegressionTest pins the adapter; this pins the core.
 *
 * The scan runs on the JVM only, because it reads files. That is deliberate and
 * sufficient: it checks source text, which is identical for every target.
 *
 * These tests must not be weakened or deleted to make a build pass. If a scan
 * produces a false positive, narrow the pattern and say why - do not delete the
 * check or add a blanket exemption.
 */
class SharedCoreSourceGuardTest {

    // --- Rule 1: no silent bilateral averaging -------------------------------

    @Test
    fun no_production_source_averages_the_two_sides_of_a_bilateral_signal() {
        val offences = mutableListOf<String>()
        val scanned = forEachProductionSource(SCANNED_MODULES) { file, lineNumber, line ->
            if (file.name in BILATERAL_REDUCER_FILES) return@forEachProductionSource
            if (readsBothSides(line) && averages(line)) {
                offences += relativePath(file) + ":" + lineNumber + "  " + line.trim()
            }
        }

        assertScannedSomething(scanned, SCANNED_MODULES)
        if (offences.isNotEmpty()) {
            fail(
                "Bilateral signals are averaged without a named reducer.\n" +
                    "CLAUDE.md section 3: left and right stay separate everywhere, and any\n" +
                    "reduction is an explicit, named operation at the call site. Use\n" +
                    "BilateralSignal.meanExplicit(), asymmetry(), dominant() or weaker().\n\n" +
                    offences.joinToString("\n"),
            )
        }
    }

    @Test
    fun no_production_source_declares_an_implicit_collapse_to_a_scalar() {
        val offences = mutableListOf<String>()
        val scanned = forEachProductionSource(SCANNED_MODULES) { file, lineNumber, line ->
            if (file.name in BILATERAL_REDUCER_FILES) return@forEachProductionSource
            if (IMPLICIT_COLLAPSE.containsMatchIn(line)) {
                offences += relativePath(file) + ":" + lineNumber + "  " + line.trim()
            }
        }

        assertScannedSomething(scanned, SCANNED_MODULES)
        if (offences.isNotEmpty()) {
            fail(
                "A BilateralSignal is given something that reads as \"the value\".\n" +
                    "CONTRACTS.md section 1: no toDouble(), no operator overload, and no\n" +
                    "property that reads as the value. Every reduction is named.\n\n" +
                    offences.joinToString("\n"),
            )
        }
    }

    /** The detector has to be able to fail, or it proves nothing. */
    @Test
    fun the_bilateral_detector_recognises_a_violation() {
        assertTrue(readsBothSides("val mean = rom.left + rom.right") )
        assertTrue(averages("val mean = (rom.left + rom.right) / 2"))
        assertTrue(averages("val mean = (rom.left + rom.right) / 2.0"))
        assertTrue(averages("val mean = (rom.left + rom.right) * 0.5"))
        assertTrue(IMPLICIT_COLLAPSE.containsMatchIn("fun BilateralSignal.toDouble(): Double = left"))
        assertTrue(IMPLICIT_COLLAPSE.containsMatchIn("val BilateralSignal.value: Double get() = left"))

        // And it has to leave legitimate code alone.
        assertTrue(!averages("val midHipX = (x(LEFT_HIP) + x(RIGHT_HIP)) / 2.0"))
        assertTrue(!readsBothSides("val peak = rom.dominant()"))
        assertTrue(!IMPLICIT_COLLAPSE.containsMatchIn("fun asymmetry(signal: BilateralSignal): Double"))
    }

    // --- Rule 2: the shared core does not know about the camera --------------

    @Test
    fun no_shared_core_source_refers_to_which_camera_produced_the_frame() {
        val offences = mutableListOf<String>()
        val scanned = forEachProductionSource(CORE_MODULES) { file, lineNumber, line ->
            val hit = CAMERA_FACING.find(line)
            if (hit != null) {
                offences += relativePath(file) + ":" + lineNumber + "  [" + hit.value + "]  " + line.trim()
            }
        }

        assertScannedSomething(scanned, CORE_MODULES)
        if (offences.isNotEmpty()) {
            fail(
                "The shared core refers to camera facing or mirroring.\n" +
                    "CONTRACTS.md section 4: there is no parameter, no flag and no branch\n" +
                    "anywhere in the core that depends on which camera produced the frame.\n" +
                    "Landmarks entering the core are always anatomical. Mirroring belongs in\n" +
                    "the platform pose adapter, which lives in :app.\n\n" +
                    offences.joinToString("\n"),
            )
        }
    }

    @Test
    fun the_camera_facing_detector_recognises_a_violation() {
        assertTrue(CAMERA_FACING.containsMatchIn("fun analyse(facing: CameraFacing) {"))
        assertTrue(CAMERA_FACING.containsMatchIn("if (isMirrored) swapSides()"))
        assertTrue(CAMERA_FACING.containsMatchIn("val f = cameraFacing"))
        assertTrue(CAMERA_FACING.containsMatchIn("frontCamera = true"))

        // Anatomical vocabulary is not camera vocabulary.
        assertTrue(!CAMERA_FACING.containsMatchIn("val front = LandmarkId.LEFT_FOOT_INDEX"))
        assertTrue(!CAMERA_FACING.containsMatchIn("requiredView = CameraView.FRONT_ON"))
    }

    // --- Rule 3: the real-time path allocates and iterates nothing ------------

    /**
     * CLAUDE.md section 6 forbids growing collections and whole-history
     * reprocessing on the per-frame path. RealTimeBudgetTest and
     * PerFrameAllocationTest prove the behaviour; this catches the idioms that
     * cause it, at the point they are written rather than after a device gets
     * slow.
     *
     * The prototype's LiveRepDetector is the case study - see DECISIONS.md P2-2.
     * It held every sample of the session in a `mutableListOf`, called `toList()`
     * on it every frame, and re-ran the whole filter chain over the result. Each
     * of those three is in the pattern list below.
     *
     * Scoped to the real-time path only. The post-rep path may allocate freely:
     * it runs once per repetition, on a bounded, already-completed signal.
     */
    @Test
    fun the_real_time_path_uses_no_growing_collections_or_whole_signal_passes() {
        val offences = mutableListOf<String>()
        val scanned = forEachProductionSource(CORE_MODULES) { file, lineNumber, line ->
            if (file.name !in REAL_TIME_PATH_FILES) return@forEachProductionSource
            val hit = GROWING_OR_ITERATING.find(line)
            if (hit != null) {
                offences += relativePath(file) + ":" + lineNumber + "  [" + hit.value + "]  " + line.trim()
            }
        }

        assertScannedSomething(scanned, CORE_MODULES)
        if (offences.isNotEmpty()) {
            fail(
                "The real-time path uses a growing collection or a whole-signal pass.\n" +
                    "CLAUDE.md section 6: the per-frame path is O(1), must not iterate over\n" +
                    "session history, and must not reprocess an entire signal per frame.\n" +
                    "Work in the preallocated ring buffer instead. If this file genuinely is\n" +
                    "not on the real-time path, take it out of REAL_TIME_PATH_FILES and say\n" +
                    "why - do not widen the pattern.\n\n" +
                    offences.joinToString("\n"),
            )
        }
    }

    @Test
    fun the_real_time_path_detector_recognises_a_violation() {
        assertTrue(GROWING_OR_ITERATING.containsMatchIn("private val samples = mutableListOf<Double>()"))
        assertTrue(GROWING_OR_ITERATING.containsMatchIn("val snapshot = rawSamples.toList()"))
        assertTrue(GROWING_OR_ITERATING.containsMatchIn("val mean = history.map { it.value }.average()"))
        assertTrue(GROWING_OR_ITERATING.containsMatchIn("samples.add(TemporalSample(t, v))"))
        assertTrue(GROWING_OR_ITERATING.containsMatchIn("return history.filter { it > 0 }"))

        // Indexed work over a fixed window is the whole point and must pass.
        assertTrue(!GROWING_OR_ITERATING.containsMatchIn("for (index in 0 until windowSamples) {"))
        assertTrue(!GROWING_OR_ITERATING.containsMatchIn("scratch[index] = values[slot]"))
        assertTrue(!GROWING_OR_ITERATING.containsMatchIn("var sum = 0.0"))
    }

    /**
     * A list of filenames is only a guard while it names the files that exist. If
     * the real-time package gains a file, it is on the real-time path until
     * someone argues otherwise in writing.
     */
    @Test
    fun every_file_in_the_real_time_package_is_covered_by_the_scan() {
        var scannedAnyPackage = false
        val uncovered = mutableListOf<String>()
        for (relative in CLASSIFIED_PACKAGES) {
            val directory = File(repoRoot(), relative)
            if (!directory.isDirectory) continue
            scannedAnyPackage = true
            for (file in directory.listFiles().orEmpty()) {
                if (!file.isFile || file.extension != "kt") continue
                if (file.name in REAL_TIME_PATH_FILES) continue
                if (file.name in POST_REP_PATH_FILES) continue
                uncovered += relative + "/" + file.name
            }
        }

        assertTrue(scannedAnyPackage, "none of the classified packages exist; the scan found nothing")
        assertTrue(
            uncovered.isEmpty(),
            "these files are classified neither real-time nor post-rep: " + uncovered +
                ". Add each to REAL_TIME_PATH_FILES, or to POST_REP_PATH_FILES with a reason.",
        )
    }

    // --- Sanity: the facade is the only exported surface -------------------------

    /**
     * CONTRACTS.md section 5: "The facade is the *only* `@JsExport` surface in
     * the project." It moved from `jsMain` to `commonMain` in P3-19, so the JVM
     * runs the same facade the parity suite compares. This used to check that
     * `jsMain` held only the facade; it now checks the rule itself, across every
     * production source set of every module.
     */
    @Test
    fun the_facade_is_the_only_js_export_in_the_project() {
        val exporting = mutableSetOf<String>()
        val scanned = forEachProductionSource(SCANNED_MODULES) { file, _, line ->
            if (line.contains("@JsExport")) exporting += relativePath(file)
        }
        assertScannedSomething(scanned, SCANNED_MODULES)
        assertEquals(
            setOf("core/analysis/src/commonMain/kotlin/com/kinesis/core/analysis/KinesisEngine.kt"),
            exporting,
            "@JsExport found outside the facade",
        )
    }

    // --- Scanning machinery ---------------------------------------------------

    private fun forEachProductionSource(
        modules: List<String>,
        visit: (file: File, lineNumber: Int, line: String) -> Unit,
    ): List<File> {
        val root = repoRoot()
        val files = modules
            .map { File(root, it + "/src") }
            .filter { it.isDirectory }
            .flatMap { moduleSrc ->
                moduleSrc.listFiles().orEmpty()
                    // Every production source set: `commonMain` and the other
                    // KMP sets, and the app's `main` and build-type sets. Until
                    // P3-19 this matched only names ending in "Main", so the app's
                    // `main` - listed in SCANNED_MODULES - was never read.
                    .filter { it.isDirectory && !it.name.contains("test", ignoreCase = true) }
                    .flatMap { sourceSet ->
                        sourceSet.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
                    }
            }

        for (file in files) {
            stripComments(file.readText()).lineSequence().forEachIndexed { index, line ->
                visit(file, index + 1, line)
            }
        }
        return files
    }

    /**
     * Comments are prose, not code. A KDoc that explains why the core must not
     * know about the front camera is not a violation of that rule, and a scan
     * that cannot tell the difference would push the explanation out of the
     * source. Line structure is preserved so reported line numbers stay true.
     */
    private fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var index = 0
        var inBlock = false
        var inString = false
        while (index < source.length) {
            val here = source[index]
            val next = if (index + 1 < source.length) source[index + 1] else '\u0000'
            when {
                inBlock -> {
                    if (here == '*' && next == '/') {
                        inBlock = false
                        index += 2
                        continue
                    }
                    if (here == '\n') out.append('\n')
                    index++
                }
                inString -> {
                    out.append(here)
                    if (here == '\\' && next != '\u0000') {
                        out.append(next)
                        index += 2
                        continue
                    }
                    if (here == '"') inString = false
                    if (here == '\n') inString = false
                    index++
                }
                here == '/' && next == '*' -> {
                    inBlock = true
                    index += 2
                }
                here == '/' && next == '/' -> {
                    while (index < source.length && source[index] != '\n') index++
                }
                here == '"' -> {
                    inString = true
                    out.append(here)
                    index++
                }
                else -> {
                    out.append(here)
                    index++
                }
            }
        }
        return out.toString()
    }

    private fun assertScannedSomething(scanned: List<File>, modules: List<String>) {
        assertTrue(
            scanned.isNotEmpty(),
            "the scan found no Kotlin source under " + modules + " relative to " + repoRoot() +
                " - a guard that scans nothing passes for the wrong reason",
        )
    }

    private fun relativePath(file: File): String =
        file.absolutePath.removePrefix(repoRoot().absolutePath).removePrefix(File.separator)
            .replace(File.separatorChar, '/')

    private fun repoRoot(): File {
        var candidate: File? = File(".").absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").isFile) return candidate
            candidate = candidate.parentFile
        }
        fail("could not locate the repository root from " + File(".").absolutePath)
    }

    private fun readsBothSides(line: String): Boolean =
        LEFT_ACCESS.containsMatchIn(line) && RIGHT_ACCESS.containsMatchIn(line)

    private fun averages(line: String): Boolean = readsBothSides(line) && AVERAGING.containsMatchIn(line)

    private companion object {
        val SCANNED_MODULES = listOf("core/domain", "core/analysis", "app", "desktop")
        val CORE_MODULES = listOf("core/domain", "core/analysis")

        /** The one place a named reducer is allowed to touch both sides at once. */
        val BILATERAL_REDUCER_FILES = setOf("BilateralSignal.kt")

        val LEFT_ACCESS = Regex("""\.left\b""")
        val RIGHT_ACCESS = Regex("""\.right\b""")
        val AVERAGING = Regex("""/\s*2(\.0)?\b|\*\s*0\.5\b|\baverage\s*\(|\bmean\s*\(""")

        val IMPLICIT_COLLAPSE = Regex(
            """(fun|val|var)\s+BilateralSignal\.(toDouble|value|asDouble|scalar)\b""",
        )

        val CAMERA_FACING = Regex(
            """\bCameraFacing\b|\bcameraFacing\b|\bisMirrored\b|\bmirrorHorizontally\b""" +
                """|\bfrontCamera\b|\bbackCamera\b|\bLENS_FACING""",
        )

        /**
         * Files on the per-frame path. Each runs about thirty times a second for
         * the length of a set, so each is held to CLAUDE.md section 6.
         */
        val REAL_TIME_PATH_FILES = setOf(
            "DoubleRingBuffer.kt",
            "EmaSmoother.kt",
            "MedianOutlierGate.kt",
            "RealTimeSignalPipeline.kt",
            // P3: the FSM and the hold analyser step once per frame.
            "RepFsm.kt",
            "BilateralRepTracker.kt",
            "HoldAnalyser.kt",
            // P3-15: the display-phase reducer is read every frame, and the live
            // orchestrator is the per-frame entry point itself.
            "BilateralPhase.kt",
            "LiveRepetitionAnalysis.kt",
            // P3-16: the hold path's reducer and orchestrator, likewise per frame.
            "HoldReading.kt",
            "LiveHoldAnalysis.kt",
        )

        /**
         * Files on the post-rep path. These run roughly once every few seconds, on
         * a repetition that has already finished, so they may allocate and may
         * walk the whole signal - that is what they are for.
         */
        val POST_REP_PATH_FILES = setOf(
            "GapInterpolator.kt",
            "SignalResampler.kt",
            "SignalMetrics.kt",
            "TemporalConfig.kt",
            // P3-10: the dashboard and the streak rule run when a screen opens,
            // over history already written. They walk the whole history on
            // purpose, which is what this path is for.
            "Streaks.kt",
            "Dashboard.kt",
        )

        /**
         * Packages whose every file must be classified as one or the other. A
         * filename list only guards while it names the files that exist, so a
         * new file in any of these is a test failure until someone says which
         * path it is on.
         */
        val CLASSIFIED_PACKAGES = listOf(
            "core/analysis/src/commonMain/kotlin/com/kinesis/core/analysis/temporal",
            "core/analysis/src/commonMain/kotlin/com/kinesis/core/analysis/rep",
            "core/analysis/src/commonMain/kotlin/com/kinesis/core/analysis/hold",
            "core/analysis/src/commonMain/kotlin/com/kinesis/core/analysis/history",
            "core/analysis/src/commonMain/kotlin/com/kinesis/core/analysis/live",
        )

        /**
         * Growing collections, whole-collection copies, and the functional
         * sequence operators, each of which walks a collection and allocates.
         */
        val GROWING_OR_ITERATING = Regex(
            """\bmutableListOf\b|\bmutableMapOf\b|\bmutableSetOf\b|\bArrayList\b|\bbuildList\b""" +
                """|\.toList\(\)|\.toMutableList\(\)|\.toTypedArray\(\)""" +
                """|\.map\s*[({]|\.filter\s*[({]|\.forEach\s*[({]|\.fold\s*[({]""" +
                """|\.sumOf\s*[({]|\.average\(\)|\.mapNotNull\s*[({]|\.count\s*[({]""" +
                """|\.add\(|\.removeAt\(""",
        )
    }
}
