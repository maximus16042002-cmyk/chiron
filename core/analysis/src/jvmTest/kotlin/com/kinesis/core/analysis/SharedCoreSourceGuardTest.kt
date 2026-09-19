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

    // --- Sanity: the jsMain source set holds the facade and nothing else ------

    /**
     * REPOSITORY_STRUCTURE.md: "core/analysis/src/jsMain contains the @JsExport
     * facade and nothing else. If a second file appears there, something has
     * gone wrong."
     */
    @Test
    fun the_js_source_set_contains_only_the_facade() {
        val jsMain = File(repoRoot(), "core/analysis/src/jsMain")
        if (!jsMain.isDirectory) return
        val sources = jsMain.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertEquals(
            listOf("KinesisEngine.kt"),
            sources.map { it.name }.sorted(),
            "jsMain must hold the @JsExport facade and nothing else",
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
                    .filter { it.isDirectory && it.name.endsWith("Main") }
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
    }
}
