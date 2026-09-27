package com.kinesis.core.analysis

import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.calibration.BilateralSummary
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepTiming
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.calibration.RobustSummary
import com.kinesis.core.domain.calibration.TimingBaseline
import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity

/**
 * Reads back what [EngineJson] writes: records and baselines - DECISIONS.md
 * P3-21.
 *
 * P6 is the first time the facade takes structured JSON in, not just an id, so
 * it needs a reader. Hand-written for the reason the writer is: the core takes
 * no JSON library (FINAL_ARCHITECTURE section 3), and the shapes are few, flat
 * and its own. JSON `null` for a number is [Double.NaN] - not measured - the
 * inverse of the writer's rule.
 *
 * Strict: a missing field or a wrong type is an [IllegalArgumentException]
 * naming the field, never a default. A default is a measurement nobody made.
 */
internal object EngineJsonReader {

    fun reps(json: String): List<RepRecord> = array(parse(json), "records").map { rep(obj(it, "record")) }

    fun rep(json: String): RepRecord = rep(obj(parse(json), "record"))

    fun holds(json: String): List<HoldRecord> = array(parse(json), "records").map { hold(obj(it, "record")) }

    fun hold(json: String): HoldRecord = hold(obj(parse(json), "record"))

    fun baseline(json: String): CalibrationBaseline {
        val o = obj(parse(json), "baseline")
        val exerciseId = string(o, "exerciseId")
        val methodVersion = string(o, "methodVersion")
        val minimumSamples = int(o, "minimumSamples")
        val offered = int(o, "recordsOffered")
        val used = int(o, "recordsUsed")
        return when (val kind = string(o, "kind")) {
            "repetition" -> RepetitionBaseline(
                exerciseId = exerciseId,
                methodVersion = methodVersion,
                minimumSamples = minimumSamples,
                recordsOffered = offered,
                recordsUsed = used,
                rangeOfMotion = obj(o["rangeOfMotion"], "rangeOfMotion").let {
                    BilateralSummary(summary(it, "left"), summary(it, "right"))
                },
                timing = timingBaseline(obj(o["timing"], "timing")),
                habitualOffset = summary(o, "habitualOffset"),
            )
            "hold" -> HoldBaseline(
                exerciseId = exerciseId,
                methodVersion = methodVersion,
                minimumSamples = minimumSamples,
                recordsOffered = offered,
                recordsUsed = used,
                totalHoldMs = summary(o, "totalHoldMs"),
                longestUnbrokenMs = summary(o, "longestUnbrokenMs"),
                meanAbsDeviation = summary(o, "meanAbsDeviation"),
            )
            else -> throw IllegalArgumentException("unknown baseline kind \"$kind\"")
        }
    }

    private fun rep(o: Map<String, Any?>): RepRecord = RepRecord(
        repIndex = int(o, "repIndex"),
        attributedSide = o["attributedSide"]?.let { Side.valueOf(it as? String ?: fail("attributedSide")) },
        startMs = number(o, "startMs"),
        endMs = number(o, "endMs"),
        validity = RepValidity.valueOf(string(o, "validity")),
        rangeOfMotion = bilateral(o, "rangeOfMotion"),
        peakValue = bilateral(o, "peakValue"),
        troughValue = bilateral(o, "troughValue"),
        descentMs = number(o, "descentMs"),
        ascentMs = number(o, "ascentMs"),
        resampledLeft = numbers(o, "resampledLeft"),
        resampledRight = numbers(o, "resampledRight"),
    )

    private fun hold(o: Map<String, Any?>): HoldRecord = HoldRecord(
        startMs = number(o, "startMs"),
        endMs = number(o, "endMs"),
        validity = HoldValidity.valueOf(string(o, "validity")),
        totalHoldMs = number(o, "totalHoldMs"),
        longestUnbrokenMs = number(o, "longestUnbrokenMs"),
        breakCount = int(o, "breakCount"),
        meanAbsDeviation = number(o, "meanAbsDeviation"),
        maxAbsDeviation = number(o, "maxAbsDeviation"),
        coverage = number(o, "coverage"),
    )

    private fun timingBaseline(o: Map<String, Any?>): TimingBaseline = when (val kind = string(o, "kind")) {
        "together" -> TimingBaseline.Together(timing(obj(o["both"], "both")))
        "perSide" -> TimingBaseline.PerSide(timing(obj(o["left"], "left")), timing(obj(o["right"], "right")))
        else -> throw IllegalArgumentException("unknown timing kind \"$kind\"")
    }

