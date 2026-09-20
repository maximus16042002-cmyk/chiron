package com.kinesis.health

import com.kinesis.core.domain.session.LocalDay

/**
 * A stand-in for Health Connect, so the read path can be exercised without a
 * device, an emulator, or Health Connect installed - DECISIONS.md P3-11.
 *
 * This is what makes most of this batch testable at all. The real source needs
 * a provider, a permission dialog and a populated health store, none of which
 * exists in a JVM unit test. What *can* be tested is that every shape the source
 * can return is handled, and that Kinesis behaves the same whichever one comes
 * back - which is the property that matters, because P3-3 makes this
 * supplementary and supplementary means never load-bearing.
 */
class FakeActivityContextSource(
    private val availability: ContextAvailability,
    private val day: (LocalDay) -> ActivityContextResult = { unavailableFrom(availability) },
) : ActivityContextSource {

    /** How many times the source was asked for a day. */
    var readCount: Int = 0
        private set

    override suspend fun availability(): ContextAvailability = availability

    override suspend fun readDay(day: LocalDay, utcOffsetMinutes: Int): ActivityContextResult {
        readCount++
        return this.day(day)
    }

    companion object {

        private fun unavailableFrom(availability: ContextAvailability): ActivityContextResult =
            when (availability) {
                is ContextAvailability.Unavailable ->
                    ActivityContextResult.Unavailable(availability.reason)

                is ContextAvailability.Available ->
                    ActivityContextResult.Unavailable(ContextUnavailable.PERMISSIONS_DENIED)
            }

        /** A device too old for Health Connect to exist at all. */
        fun platformTooOld(): FakeActivityContextSource = FakeActivityContextSource(
            ContextAvailability.Unavailable(ContextUnavailable.PLATFORM_TOO_OLD),
        )

        /** A modern device with Health Connect absent. */
        fun notInstalled(): FakeActivityContextSource = FakeActivityContextSource(
            ContextAvailability.Unavailable(ContextUnavailable.NOT_INSTALLED),
        )

        /** Installed, but the provider is too old to serve reads. */
        fun providerUpdateRequired(): FakeActivityContextSource = FakeActivityContextSource(
            ContextAvailability.Unavailable(ContextUnavailable.PROVIDER_UPDATE_REQUIRED),
        )

        /** Installed and usable, with the user having granted nothing. */
        fun permissionsDenied(): FakeActivityContextSource = FakeActivityContextSource(
            ContextAvailability.Available(emptySet()),
        )

        /** Installed, with only some of the three permissions granted. */
        fun partiallyPermitted(
            granted: Set<String>,
            steps: DailyMetric = DailyMetric.NotPermitted,
            calories: DailyMetric = DailyMetric.NotPermitted,
            heartRate: DailyMetric = DailyMetric.NotPermitted,
        ): FakeActivityContextSource = FakeActivityContextSource(
            availability = ContextAvailability.Available(granted),
            day = { day ->
                ActivityContextResult.Available(
                    DailyActivityContext(day, steps, calories, heartRate),
                )
            },
        )

        /** Everything granted and a full day of data. */
        fun fullyAvailable(
            steps: Double = 8_000.0,
            calories: Double = 420.0,
            heartRate: Double = 62.0,
        ): FakeActivityContextSource = FakeActivityContextSource(
            availability = ContextAvailability.Available(ActivityContextPermissions.ALL),
            day = { day ->
                ActivityContextResult.Available(
                    DailyActivityContext(
                        day = day,
                        steps = DailyMetric.Measured(steps),
                        activeCaloriesKcal = DailyMetric.Measured(calories),
                        meanHeartRateBpm = DailyMetric.Measured(heartRate),
                    ),
                )
            },
        )
    }
}
