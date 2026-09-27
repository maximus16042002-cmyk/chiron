package com.kinesis.core.analysis

/**
 * Just enough JSON reading for the core's own formats - DECISIONS.md P3-22.
 *
 * The facade's `computeBaseline` and `analyzeRep` take records and baselines as
 * JSON, and the app stores baselines as JSON, so the core now reads what
 * [EngineJson] writes. The core takes no JSON library (FINAL_ARCHITECTURE
 * section 3 keeps it dependency-free), and a reader for these flat documents is
 * small.
 *
 * Objects become maps, arrays lists, numbers `Double`, and `null` `null`.
 * Anything malformed is an [IllegalArgumentException] naming where, never a
 * half-read value. Written character by character: Kotlin/JS compiles regular
 * expressions with JavaScript's `u` flag, which already broke one parser here
 * (P3-19).
 *
 * Post-rep path only: it reads whole documents, once per record or baseline.
 */
internal object JsonText {

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipSpace()
        require(reader.done) { "unexpected text at ${reader.at}" }
        return value
    }

    private class Reader(val text: String) {
        var at = 0
        val done: Boolean get() = at >= text.length

        fun skipSpace() {
            while (!done && (text[at] == ' ' || text[at] == '\n' || text[at] == '\r' || text[at] == '\t')) at++
        }

        private fun peek(): Char {
            require(!done) { "unexpected end of JSON" }
            return text[at]
        }

        private fun expect(char: Char) {
            require(peek() == char) { "expected '$char' at $at" }
            at++
        }

        fun value(): Any? {
            skipSpace()
            return when (val char = peek()) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                'n' -> literal("null", null)
                't' -> literal("true", true)
                'f' -> literal("false", false)
                else -> if (char == '-' || char in '0'..'9') num() else throw IllegalArgumentException("unexpected '$char' at $at")
            }
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            expect('{')
            skipSpace()
            if (peek() == '}') {
                at++
                return map
            }
            while (true) {
                skipSpace()
                val key = str()
                skipSpace()
                expect(':')
                require(key !in map) { "duplicate key \"$key\"" }
                map[key] = value()
                skipSpace()
                if (peek() == ',') {
                    at++
                    continue
                }
                expect('}')
                return map
            }
        }

        private fun arr(): List<Any?> {
            val list = ArrayList<Any?>()
            expect('[')
            skipSpace()
            if (peek() == ']') {
                at++
                return list
            }
            while (true) {
                list += value()
                skipSpace()
                if (peek() == ',') {
                    at++
                    continue
                }
                expect(']')
                return list
            }
        }

        private fun str(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val char = peek()
                at++
                when (char) {
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
                                require(at + 4 <= text.length) { "truncated escape at $at" }
                                out.append(text.substring(at, at + 4).toInt(16).toChar())
                                at += 4
                            }
                            else -> throw IllegalArgumentException("bad escape at $at")
                        }
                    }
                    else -> out.append(char)
                }
            }
        }

        private fun num(): Double {
            val start = at
            while (!done && (text[at] == '-' || text[at] == '+' || text[at] == '.' || text[at] == 'e' ||
                    text[at] == 'E' || text[at] in '0'..'9')
            ) {
                at++
            }
            val literal = text.substring(start, at)
            return literal.toDoubleOrNull() ?: throw IllegalArgumentException("bad number \"$literal\" at $start")
        }

        private fun <T> literal(word: String, value: T): T {
            require(text.startsWith(word, at)) { "expected $word at $at" }
            at += word.length
            return value
        }
    }

    // --- Typed access, for readers of the core's own documents --------------------------

    fun obj(value: Any?, what: String): Map<String, Any?> {
        require(value is Map<*, *>) { "$what is not a JSON object" }
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any?>
    }

    fun field(map: Map<String, Any?>, key: String): Any? {
        require(key in map) { "missing \"$key\"" }
        return map[key]
    }

    /** A number, or `null` for not measured - which is `NaN` inside Kotlin. */
    fun double(map: Map<String, Any?>, key: String): Double = when (val value = field(map, key)) {
        null -> Double.NaN
        is Double -> value
        else -> throw IllegalArgumentException("\"$key\" is not a number")
    }

    fun int(map: Map<String, Any?>, key: String): Int {
        val value = field(map, key)
        require(value is Double && value == value.toInt().toDouble()) { "\"$key\" is not a whole number" }
        return value.toInt()
    }

    fun string(map: Map<String, Any?>, key: String): String {
        val value = field(map, key)
        require(value is String) { "\"$key\" is not a string" }
        return value
    }

    fun stringOrNull(map: Map<String, Any?>, key: String): String? {
        val value = field(map, key)
        require(value == null || value is String) { "\"$key\" is not a string" }
        return value as String?
    }

    fun doubles(map: Map<String, Any?>, key: String): DoubleArray {
        val value = field(map, key)
        require(value is List<*>) { "\"$key\" is not an array" }
        return DoubleArray(value.size) { index ->
            when (val element = value[index]) {
                null -> Double.NaN
                is Double -> element
                else -> throw IllegalArgumentException("\"$key\"[$index] is not a number")
            }
        }
    }
}
