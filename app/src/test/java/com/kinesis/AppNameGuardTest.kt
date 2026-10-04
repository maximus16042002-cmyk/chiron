package com.kinesis

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app is called Chiron - DECISIONS.md P3-65.
 *
 * The code, its packages and the project record keep the name Kinesis; everything a person reads
 * says Chiron. This scans every production source of `:app` and the shared core for a string
 * literal or XML attribute a user could read that still says "Kinesis". Comments are not read.
 */
class AppNameGuardTest {

    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "could not find the repository root" }
    }

    /**
     * Identifiers that are data or plumbing, not words to read: the export's format id (a reader
     * of old files relies on it), work, preference and database names, and log tags.
     */
    private val allowed = listOf(
        "\"kinesis-export\"",
        "kinesis-reminder",
        "kinesis-session-sync",
        "kinesis-accounts",
        "\"kinesis\"",
        "\"kinesis-\" + fileSafe(uid)",
        "\"kinesis.db\"",
    )

    /** A log tag, such as `"KinesisSync"`: read in logcat by a developer, never by a user. */
    private val logTag = Regex("\"Kinesis[A-Z][A-Za-z]*\"")

    private val literal = Regex("\"[^\"]*\"")

    @Test
    fun no_text_a_user_reads_says_kinesis() {
        val root = repoRoot()
        val sources = listOf(
            "app/src/main/java",
            "app/src/main/res",
            "core/analysis/src/commonMain",
            "core/domain/src/commonMain",
        ).flatMap { File(root, it).walkTopDown().filter { f -> f.isFile && (f.extension == "kt" || f.extension == "xml") }.toList() }
            .plus(File(root, "app/src/main/AndroidManifest.xml"))

        assertTrue("nothing was scanned", sources.size > 50)
        val found = mutableListOf<String>()
        for (file in sources) {
            var inBlockComment = false
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("/*") || line.startsWith("<!--")) inBlockComment = true
                val commented = inBlockComment || line.startsWith("*") || line.startsWith("//")
                if (line.contains("*/") || line.contains("-->")) inBlockComment = false
                if (commented) return@forEachIndexed
                for (match in literal.findAll(raw)) {
                    val text = match.value
                    if (!text.contains("kinesis", ignoreCase = true)) continue
                    if (allowed.any { raw.contains(it) } || logTag.matches(text)) continue
                    // Code identifiers inside a literal (a class name in a KDoc link) are not prose.
                    if (text.contains("com.kinesis")) continue
                    found += file.relativeTo(root).path + ":" + (index + 1) + "  " + text
                }
            }
        }
        assertTrue("still says Kinesis where a user reads it:\n" + found.joinToString("\n"), found.isEmpty())
    }
}
