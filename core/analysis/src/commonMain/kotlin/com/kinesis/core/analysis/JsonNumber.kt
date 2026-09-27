package com.kinesis.core.analysis

/**
 * One text for one number, on every target - DECISIONS.md P3-25.
 *
 * `Double.toString()` is not the same on every platform: the JVM writes `100.0`,
 * `1.0E-5` and `1.7905376974852E12`, JavaScript `100`, `0.00001` and
 * `1790537697485.2`. Both parse to the same double, so the facade's parity suite
 * never minded; but the P5 export must match a golden file byte for byte on JVM
 * and JS, and cannot be written with it.
 *
 * So every number the core writes into JSON or CSV comes through [format], which
 * takes the platform's digits and lays them out by ECMAScript's Number::toString
 * rules (ECMA-262, "Number::toString"). On JavaScript that changes nothing. On a
 * JVM from JDK 19 on, `Double.toString` gives the same shortest round-tripping
 * digits JavaScript does (JDK-4511638), so the text comes out identical. Where a
 * runtime gives longer digits - Android's ART may - the text can differ in its
 * last digits but still reads back to exactly the same double.
 *
 * Not on the real-time path: the core writes JSON only when a record completes
 * and when a set is exported.
 */
internal object JsonNumber {

    /** [value] as JSON: `null` for `NaN` and the infinities, which JSON cannot hold. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "null"
        // Negative zero too: JavaScript writes it as 0.
        if (value == 0.0) return "0"

        var text = value.toString()
        val negative = text.startsWith('-')
        if (negative) text = text.substring(1)

        // Split the platform's text into its digits and a decimal point position:
        // value = 0.<digits> x 10^point.
        val e = text.indexOfFirst { it == 'e' || it == 'E' }
        val mantissa = if (e < 0) text else text.substring(0, e)
        val exponent = if (e < 0) 0 else text.substring(e + 1).removePrefix("+").toInt()
        val dot = mantissa.indexOf('.')
        val whole = if (dot < 0) mantissa else mantissa.substring(0, dot)
        val fraction = if (dot < 0) "" else mantissa.substring(dot + 1)

        var digits = whole + fraction
        var point = whole.length + exponent
        val leadingZeros = digits.indexOfFirst { it != '0' }
        digits = digits.substring(leadingZeros).trimEnd('0')
        point -= leadingZeros
        val count = digits.length

        // Number::toString, with n = point and k = count.
        val body = when {
            point in count..21 -> digits + "0".repeat(point - count)
            point in 1..21 -> digits.substring(0, point) + "." + digits.substring(point)
            point in -5..0 -> "0." + "0".repeat(-point) + digits
            else -> {
                val power = point - 1
                val mantissaText = if (count == 1) digits else digits.substring(0, 1) + "." + digits.substring(1)
                mantissaText + "e" + (if (power < 0) "-" else "+") + (if (power < 0) -power else power)
            }
        }
        return if (negative) "-" + body else body
    }
}
