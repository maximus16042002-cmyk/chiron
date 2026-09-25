package com.kinesis.health

import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class HealthConnectExceptionTest {
    @Test
    fun internal_exceptions_are_not_swallowed() = runBlocking {
        try {
            val mockClient = FakeHealthConnectClient {
                throw NullPointerException("Internal bug!")
            }
            
            // readMetric became top-level in P3-19; the rule it pins is unchanged.
            readMetric<Long>(
                client = mockClient,
                range = TimeRangeFilter.between(Instant.EPOCH, Instant.EPOCH.plusMillis(1)),
                granted = setOf("perm"),
                permission = "perm",
                metric = StepsRecord.COUNT_TOTAL,
                toDouble = { it.toDouble() }
            )
            fail("Exception was swallowed!")
        } catch (e: Throwable) {
            println("CAUGHT EXCEPTION: ${e.javaClass.name}: ${e.message}")
            if (e is NullPointerException && e.message == "Internal bug!") {
                // Success
            } else {
                throw e
            }
        }
    }
}
