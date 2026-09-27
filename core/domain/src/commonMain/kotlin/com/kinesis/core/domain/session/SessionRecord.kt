package com.kinesis.core.domain.session

import com.kinesis.core.domain.rep.HoldRecord
import com.kinesis.core.domain.rep.HoldValidity
import com.kinesis.core.domain.rep.RepRecord
import com.kinesis.core.domain.rep.RepValidity
import kotlin.math.floor

/**
 * A calendar day, as a count of days since 1970-01-01 in some local offset.
 *
 * ### Why not a date type
 *
 * FINAL_ARCHITECTURE section 3 lists "No date/time, no locale, no file system"
 * among the properties that make this core good multiplatform material, and
 * names structured concurrency, HTTP and database drivers as the things that
 * would have made Kotlin/JS a bad idea. A timezone database is the same kind of
 * dependency, and none is needed: the platform records the UTC offset in force
 * when a session happened, and everything after that is integer arithmetic.
 *
 * ### Why the offset is captured rather than looked up
 *
 * The day a session belongs to is fixed when the session happens. Reading the
 * device's *current* offset to bucket a past session means a user who travels
 * silently rewrites their own history - a set performed on Tuesday evening in
 * Auckland becomes Tuesday morning, or Monday, depending where they are when
 * they next open the app. DECISIONS.md P3-10.
 */
data class LocalDay(val dayNumber: Int) : Comparable<LocalDay> {

    override fun compareTo(other: LocalDay): Int = dayNumber.compareTo(other.dayNumber)

    fun plusDays(days: Int): LocalDay = LocalDay(dayNumber + days)

    /** Days from this one to [other]. Negative when [other] is earlier. */
    fun daysUntil(other: LocalDay): Int = other.dayNumber - dayNumber

    /**
     * This day in the proleptic Gregorian calendar, as `YYYY-MM-DD`.
     *
     * Integer arithmetic only, for the same reason this class exists: no
     * calendar library in the core. Howard Hinnant's `civil_from_days`, whose
     * divisions are floored so days before 1970 come out right too.
     */
    fun toIsoDate(): String {
        val z = dayNumber + 719_468
        val era = z.floorDiv(146_097)
        val dayOfEra = z - era * 146_097
        val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val shiftedMonth = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * shiftedMonth + 2) / 5 + 1
        val month = if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9
        val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
        return year.toString().padStart(4, '0') + "-" +
            month.toString().padStart(2, '0') + "-" +
            day.toString().padStart(2, '0')
    }

    override fun toString(): String = "LocalDay(" + dayNumber + ")"

    companion object {

        const val MILLIS_PER_DAY: Double = 86_400_000.0

        /**
         * The local calendar day an instant falls on, given the UTC offset in
         * force at the time.
         *
         * A **floor**, not a truncation. The two agree for every timestamp after
         * 1970 and disagree for every one before it, so a truncation passes every
         * plausible test and then quietly folds two days into one the first time
         * a device clock is wrong.
         */
        fun of(epochMs: Double, utcOffsetMinutes: Int): LocalDay {
            require(epochMs.isFinite()) { "a session needs a real timestamp, got " + epochMs }
            val local = epochMs + utcOffsetMinutes * 60_000.0
            return LocalDay(floor(local / MILLIS_PER_DAY).toInt())
        }
    }
}

/**
 * One set of one exercise - CLAUDE.md section 15's middle level.
 *
 * `sealed`, with a variant per analysis mode, so a set cannot carry both
 * repetitions and holds. That is the same separation CLAUDE.md section 11
 * requires of the analysers, applied to the records they produce: a plank set
 * with somewhere to put a repetition count is a plank set something will
 * eventually put one in.
 */
sealed interface SetRecord {

    /** The profile this set was performed against, e.g. `squat`. */
    val exerciseId: String

    val startedAtEpochMs: Double

    /** Completed work in this set, by the definition the streak rule uses. */
    val completedUnits: Int
}

/** A set of a repetition exercise. */
class RepetitionSetRecord(
    override val exerciseId: String,
    override val startedAtEpochMs: Double,
    val reps: List<RepRecord>,
) : SetRecord {

    /** Repetitions that actually completed. Aborted and incomplete ones are not work. */
    val validReps: List<RepRecord> get() = reps.filter { it.validity == RepValidity.VALID }

    override val completedUnits: Int get() = validReps.size

    override fun toString(): String =
        "RepetitionSetRecord(" + exerciseId + ", " + completedUnits + "/" + reps.size + " reps)"
}

/** A set of an isometric exercise. Carries no repetition count, deliberately. */
class HoldSetRecord(
    override val exerciseId: String,
    override val startedAtEpochMs: Double,
    val holds: List<HoldRecord>,
) : SetRecord {

    /** Attempts that became holds. A too-short attempt is not work. */
    val validHolds: List<HoldRecord> get() = holds.filter { it.validity == HoldValidity.VALID }

    override val completedUnits: Int get() = validHolds.size

    override fun toString(): String =
        "HoldSetRecord(" + exerciseId + ", " + completedUnits + "/" + holds.size + " holds)"
}

/**
 * One training session - the top of CLAUDE.md section 15's hierarchy.
 *
 * Measurement, not interpretation. It records what happened and when; whether
 * that was a good session is an `AnalysisResult`, and whether it extended a
 * streak is [com.kinesis.core.analysis.history.Streaks]' business. Keeping the
 * threshold out of the record is what lets the rule change later without
 * rewriting history.
 */
class SessionRecord(
    val sessionId: String,
    val startedAtEpochMs: Double,

    /**
     * Minutes east of UTC in force when the session started, as the platform
     * reported it. Stored rather than derived - see [LocalDay].
     */
    val utcOffsetMinutes: Int,

    val sets: List<SetRecord>,
) {

    /** The calendar day this session belongs to, and always will. */
    val localDay: LocalDay get() = LocalDay.of(startedAtEpochMs, utcOffsetMinutes)

    /** Repetitions and holds that completed, across every set. */
    val completedUnits: Int get() = sets.sumOf { it.completedUnits }

    override fun toString(): String =
        "SessionRecord(" + sessionId + ", " + localDay +
            ", " + sets.size + " sets, " + completedUnits + " completed)"
}
