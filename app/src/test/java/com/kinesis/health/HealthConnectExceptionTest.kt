package com.kinesis.health

import android.content.ContextWrapper
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

class HealthConnectExceptionTest {
    @Test
    fun internal_exceptions_are_not_swallowed() = runBlocking {
        try {
            val source = HealthConnectActivityContextSource(
                object : ContextWrapper(null) {}
            )
            
            val mockClient = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(HealthConnectClient::class.java)
            ) { _, _, _ ->
                throw NullPointerException("Internal bug!")
            } as HealthConnectClient
            
            source.read<Long>(
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
