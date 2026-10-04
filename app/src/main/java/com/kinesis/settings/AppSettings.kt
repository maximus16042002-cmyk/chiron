package com.kinesis.settings

import android.content.Context
import com.kinesis.core.analysis.history.WeeklyGoal

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

    private companion object {
        const val FILE = "kinesis"
        const val INTRODUCED = "introduced"
        const val WEEKLY_GOAL_DAYS = "weekly_goal_days"
    }
}
