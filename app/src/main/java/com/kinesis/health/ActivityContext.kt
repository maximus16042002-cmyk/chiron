package com.kinesis.health

import com.kinesis.core.domain.session.LocalDay

/**
 * Read-only daily activity context, from the platform's on-device health store -
 * DECISIONS.md P3-3 and P3-11.
 *
 * ### What this is for, and what it must never become
 *
 * P3-3: Health Connect data is "displayed alongside Kinesis's own measurements
 * rather than replacing them". It contextualises a technique measurement within
 * the day it was performed in - was this a sedentary day, was the user's resting
 * heart rate elevated - and it never feeds an analysis decision. Nothing in
 * `:core:domain` or `:core:analysis` knows this package exists, and nothing in
 * Kinesis's own measurement path depends on it.
 *
 * That is why absence is modelled as a first-class result rather than an
 * exception or a null: on a device with no Health Connect, with permissions
 * denied, or running an Android version too old to have it, the app must behave
 * exactly as it does today. A type that can only be constructed by saying which
 * of those happened is how that stays true.
 *
 * ### No analysis logic here
 *
 * CLAUDE.md section 2: platform code is limited to acquisition. This file
 * defines shapes and nothing else - no thresholds, no judgements, no
 * aggregation. The one core type it borrows is [LocalDay], so a day means the
 * same thing here as it does to the streak rule (P3-10).
 */

/**
 * One metric on one day.
 *
 * Three cases, because they mean different things to a reader and the UI should
 * say different things about them. "No steps recorded" and "you did not grant
 * permission to read steps" are not the same sentence, and collapsing both to
 * `null` would make them one.
 */
sealed interface DailyMetric {

    /** A real reading. */
    data class Measured(val value: Double) : DailyMetric

    /** The user did not grant permission for this metric. */
    data object NotPermitted : DailyMetric

    /** Permission was granted, and the store held nothing for that day. */
    data object NoData : DailyMetric

    val valueOrNull: Double? get() = (this as? Measured)?.value
}

/**
 * The three metrics pinned in DECISIONS.md P3-11, for one local day.
 *
 * P3-3 named "steps, active calories, heart rate" as examples with the "exact
 * set to be pinned when this is built". These three are that set, and P3-11
 * records why each earns its place and why the obvious fourth - Health Connect's
 * own exercise sessions - is deliberately excluded.
 */
data class DailyActivityContext(
    val day: LocalDay,

    /** Steps taken, whole-day total. */
    val steps: DailyMetric,

    /** Active energy expenditure in kilocalories, whole-day total. */
    val activeCaloriesKcal: DailyMetric,

    /** Mean heart rate in beats per minute across the day. */
    val meanHeartRateBpm: DailyMetric,
) {
    /** True when nothing at all could be read, however permissions fell. */
    val isEmpty: Boolean
        get() = steps !is DailyMetric.Measured &&
            activeCaloriesKcal !is DailyMetric.Measured &&
            meanHeartRateBpm !is DailyMetric.Measured
}

/** Why no context is available. Each case is a different sentence to the user. */
enum class ContextUnavailable {

    /**
     * The device's Android version predates Health Connect. Kinesis supports
     * API 24; Health Connect needs 26 at the library level and a provider that
     * only exists from Android 9. Nothing to offer and nothing to prompt for.
     */
    PLATFORM_TOO_OLD,

    /** Health Connect is not present on this device and is not installable here. */
    NOT_INSTALLED,

    /** Present, but the provider needs updating before it can be used. */
    PROVIDER_UPDATE_REQUIRED,

    /** Available, but the user granted none of the requested read permissions. */
    PERMISSIONS_DENIED,

    /** Available and permitted, but the read itself failed. */
    READ_FAILED,
}

/**
 * The result of asking for a day's context.
 *
 * Deliberately not `DailyActivityContext?`. A null would say "nothing here" and
 * lose the reason, and the reason is the whole difference between "install
 * Health Connect to see this" and "you denied this permission" and "your phone
 * is too old for this feature".
 */
sealed interface ActivityContextResult {

    data class Available(val context: DailyActivityContext) : ActivityContextResult

    data class Unavailable(val reason: ContextUnavailable) : ActivityContextResult
}

/** Whether the platform can serve context at all, before any read is attempted. */
sealed interface ContextAvailability {

    /** Health Connect is usable. [grantedPermissions] may still be empty. */
    data class Available(val grantedPermissions: Set<String>) : ContextAvailability

    data class Unavailable(val reason: ContextUnavailable) : ContextAvailability
}

/**
 * A source of daily activity context.
 *
 * An interface so the read path is unit-testable without a device, an emulator,
 * or Health Connect installed - which is most of what "tested" can mean for this
 * batch (P3-11). The Health Connect implementation is one of two; the other is
 * the fake the tests drive.
 */
interface ActivityContextSource {

    /** Whether context can be served, and which permissions are held. */
    suspend fun availability(): ContextAvailability

    /**
     * Context for one local day.
     *
     * Never throws for an expected condition - a missing provider, a denied
     * permission and an empty day are all results, not exceptions.
     */
    suspend fun readDay(day: LocalDay, utcOffsetMinutes: Int): ActivityContextResult
}

/** The read permissions P3-11 pins. Requested together, granted individually. */
object ActivityContextPermissions {

    const val READ_STEPS: String = "android.permission.health.READ_STEPS"
    const val READ_ACTIVE_CALORIES: String =
        "android.permission.health.READ_ACTIVE_CALORIES_BURNED"
    const val READ_HEART_RATE: String = "android.permission.health.READ_HEART_RATE"

    /**
     * Everything Kinesis asks for, and nothing else.
     *
     * Read-only by construction: there is no write permission in this set, and
     * P3-3's "read-only context" is enforced by never requesting one.
     */
    val ALL: Set<String> = setOf(READ_STEPS, READ_ACTIVE_CALORIES, READ_HEART_RATE)
}
