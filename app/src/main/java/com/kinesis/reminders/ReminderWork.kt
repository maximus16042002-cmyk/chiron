package com.kinesis.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kinesis.MainActivity
import com.kinesis.R
import com.kinesis.core.analysis.history.Reminders
import com.kinesis.core.domain.session.LocalDay
import com.kinesis.settings.AppSettings
import com.kinesis.storage.KinesisDatabase
import com.kinesis.storage.RoomSessionRepository
import com.kinesis.storage.utcOffsetMinutesAt
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Training reminders - DECISIONS.md P3-49. **Local only**: a notification made on this phone at a
 * time the user chose. No push service, no Firebase Cloud Messaging, no network (FEATURE_PLAN rule
 * 5, CLAUDE.md section 16).
 *
 * WorkManager, already a dependency (P3-14), runs one piece of work at the next reminder time; the
 * work posts the reminder and schedules the one after. WorkManager keeps its work across a reboot
 * by itself, so there is no boot receiver of our own.
 */
object ReminderScheduler {

    private const val WORK_NAME = "kinesis-reminder"

    /** After the settings change: the next reminder from now, replacing any already scheduled, or none. */
    fun reschedule(context: Context) = enqueue(context, ExistingWorkPolicy.REPLACE)

    /** When the app starts: schedule one if reminders are on and none is waiting. */
    fun ensureScheduled(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

    /**
     * From the reminder work itself, as it finishes. Appended rather than replacing, because
     * replacing would cancel the work that is still running.
     */
    internal fun scheduleAfterThis(context: Context) = enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
        val settings = AppSettings(context)
        val work = WorkManager.getInstance(context)
        val now = System.currentTimeMillis()
        val due = if (settings.remindersOn) {
            ReminderTiming.next(now, TimeZone.getDefault(), settings.reminderWeekdays, settings.reminderMinuteOfDay)
        } else {
            null
        }
        if (due == null) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(due - now, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_DUE_AT to due))
            .build()
        work.enqueueUniqueWork(WORK_NAME, policy, request)
    }
}

/**
 * Posts the reminder if one is due, then schedules the next.
 *
 * What it says is the core's (`Reminders.message`): nothing if work was already completed today.
 * If the phone held the work back for hours (switched off, deep sleep), the moment has passed and
 * nothing is posted - a reminder at 3 a.m. for 6 p.m. yesterday would be noise.
 */
class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        if (settings.remindersOn) {
            val dueAt = inputData.getLong(KEY_DUE_AT, 0L)
            val now = System.currentTimeMillis()
            if (dueAt > 0L && now - dueAt <= LATE_LIMIT_MS) {
                val sessions = RoomSessionRepository(KinesisDatabase.get(applicationContext).sessionDao()).allSessions()
                val today = LocalDay.of(now.toDouble(), utcOffsetMinutesAt(now))
                Reminders.message(sessions, today)?.let { ReminderNotification.show(applicationContext, it) }
            }
        }
        ReminderScheduler.scheduleAfterThis(applicationContext)
        return Result.success()
    }

    companion object {
        const val KEY_DUE_AT: String = "due_at"

        /** Later than this after its time, a reminder is not posted. */
        const val LATE_LIMIT_MS: Long = 3 * 60 * 60 * 1000L
    }
}

/** The notification itself, on its own channel so the user can silence it in the phone's settings. */
object ReminderNotification {

    private const val CHANNEL_ID = "training-reminders"
    private const val NOTIFICATION_ID = 1

    /** Whether the app may post notifications at all (Android 13+ asks; earlier versions do not). */
    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by permitted() on the line before
    fun show(context: Context, text: String) {
        if (!permitted(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Kinesis")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Training reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "The reminders you set in Kinesis's Profile page."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