    private fun timing(o: Map<String, Any?>): RepTiming = RepTiming(
        descentMs = summary(o, "descentMs"),
        ascentMs = summary(o, "ascentMs"),
        durationMs = summary(o, "durationMs"),
        tempoRatio = summary(o, "tempoRatio"),
    )

    private fun summary(parent: Map<String, Any?>, key: String): RobustSummary {
        val o = obj(parent[key], key)
        return RobustSummary(
            n = int(o, "n"),
            median = number(o, "median"),
            q1 = number(o, "q1"),
            q3 = number(o, "q3"),
        )
    }

    private fun bilateral(o: Map<String, Any?>, key: String): BilateralSignal {
        val pair = obj(o[key], key)
        return BilateralSignal(number(pair, "left"), number(pair, "right"))
    }

    // --- Field access ----------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun obj(value: Any?, what: String): Map<String, Any?> =
        value as? Map<String, Any?> ?: fail(what, "an object")

    private fun array(value: Any?, what: String): List<Any?> = value as? List<Any?> ?: fail(what, "an array")

    private fun string(o: Map<String, Any?>, key: String): String = o[key] as? String ?: fail(key, "a string")

    private fun number(o: Map<String, Any?>, key: String): Double {
        if (key !in o) fail(key)
        return when (val value = o[key]) {
            null -> Double.NaN
            is Double -> value
            else -> fail(key, "a number or null")
        }
    }

    private fun int(o: Map<String, Any?>, key: String): Int {
        val value = o[key] as? Double ?: fail(key, "a whole number")
        val whole = value.toInt()
        if (whole.toDouble() != value) fail(key, "a whole number")
        return whole
    }

    private fun numbers(o: Map<String, Any?>, key: String): DoubleArray {
        val list = array(o[key], key)
        return DoubleArray(list.size) {
            when (val value = list[it]) {
                null -> Double.NaN
                is Double -> value
                else -> fail("$key[$it]", "a number or null")
            }
        }
    }

    private fun fail(key: String, expected: String = "present"): Nothing =
        throw IllegalArgumentException("\"$key\" must be $expected")

    // --- Parsing ---------------------------------------------------------------------

    /** JSON text to maps, lists, strings, doubles, booleans and `null`. */
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipSpace()
        require(parser.done) { "unexpected text at ${parser.at}" }
        return value
    }

    private class Parser(val text: String) {
        var at = 0
        val done: Boolean get() = at >= text.length

        fun skipSpace() {
            while (!done && text[at].isWhitespace()) at++
        }

        private fun peek(): Char {
            require(!done) { "unexpected end of JSON" }
            return text[at]
        }

        private fun expect(c: Char) {
            require(peek() == c) { "expected '$c' at $at" }
            at++
        }

        fun value(): Any? {
            skipSpace()
            return when (peek()) {
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
            expect('{')
            skipSpace()
            if (peek() == '}') { at++; return map }
            while (true) {
                skipSpace()
                val key = str()
                skipSpace()
                expect(':')
                require(key !in map) { "duplicate key \"$key\"" }
                map[key] = value()
                skipSpace()
                if (peek() == ',') { at++; continue }
                expect('}')
                return map
            }
        }

        private fun arr(): List<Any?> {
            val list = ArrayList<Any?>()
            expect('[')
            skipSpace()
            if (peek() == ']') { at++; return list }
            while (true) {
                list += value()
                skipSpace()
                if (peek() == ',') { at++; continue }
                expect(']')
                return list
            }
        }

        private fun str(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val c = peek()
                at++
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        val escaped = peek()
                        at++
                        when (escaped) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(at + 4 <= text.length) { "short \\u escape at $at" }
                                out.append(text.substring(at, at + 4).toInt(16).toChar())
                                at += 4
                            }
                            else -> throw IllegalArgumentException("bad escape '\\$escaped' at $at")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = at
            while (!done && (text[at].isDigit() || text[at] in "+-.eE")) at++
            require(at > start) { "unexpected '${text[start]}' at $start" }
            return text.substring(start, at).toDoubleOrNull()
                ?: throw IllegalArgumentException("bad number \"${text.substring(start, at)}\" at $start")
        }

        private fun <T> literal(word: String, value: T): T {
            require(text.startsWith(word, at)) { "expected $word at $at" }
            at += word.length
            return value
        }
    }
}
