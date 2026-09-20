package com.kinesis.health

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.kinesis.core.domain.session.LocalDay
import java.time.Instant

/**
 * Reads daily activity context from Health Connect - DECISIONS.md P3-11.
 *
 * ### The API level story, which is the reason this class is shaped this way
 *
 * Kinesis targets `minSdk 24`. The Health Connect client library needs 26, and
 * a provider only exists from Android 9 onward - built into the framework from
 * Android 14. So every entry point checks the platform first and returns
 * [ContextUnavailable.PLATFORM_TOO_OLD] rather than touching the library at all,
 * and the parts that do touch it are `@RequiresApi(26)`. The app installs and
 * runs identically on API 24 and 25; it simply has no context to show.
 *
 * ### Nothing here can fail loudly
 *
 * P3-3 makes this supplementary - "displayed alongside Kinesis's own
 * measurements rather than replacing them". A supplementary feature that can
 * throw is not supplementary. Every expected condition is a result, and the
 * catch-all exists because a health provider on an arbitrary device is exactly
 * the kind of thing that fails in ways its documentation does not list.
 *
 * ### Read-only
 *
 * This class calls `aggregate` and `getGrantedPermissions`, and nothing else.
 * There is no write path and no write permission is ever requested, which is
 * what keeps P3-3's "read-only" claim true by construction rather than by
 * intention.
 */
class HealthConnectActivityContextSource(
    private val appContext: Context,
) : ActivityContextSource {

    override suspend fun availability(): ContextAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return ContextAvailability.Unavailable(ContextUnavailable.PLATFORM_TOO_OLD)
        }
        return availabilityOnSupportedPlatform()
    }

    override suspend fun readDay(day: LocalDay, utcOffsetMinutes: Int): ActivityContextResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return ActivityContextResult.Unavailable(ContextUnavailable.PLATFORM_TOO_OLD)
        }
        return readDayOnSupportedPlatform(day, utcOffsetMinutes)
    }

    // --- Everything below needs API 26 -------------------------------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun availabilityOnSupportedPlatform(): ContextAvailability {
        val status = try {
            HealthConnectClient.sdkStatus(appContext)
        } catch (error: Throwable) {
            return ContextAvailability.Unavailable(ContextUnavailable.NOT_INSTALLED)
        }

        when (status) {
            HealthConnectClient.SDK_UNAVAILABLE ->
                return ContextAvailability.Unavailable(ContextUnavailable.NOT_INSTALLED)

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                return ContextAvailability.Unavailable(
                    ContextUnavailable.PROVIDER_UPDATE_REQUIRED,
                )
        }

        return try {
            val granted = HealthConnectClient.getOrCreate(appContext)
                .permissionController
                .getGrantedPermissions()
            ContextAvailability.Available(granted.intersect(ActivityContextPermissions.ALL))
        } catch (error: Throwable) {
            ContextAvailability.Unavailable(ContextUnavailable.READ_FAILED)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun readDayOnSupportedPlatform(
        day: LocalDay,
        utcOffsetMinutes: Int,
    ): ActivityContextResult {
        val granted = when (val availability = availabilityOnSupportedPlatform()) {
            is ContextAvailability.Unavailable ->
                return ActivityContextResult.Unavailable(availability.reason)

            is ContextAvailability.Available -> availability.grantedPermissions
        }

        if (granted.isEmpty()) {
            return ActivityContextResult.Unavailable(ContextUnavailable.PERMISSIONS_DENIED)
        }

        // The local day's window, expressed as the UTC instants that bound it.
        // Derived from the offset captured with the session (P3-10) rather than
        // the device's current zone, for the same reason: the day a session
        // belongs to does not move when the user travels.
        val startOfDayUtcMs = day.dayNumber.toLong() * MILLIS_PER_DAY - utcOffsetMinutes * 60_000L
        val start = Instant.ofEpochMilli(startOfDayUtcMs)
        val range = TimeRangeFilter.between(start, start.plusMillis(MILLIS_PER_DAY))

        val client = try {
            HealthConnectClient.getOrCreate(appContext)
        } catch (error: Throwable) {
            return ActivityContextResult.Unavailable(ContextUnavailable.READ_FAILED)
        }

        val steps = read(
            client, range, granted,
            ActivityContextPermissions.READ_STEPS, StepsRecord.COUNT_TOTAL,
        ) { it.toDouble() }

        val calories = read(
            client, range, granted,
            ActivityContextPermissions.READ_ACTIVE_CALORIES,
            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
        ) { it.inKilocalories }

        val heartRate = read(
            client, range, granted,
            ActivityContextPermissions.READ_HEART_RATE, HeartRateRecord.BPM_AVG,
        ) { it.toDouble() }

        return ActivityContextResult.Available(
            DailyActivityContext(
                day = day,
                steps = steps,
                activeCaloriesKcal = calories,
                meanHeartRateBpm = heartRate,
            ),
        )
    }

    /**
     * One metric, with every failure turned into a [DailyMetric] rather than an
     * exception.
     *
     * A permission the user withheld is [DailyMetric.NotPermitted] without a call
     * being made; a granted permission over an empty day is [DailyMetric.NoData];
     * anything the provider throws is also [DailyMetric.NoData], because a
     * supplementary panel must not take the screen down with it.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun <T : Any> read(
        client: HealthConnectClient,
        range: TimeRangeFilter,
        granted: Set<String>,
        permission: String,
        metric: AggregateMetric<T>,
        toDouble: (T) -> Double,
    ): DailyMetric {
        if (permission !in granted) return DailyMetric.NotPermitted
        return try {
            val value = client
                .aggregate(AggregateRequest(setOf(metric), range))[metric]
            if (value == null) DailyMetric.NoData else DailyMetric.Measured(toDouble(value))
        } catch (error: Throwable) {
            DailyMetric.NoData
        }
    }

    private companion object {
        const val MILLIS_PER_DAY: Long = 86_400_000L

        /**
         * The permissions to request, in the form the permission contract wants.
         * Read-only: there is no write permission in this set and never will be.
         */
        @Suppress("unused")
        val REQUESTED: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
        )
    }
}
