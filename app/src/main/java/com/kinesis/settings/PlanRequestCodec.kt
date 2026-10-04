package com.kinesis.settings

import com.kinesis.core.analysis.workout.Equipment
import com.kinesis.core.analysis.workout.Focus
import com.kinesis.core.analysis.workout.Level
import com.kinesis.core.analysis.workout.PlanRequest

/**
 * A workout request as one line of text, for SharedPreferences - DECISIONS.md P3-50. The plan is
 * not stored: the generator rebuilds it from the request and seed, and its reproducibility test is
 * what makes that safe. A line that cannot be read back gives `null`.
 */
object PlanRequestCodec {

    private const val VERSION = "v1"

    fun encode(request: PlanRequest): String = listOf(
        VERSION,
        request.minutes.toString(),
        request.focus.name,
        request.level.name,
        request.equipment.name,
        request.seed.toString(),
        request.avoid.sorted().joinToString(","),
    ).joinToString(";")

    fun decode(line: String): PlanRequest? {
        val parts = line.split(';')
        if (parts.size != 7 || parts[0] != VERSION) return null
        val minutes = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val focus = Focus.entries.firstOrNull { it.name == parts[2] } ?: return null
        val level = Level.entries.firstOrNull { it.name == parts[3] } ?: return null
        val equipment = Equipment.entries.firstOrNull { it.name == parts[4] } ?: return null
        val seed = parts[5].toIntOrNull() ?: return null
        val avoid = parts[6].split(',').filter { it.isNotBlank() }.toSet()
        return PlanRequest(minutes, focus, level, equipment, avoid, seed)
    }
}
