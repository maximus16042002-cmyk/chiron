package com.kinesis.core.domain

import com.kinesis.core.domain.rep.MovementContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MovementContextTest {

    @Test
    fun movement_context_requires_exactly_145_features() {
        val valid = DoubleArray(MovementContext.FEATURE_COUNT) { it.toDouble() }
        val context = MovementContext(valid)
        assertEquals(MovementContext.FEATURE_COUNT, context.features.size)
        assertEquals(145, MovementContext.FEATURE_COUNT)
        assertEquals(9, MovementContext.SIGNAL_COUNT)
        assertEquals(16, MovementContext.POINTS_PER_SIGNAL)

        assertFailsWith<IllegalArgumentException> {
            MovementContext(DoubleArray(144))
        }
        assertFailsWith<IllegalArgumentException> {
            MovementContext(DoubleArray(146))
        }
    }

    @Test
    fun duration_is_stored_in_the_last_feature_slot() {
        val features = DoubleArray(MovementContext.FEATURE_COUNT)
        features[MovementContext.FEATURE_COUNT - 1] = 1250.0
        val context = MovementContext(features)
        assertEquals(1250.0, context.durationMs)
    }

    @Test
    fun signal_names_has_9_distinct_entries() {
        assertEquals(9, MovementContext.SIGNAL_NAMES.size)
        assertEquals(9, MovementContext.SIGNAL_NAMES.toSet().size)
    }
}
