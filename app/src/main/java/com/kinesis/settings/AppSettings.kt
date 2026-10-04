package com.kinesis.settings

import android.content.Context
import com.kinesis.core.analysis.history.WeeklyGoal
import com.kinesis.core.analysis.workout.PlanRequest

/**
 * The app's own small settings - DECISIONS.md P3-48 and P3-49.
 *
 * SharedPreferences, as FEATURE_PLAN prefers: no new dependency (DataStore would need a section 17
 * check for a handful of values). Local to the phone and never synced; nothing here is training
 * data.
 */
class AppSettings(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Whether the first-launch pages have been seen (P3-48). */
    var introduced: Boolean
        get() = preferences.getBoolean(INTRODUCED, false)
        set(value) = preferences.edit().putBoolean(INTRODUCED, value).apply()

    /** Active days a week the user aims for (P3-49), 1 to 7. */
    var weeklyGoalDays: Int
        get() = preferences.getInt(WEEKLY_GOAL_DAYS, WeeklyGoal.DEFAULT_GOAL_DAYS).coerceIn(1, 7)
        set(value) = preferences.edit().putInt(WEEKLY_GOAL_DAYS, value.coerceIn(1, 7)).apply()

    /** Whether training reminders are on (P3-49). Off until the user turns them on. */
    var remindersOn: Boolean
        get() = preferences.getBoolean(REMINDERS_ON, false)
        set(value) = preferences.edit().putBoolean(REMINDERS_ON, value).apply()

    /** The days a reminder may come, numbered as `java.util.Calendar` numbers them. Every day by default. */
    var reminderWeekdays: Set<Int>
        get() = preferences.getString(REMINDER_WEEKDAYS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..7 }
            ?.toSet()
            ?: (1..7).toSet()
        set(value) = preferences.edit()
            .putString(REMINDER_WEEKDAYS, value.filter { it in 1..7 }.sorted().joinToString(","))
            .apply()

    /** The reminder's time, in minutes after midnight. 18:00 by default. */
    var reminderMinuteOfDay: Int
        get() = preferences.getInt(REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE).coerceIn(0, 24 * 60 - 1)
        set(value) = preferences.edit().putInt(REMINDER_MINUTE, value.coerceIn(0, 24 * 60 - 1)).apply()

    /** A rest's length on the set summary, in seconds: 15 to 300, 60 by default (P3-49). */
    var restSeconds: Int
        get() = preferences.getInt(REST_SECONDS, DEFAULT_REST_SECONDS).coerceIn(15, 300)
        set(value) = preferences.edit().putInt(REST_SECONDS, value.coerceIn(15, 300)).apply()

    /** A short tone on each counted repetition (P3-49). Off by default. */
    var repTone: Boolean
        get() = preferences.getBoolean(REP_TONE, false)
        set(value) = preferences.edit().putBoolean(REP_TONE, value).apply()

    /** A vibration on each counted repetition (P3-49). Off by default. */
    var repVibrate: Boolean
        get() = preferences.getBoolean(REP_VIBRATE, false)
        set(value) = preferences.edit().putBoolean(REP_VIBRATE, value).apply()

    /** Each count spoken aloud, by an offline voice only (P3-49). Off by default. */
    var spokenCounts: Boolean
        get() = preferences.getBoolean(SPOKEN_COUNTS, false)
        set(value) = preferences.edit().putBoolean(SPOKEN_COUNTS, value).apply()

    /** The last workout started, as its request and seed (P3-50), or `null`. */
    var lastWorkout: PlanRequest?
        get() = preferences.getString(LAST_WORKOUT, null)?.let(PlanRequestCodec::decode)
        set(value) = preferences.edit().putString(LAST_WORKOUT, value?.let(PlanRequestCodec::encode)).apply()

    /** Saved workouts, newest first, at most [MAX_FAVOURITES] (P3-50). Unreadable lines are dropped. */
    var favouriteWorkouts: List<PlanRequest>
        get() = preferences.getString(FAVOURITES, null).orEmpty().lines().mapNotNull(PlanRequestCodec::decode)
        set(value) = preferences.edit()
            .putString(FAVOURITES, value.distinct().take(MAX_FAVOURITES).joinToString("\n") { PlanRequestCodec.encode(it) })
            .apply()

    private companion object {
        const val FILE = "kinesis"
        const val LAST_WORKOUT = "last_workout"
        const val FAVOURITES = "favourite_workouts"
        const val MAX_FAVOURITES = 10
        const val REST_SECONDS = "rest_seconds"
        const val DEFAULT_REST_SECONDS = 60
        const val REP_TONE = "rep_tone"
        const val REP_VIBRATE = "rep_vibrate"
        const val SPOKEN_COUNTS = "spoken_counts"
        const val INTRODUCED = "introduced"
        const val WEEKLY_GOAL_DAYS = "weekly_goal_days"
        const val REMINDERS_ON = "reminders_on"
        const val REMINDER_WEEKDAYS = "reminder_weekdays"
        const val REMINDER_MINUTE = "reminder_minute"
        const val DEFAULT_REMINDER_MINUTE = 18 * 60
    }
}
