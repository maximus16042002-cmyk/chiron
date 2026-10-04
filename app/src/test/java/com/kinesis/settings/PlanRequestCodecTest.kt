package com.kinesis.settings

import com.kinesis.core.analysis.workout.Equipment
import com.kinesis.core.analysis.workout.Focus
import com.kinesis.core.analysis.workout.Level
import com.kinesis.core.analysis.workout.PlanRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Favourites and the last workout are stored as their request and seed, and the plan rebuilt from
 * them - DECISIONS.md P3-50. A stored line that cannot be read back is dropped, never guessed at.
 */
class PlanRequestCodecTest {

    @Test
    fun a_request_survives_the_round_trip() {
        for (request in listOf(
            PlanRequest(20, Focus.FULL_BODY, Level.INTERMEDIATE, Equipment.DUMBBELLS, seed = 2026),
            PlanRequest(10, Focus.CORE, Level.BEGINNER, Equipment.NONE, avoid = setOf("plank", "situp"), seed = -7),
        )) {
            assertEquals(request, PlanRequestCodec.decode(PlanRequestCodec.encode(request)))
        }
    }

    @Test
    fun anything_unreadable_is_dropped() {
        for (line in listOf("", "nonsense", "v1;20;FULL_BODY;INTERMEDIATE;DUMBBELLS", "v1;x;FULL_BODY;BEGINNER;NONE;1;",
            "v1;20;SIDEWAYS;BEGINNER;NONE;1;", "v9;20;FULL_BODY;BEGINNER;NONE;1;", "v1;0;FULL_BODY;BEGINNER;NONE;1;")) {
            assertNull(line, PlanRequestCodec.decode(line))
        }
    }
}
