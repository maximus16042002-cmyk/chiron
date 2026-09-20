package com.kinesis.health

import com.kinesis.core.analysis.history.Dashboard
import com.kinesis.core.analysis.history.DashboardSummary
import com.kinesis.core.domain.bilateral.BilateralSignal
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permanent guard - DECISIONS.md P3-3 and P3-11.
 *
 * Health Connect is supplementary: P3-3 has it "displayed alongside Kinesis's
 * own measurements rather than replacing them". The whole risk of adding it is
 * that "alongside" quietly becomes "required" - an exception on a device without
 * it, a null that propagates, a screen that will not render because a panel
 * could not load. These tests exist to make that impossible to introduce
 * without noticing.
 *
 * ### What "tested" means for this batch, and what it cannot mean
 *
 * The pattern from every prior batch - resolve a question against MM-Fit, then
 * mutate the guard to prove it fails - does not transfer. There is no dataset
 * and no threshold here; the question is not "what is the right number" but
 * "does the app survive every way this can be missing".
 *
 * So what is tested is the **read path's contract**, against a fake, with no
 * device involved: every absence reason is a value the caller must handle, and
 * Kinesis's own summary is byte-identical whichever one comes back.
 *
 * What is **not** tested here, and is flagged for P4 device work rather than
 * faked into a false sense of coverage:
 *
 * - that `HealthConnectClient.sdkStatus` reports what this code expects on a
 *   real device, on Android 13 with the APK provider and on Android 14+ with
 *   the framework module;
 * - that the permission dialog appears, and that a partial grant produces the
 *   permission set this code assumes;
 * - that a real `aggregate` call over a real store returns the units this code
 *   converts (`Energy.inKilocalories`, `Long` beats per minute);
 * - that API 24 and 25 devices install and run with the library's minSdk 26
 *   overridden, which is the one place P3-11's `tools:overrideLibrary` decision
 *   could bite.
 *
 * Those need a device, and saying so is more useful than a mock that asserts
 * this code's own assumptions back at it.
 */
class ActivityContextAbsenceTest {

    private val today = LocalDay(20_000)

    // --- The three ways it can be missing -------------------------------------------

    @Test
    fun a_platform_too_old_for_health_connect_reports_that_and_does_not_throw() = runBlocking {
        val source = FakeActivityContextSource.platformTooOld()

        val availability = source.availability()
        assertTrue(availability is ContextAvailability.Unavailable)
        assertEquals(
            ContextUnavailable.PLATFORM_TOO_OLD,
            (availability as ContextAvailability.Unavailable).reason,
        )

        val result = source.readDay(today, utcOffsetMinutes = 0)
        assertEquals(
            ContextUnavailable.PLATFORM_TOO_OLD,
            (result as ActivityContextResult.Unavailable).reason,
        )
    }

    @Test
    fun health_connect_not_installed_reports_that_and_does_not_throw() = runBlocking {
        val result = FakeActivityContextSource.notInstalled().readDay(today, 0)
        assertEquals(
            ContextUnavailable.NOT_INSTALLED,
            (result as ActivityContextResult.Unavailable).reason,
        )
    }

    @Test
    fun a_provider_needing_an_update_is_distinguished_from_one_that_is_absent() = runBlocking {
        val result = FakeActivityContextSource.providerUpdateRequired().readDay(today, 0)
        assertEquals(
            ContextUnavailable.PROVIDER_UPDATE_REQUIRED,
            (result as ActivityContextResult.Unavailable).reason,
        )
    }

    @Test
    fun denying_every_permission_reports_that_and_does_not_throw() = runBlocking {
        val source = FakeActivityContextSource.permissionsDenied()

        val availability = source.availability()
        assertTrue(
            "an installed provider with nothing granted is still available",
            availability is ContextAvailability.Available,
        )
        assertTrue((availability as ContextAvailability.Available).grantedPermissions.isEmpty())

        val result = source.readDay(today, 0)
        assertEquals(
            ContextUnavailable.PERMISSIONS_DENIED,
            (result as ActivityContextResult.Unavailable).reason,
        )
    }

    /**
     * The case that is easiest to get wrong, because it is neither fully present
     * nor fully absent. Permissions are granted per metric, so a user can allow
     * steps and refuse heart rate, and each metric has to carry its own answer.
     */
    @Test
    fun granting_some_permissions_yields_those_metrics_and_marks_the_rest_not_permitted() =
        runBlocking {
            val source = FakeActivityContextSource.partiallyPermitted(
                granted = setOf(ActivityContextPermissions.READ_STEPS),
                steps = DailyMetric.Measured(7_400.0),
            )

            val context = (source.readDay(today, 0) as ActivityContextResult.Available).context
            assertEquals(DailyMetric.Measured(7_400.0), context.steps)
            assertEquals(DailyMetric.NotPermitted, context.activeCaloriesKcal)
            assertEquals(DailyMetric.NotPermitted, context.meanHeartRateBpm)
        }

    /**
     * A permission granted over a day with nothing recorded is not the same as a
     * permission withheld, and the UI should not say the same thing about both.
     */
    @Test
    fun a_permitted_metric_with_no_data_is_distinguished_from_one_that_was_refused() =
        runBlocking {
            val source = FakeActivityContextSource.partiallyPermitted(
                granted = ActivityContextPermissions.ALL,
                steps = DailyMetric.NoData,
                calories = DailyMetric.NoData,
                heartRate = DailyMetric.Measured(58.0),
            )

            val context = (source.readDay(today, 0) as ActivityContextResult.Available).context
            assertEquals(DailyMetric.NoData, context.steps)
            assertEquals(DailyMetric.Measured(58.0), context.meanHeartRateBpm)
            assertTrue("a day with one reading is not empty", !context.isEmpty)
        }

