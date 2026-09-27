package com.kinesis.calibration

import com.kinesis.core.analysis.calibration.Calibration
import com.kinesis.core.analysis.calibration.CalibrationKey
import com.kinesis.core.analysis.calibration.CalibrationOutcome
import com.kinesis.core.analysis.calibration.PostRepAnalysis
import com.kinesis.core.analysis.profile.IsometricExercise
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.analysis.AnalysisResult
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.domain.calibration.HoldBaseline
import com.kinesis.core.domain.calibration.RepetitionBaseline
import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.session.HoldSetRecord
import com.kinesis.core.domain.session.RepetitionSetRecord
import com.kinesis.core.domain.session.SessionRecord
import com.kinesis.storage.BaselineRepository

/**
 * The app's side of calibration - DECISIONS.md P3-22. It decides nothing: the
 * baseline and the comparison are computed by the shared core (CLAUDE.md
 * section 2); this stores the one and looks up the other.
 */
class CalibrationCoordinator(private val baselines: BaselineRepository) {

    /**
     * Fits a baseline to a finished calibration set and, if one results, saves it
     * as the user's baseline for the exercise, replacing any earlier one. If none
     * results, the earlier baseline is left exactly as it was.
     *
     * A live set is saved as its own session (P3-16), so [session] holds the one
     * calibration set.
     */
    suspend fun finishCalibration(
        ownerUid: String,
        profile: ExerciseProfile,
        session: SessionRecord,
    ): CalibrationOutcome {
        val set = session.sets.single()
        val outcome = Calibration.fromSet(
            profile = profile,
            set = set,
            createdAtEpochMs = set.startedAtEpochMs,
            sourceSessionId = session.sessionId,
        )
        if (outcome is CalibrationOutcome.Calibrated) baselines.save(ownerUid, outcome.baseline)
        return outcome
    }

    /** The user's baseline for [profile], a variant's being its parent's. */
    suspend fun baselineFor(ownerUid: String, profile: ExerciseProfile): CalibrationBaseline? =
        baselines.baselinesFor(ownerUid)[CalibrationKey.of(profile)]

    /**
     * Every record of a finished set beside the baseline, if there is one - the
     * set summary. `AnalysisResult`s are computed here, from the stored records,
     * and never stored with them (CLAUDE.md section 15).
     */
    fun analyseSet(profile: ExerciseProfile, session: SessionRecord, baseline: CalibrationBaseline?): List<AnalysisResult> =
        session.sets.flatMap { set ->
            when {
                set is RepetitionSetRecord && profile is RepetitionExercise ->
                    set.reps.map { PostRepAnalysis.analyse(profile, it, baseline as? RepetitionBaseline) }
                set is HoldSetRecord && profile is IsometricExercise ->
                    set.holds.map { PostRepAnalysis.analyse(profile, it, baseline as? HoldBaseline) }
                else -> emptyList()
            }
        }
}
