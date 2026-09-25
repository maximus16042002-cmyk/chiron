package com.kinesis.health

import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * DECISIONS.md P3-19, part 7: the daily-context panel must name the real reason
 * a metric is missing. A read Health Connect refuses or fails used to be
 * reported as "no data", the same as an empty day.
 */
class HealthMetricReadTest {

    private val range = TimeRangeFilter.between(Instant.EPOCH, Instant.EPOCH.plusSeconds(86_400))
    private val granted = ActivityContextPermissions.ALL

    @Test
    fun a_read_that_throws_is_reported_as_failed_not_as_no_data() = runBlocking {
        var reported = 0
        val refusing = FakeHealthConnectClient { throw SecurityException("Caller doesn't have foreground access") }
        val metric = readMetric(
            refusing, range, granted, ActivityContextPermissions.READ_STEPS, StepsRecord.COUNT_TOTAL,
            reportFailure = { _, _ -> reported++ },
        ) { it.toDouble() }
        assertEquals(DailyMetric.ReadFailed, metric)
        assertEquals("a failed read must be reported, not swallowed", 1, reported)
    }

    @Test
    fun a_permission_the_user_withheld_is_not_permitted_without_asking() = runBlocking {
        val untouched = FakeHealthConnectClient { throw AssertionError("asked Health Connect for a withheld permission") }
        val metric = readMetric(
            untouched, range, emptySet(), ActivityContextPermissions.READ_STEPS, StepsRecord.COUNT_TOTAL,
        ) { it.toDouble() }
        assertEquals(DailyMetric.NotPermitted, metric)
    }
}