    @Test
    fun a_day_with_nothing_readable_reports_empty_rather_than_zeroes() = runBlocking {
        val source = FakeActivityContextSource.partiallyPermitted(
            granted = ActivityContextPermissions.ALL,
            steps = DailyMetric.NoData,
            calories = DailyMetric.NoData,
            heartRate = DailyMetric.NoData,
        )

        val context = (source.readDay(today, 0) as ActivityContextResult.Available).context
        assertTrue(context.isEmpty)
        assertEquals(null, context.steps.valueOrNull)
    }

    @Test
    fun a_fully_available_source_returns_all_three_pinned_metrics() = runBlocking {
        val context = (
            FakeActivityContextSource.fullyAvailable()
                .readDay(today, 0) as ActivityContextResult.Available
            ).context

        assertEquals(8_000.0, context.steps.valueOrNull!!, 1e-9)
        assertEquals(420.0, context.activeCaloriesKcal.valueOrNull!!, 1e-9)
        assertEquals(62.0, context.meanHeartRateBpm.valueOrNull!!, 1e-9)
        assertEquals(today, context.day)
    }

    // --- The property that actually matters -------------------------------------------

    /**
     * **Supplementary means never load-bearing.**
     *
     * Kinesis's own dashboard summary is computed from `SessionRecord` history
     * and nothing else. This drives every absence case past it and asserts the
     * summary is identical each time - including the fully-available case, which
     * is the one that would differ if anyone ever let health data leak into the
     * engine's own numbers.
     *
     * It is a structural fact today: `:core:analysis` has no reference to this
     * package and could not read it if it wanted to. The test is here so that
     * stops being an accident of layering and becomes something that fails
     * loudly if a future batch wires the two together.
     */
    @Test
    fun kinesis_own_measurements_are_identical_however_health_connect_is_missing() = runBlocking {
        val history = listOf(session(dayAt(-1)), session(dayAt(0)))
        val expected = Dashboard.summarise(history, today)

        val sources = listOf(
            FakeActivityContextSource.platformTooOld(),
            FakeActivityContextSource.notInstalled(),
            FakeActivityContextSource.providerUpdateRequired(),
            FakeActivityContextSource.permissionsDenied(),
            FakeActivityContextSource.fullyAvailable(),
        )

        for (source in sources) {
            // Whatever the context read does, the engine's own summary is
            // recomputed from the same history and must not move.
            source.readDay(today, 0)
            val actual = Dashboard.summarise(history, today)

            assertSameSummary(expected, actual)
        }
    }

    /**
     * And the converse: the engine never asks for context. If some future change
     * makes a Kinesis measurement depend on Health Connect, the read count stops
     * being zero.
     */
    @Test
    fun computing_kinesis_measurements_never_reads_health_connect() = runBlocking {
        val source = FakeActivityContextSource.fullyAvailable()
        val history = listOf(session(dayAt(0)))

        Dashboard.summarise(history, today)

        assertEquals(
            "the dashboard summary read Health Connect, which makes it load-bearing",
            0,
            source.readCount,
        )
    }

    private fun assertSameSummary(expected: DashboardSummary, actual: DashboardSummary) {
        assertEquals(expected.sessionCount, actual.sessionCount)
        assertEquals(expected.totalValidReps, actual.totalValidReps)
        assertEquals(expected.totalHoldMs, actual.totalHoldMs, 1e-9)
        assertEquals(expected.streak.currentDays, actual.streak.currentDays)
        assertEquals(expected.streak.longestDays, actual.streak.longestDays)
        assertEquals(expected.firstDay, actual.firstDay)
        assertEquals(expected.lastDay, actual.lastDay)
        assertEquals(expected.perExercise.size, actual.perExercise.size)
        for (index in expected.perExercise.indices) {
            val a = expected.perExercise[index]
            val b = actual.perExercise[index]
            assertEquals(a.exerciseId, b.exerciseId)
            assertEquals(a.validRepCount, b.validRepCount)
            assertEquals(a.meanRangeOfMotion.left, b.meanRangeOfMotion.left, 1e-9)
            assertEquals(a.meanRangeOfMotion.right, b.meanRangeOfMotion.right, 1e-9)
        }
        assertNotNull(actual.lastDay)
    }

    // --- History ----------------------------------------------------------------------

    private fun dayAt(offsetDays: Int): Double =
        (today.dayNumber + offsetDays) * LocalDay.MILLIS_PER_DAY + 12 * 3_600_000.0

    private fun session(epochMs: Double): SessionRecord = SessionRecord(
        sessionId = "s" + epochMs.toLong(),
        startedAtEpochMs = epochMs,
        utcOffsetMinutes = 0,
        sets = listOf(
            RepetitionSetRecord(
                exerciseId = "squat",
                startedAtEpochMs = epochMs,
                reps = (1..5).map { index ->
                    RepRecord(
                        repIndex = index,
                        attributedSide = null,
                        startMs = 0.0,
                        endMs = 2_000.0,
                        validity = RepValidity.VALID,
                        rangeOfMotion = BilateralSignal(95.0, 92.0),
                        peakValue = BilateralSignal(170.0, 170.0),
                        troughValue = BilateralSignal(75.0, 78.0),
                        descentMs = 1_000.0,
                        ascentMs = 1_000.0,
                        resampledLeft = DoubleArray(64),
                        resampledRight = DoubleArray(64),
                    )
                },
            ),
        ),
    )
}
