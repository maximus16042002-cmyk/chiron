package com.kinesis.tools.mmfit

import java.io.File

/**
 * MM-Fit's subjects and the evaluation's splits, as `tools/mmfit-eval/subjects.py` defines them -
 * DECISIONS.md P3-30. This reads the JSON copy that script writes; `test_subjects.py` fails if the copy
 * is stale, and `SubjectsTest` checks the same properties here.
 *
 * The split is by subject. P3-27's split, by workout, is kept under its own names (`p3-27-...`) only
 * to reproduce results recorded under it.
 */
class Subjects(
    val subjects: Map<String, List<String>>,
    val paperWorkoutsPerSubject: List<Int>,
    val splits: Map<String, List<String>>,
) {
    private val subjectOf: Map<String, String> = subjects.flatMap { (s, ws) -> ws.map { it to s } }.toMap()

    fun subjectOf(workout: String): String = subjectOf[workout] ?: error("no subject for $workout")

    fun split(name: String): List<String> = splits[name] ?: error("no split \"$name\"; known: ${splits.keys}")

    /** Subjects with workouts on both sides of two splits, with their workouts on each side. */
    fun leaks(first: String, second: String): Map<String, Pair<List<String>, List<String>>> {
        val a = split(first).toSet()
        val b = split(second).toSet()
        return subjects.mapNotNull { (subject, workouts) ->
            val inA = workouts.filter { it in a }.sorted()
            val inB = workouts.filter { it in b }.sorted()
            if (inA.isNotEmpty() && inB.isNotEmpty()) subject to (inA to inB) else null
        }.toMap()
    }

    companion object {
        /** `tools/mmfit-eval/subjects.json`, found from the module directory or the repository root. */
        fun load(file: File = locate()): Subjects {
            @Suppress("UNCHECKED_CAST")
            val root = MiniJson(file.readText()).value() as Map<String, Any?>

            @Suppress("UNCHECKED_CAST")
            fun strings(v: Any?) = (v as List<Any?>).map { it as String }

            @Suppress("UNCHECKED_CAST")
            fun lists(v: Any?) = (v as Map<String, Any?>).mapValues { strings(it.value) }

            @Suppress("UNCHECKED_CAST")
            val counts = (root["paperWorkoutsPerSubject"] as List<Any?>).map { (it as Double).toInt() }
            return Subjects(lists(root["subjects"]), counts, lists(root["splits"]))
        }

        fun locate(): File = listOf(File("subjects.json"), File("tools/mmfit-eval/subjects.json"))
            .firstOrNull { it.isFile } ?: error("subjects.json not found from ${File(".").absolutePath}")
    }
}

/**
 * Enough JSON for `subjects.json` and the harness's own P5 exports: objects, arrays, strings without
 * escapes beyond `\"` and `\\`, numbers, `null`, `true` and `false`. The tool takes no JSON library for
 * files of its own making.
 */
internal class MiniJson(private val text: String) {
    private var at = 0

    fun value(): Any? {
        space()
        return when (val c = text[at]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            'n' -> literal("null", null)
            't' -> literal("true", true)
            'f' -> literal("false", false)
            else -> if (c == '-' || c.isDigit()) num() else error("unexpected '$c' at $at")
        }
    }

    private fun literal(word: String, value: Any?): Any? {
        check(text.startsWith(word, at)) { "expected $word at $at" }
        at += word.length
        return value
    }

    private fun obj(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        at++
        space()
        if (text[at] == '}') return out.also { at++ }
        while (true) {
            space()
            val key = str()
            space()
            check(text[at++] == ':') { "expected ':' at ${at - 1}" }
            out[key] = value()
            space()
            when (text[at++]) {
                ',' -> continue
                '}' -> return out
                else -> error("expected ',' or '}' at ${at - 1}")
            }
        }
    }

    private fun arr(): List<Any?> {
        val out = ArrayList<Any?>()
        at++
        space()
        if (text[at] == ']') return out.also { at++ }
        while (true) {
            out += value()
            space()
            when (text[at++]) {
                ',' -> continue
                ']' -> return out
                else -> error("expected ',' or ']' at ${at - 1}")
            }
        }
    }

    private fun str(): String {
        check(text[at++] == '"') { "expected a string at ${at - 1}" }
        val out = StringBuilder()
        while (true) {
            when (val c = text[at++]) {
                '"' -> return out.toString()
                '\\' -> out.append(text[at++])
                else -> out.append(c)
            }
        }
    }

    private fun num(): Double {
        val start = at
        while (at < text.length && (text[at].isDigit() || text[at] in "+-.eE")) at++
        return text.substring(start, at).toDouble()
    }

    private fun space() {
        while (at < text.length && text[at].isWhitespace()) at++
    }
}
