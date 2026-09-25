package com.kinesis.health

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kinesis.MainActivity
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.storage.utcOffsetMinutesAt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Permanent on-device guard - DECISIONS.md P3-19, part 7.
 *
 * On the owner's Android 16 phone the dashboard said "Health Connect needs
 * updating". From Android 14 Health Connect is part of the platform, so there is
 * nothing to update: the app itself was out of date. These pin what the phone
 * needs from the app, measured on the phone.
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectOnDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** On Android 14 and later the platform provides Health Connect: the app's own check must say so. */
    @Test
    fun on_android_14_and_later_health_connect_is_usable() {
        assumeTrue("Health Connect is built in from Android 14", Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        val availability = runBlocking { HealthConnectActivityContextSource(context).availability() }
        Log.i(TAG, "availability on Android ${Build.VERSION.RELEASE}: $availability")
        assertTrue("Health Connect reported unusable: $availability", availability is ContextAvailability.Available)
    }

    /**
     * Health Connect only shows its permission dialog to an app that says why it
     * wants the data. From Android 14 that is an activity-alias for
     * `VIEW_PERMISSION_USAGE` in the `HEALTH_PERMISSIONS` category; before, an
     * activity for `ACTION_SHOW_PERMISSIONS_RATIONALE`. Without them the request
     * returns nothing and the user never sees a dialog.
     */
    @Test
    fun the_app_says_why_it_wants_health_data() {
        val packages = context.packageManager
        val usage = Intent("android.intent.action.VIEW_PERMISSION_USAGE")
            .addCategory("android.intent.category.HEALTH_PERMISSIONS")
            .setPackage(context.packageName)
        val rationale = Intent("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE").setPackage(context.packageName)
        assertTrue(
            "no activity declares VIEW_PERMISSION_USAGE / HEALTH_PERMISSIONS (Android 14+)",
            packages.queryIntentActivities(usage, PackageManager.MATCH_ALL).isNotEmpty(),
        )
        assertTrue(
            "no activity declares ACTION_SHOW_PERMISSIONS_RATIONALE (Android 13 and earlier)",
            packages.queryIntentActivities(rationale, PackageManager.MATCH_ALL).isNotEmpty(),
        )
    }

    /**
     * Today's three metrics, read through the production source exactly as the
     * History screen reads them, with the app in the foreground - Health Connect
     * refuses background reads. Each must come back as a value, "no data" or "not
     * permitted": never as a failed read. Skipped until the owner has granted the
     * three permissions. Logs what it read, for the record.
     */
    @Test
    fun todays_metrics_are_read_without_failure_in_the_foreground() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        val source = HealthConnectActivityContextSource(context)
        val availability = runBlocking { source.availability() }
        assumeTrue(
            "the three read permissions are not all granted",
            availability is ContextAvailability.Available &&
                availability.grantedPermissions.containsAll(ActivityContextPermissions.ALL),
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.setShowWhenLocked(true)
                it.setTurnScreenOn(true)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val offset = utcOffsetMinutesAt(System.currentTimeMillis())
            val today = LocalDay.of(System.currentTimeMillis().toDouble(), offset)
            val result = runBlocking { source.readDay(today, offset) }
            Log.i(TAG, "today ($today, offset $offset): $result")
            assertTrue("the day could not be read: $result", result is ActivityContextResult.Available)
            val day = (result as ActivityContextResult.Available).context
            for ((name, metric) in listOf("steps" to day.steps, "active kcal" to day.activeCaloriesKcal, "mean bpm" to day.meanHeartRateBpm)) {
                assertTrue("$name: the read failed", metric != DailyMetric.ReadFailed)
            }
        }
    }

    private companion object {
        const val TAG = "KinesisHealth"
    }
}
