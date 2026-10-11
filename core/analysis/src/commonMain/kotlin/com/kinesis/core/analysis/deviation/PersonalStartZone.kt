package com.kinesis.core.analysis.deviation

import com.kinesis.core.domain.profile.ExerciseProfile
import com.kinesis.core.domain.calibration.CalibrationBaseline
import com.kinesis.core.analysis.profile.RepSignalSpec
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.calibration.RepetitionBaseline

/** Where a start zone's edge came from. */
enum class StartZoneSource {
    /** The profile's own, population-set edge. */
    PROFILE,

    /** The user's calibration. */
    PERSONAL,

    /** The floor: the loosest edge allowed, from development data. */
    FLOOR,
}

/** A start zone: the angle at or above which the push-up or sit-up is at its start. */
class StartZone(val edge: Double, val source: StartZoneSource)

/**
 * The push-up's and sit-up's personal start zone - the P3-29 proposal, built in
 * DECISIONS.md P3-30.
 *
 * On MM-Fit the start of these two depends on the person: the push-up's 2D elbow
 * reading at the top runs from about 136 to 166 degrees between subjects, and one
 * fixed edge cannot hold both without admitting partial repetitions (P3-29). So the
 * edge comes from the user's own calibration tops - but **never stricter than the
 * profile's own edge, and never looser than a floor** that keeps a real minimum
 * sweep, so a shallow calibration cannot loosen it into counting partial
 * repetitions.
 *
 * **Used by the app since DECISIONS.md P3-82**, through [countingProfile], after it passed on
 * MM-Fit's development and held-out people by a protocol fixed before either ran.
 */
object PersonalStartZone {

    /**
     * The profiles it serves. P3-29 proposed it for the push-up and the sit-up; the sit-up left in
     * DECISIONS.md P3-46, when its trunk-angle driver made a start zone on the hip angle moot.
     */
    val PROFILE_IDS: Set<String> = setOf("pushup")

    /**
     * The floors, degrees - DECISIONS.md P3-30, derived from the development subjects
     * only (`tools/mmfit-eval/start_zone_floor.py`) by a method fixed before it ran:
     * the depth edge plus half the smallest development subject's median near-side
     * sweep. Push-up: 95 + 62.1 / 2 (subject B). Sit-up: 83 + 62.9 / 2 (subject A).
     * Provisional, as every threshold here is.
     */
    private val FLOOR_EDGE: Map<String, Double> = mapOf("pushup" to 126.0)

    /** The loosest start edge the profile may have. */
    fun floorEdge(profile: RepetitionExercise): Double =
        FLOOR_EDGE[profile.id] ?: throw IllegalArgumentException("${profile.id} has no personal start zone (P3-30)")

    /** The start zone for this user: personal, clamped between the floor and the profile's own edge. */
    fun of(profile: RepetitionExercise, baseline: RepetitionBaseline?): StartZone {
        val floor = floorEdge(profile)
        val spec = profile.repSignal
        require(spec.topIsGreater) { "${profile.id}: a start zone 'at or above' needs the start to be the larger angle" }
        val profileEdge = spec.topThreshold - spec.hysteresis
        val personal = baseline?.let { personalEdge(it, spec.hysteresis) } ?: Double.NaN
        return when {
            personal.isNaN() || personal >= profileEdge -> StartZone(profileEdge, StartZoneSource.PROFILE)
            personal <= floor -> StartZone(floor, StartZoneSource.FLOOR)
            else -> StartZone(personal, StartZoneSource.PERSONAL)
        }
    }

    /** The profile with its start zone at [zone] and nothing else changed. */
    fun profileWith(profile: RepetitionExercise, zone: StartZone): RepetitionExercise {
        floorEdge(profile)
        val signal = profile.repSignal.copy(topThreshold = zone.edge + profile.repSignal.hysteresis)
        return WithStartZone(profile, signal)
    }

    /**
     * The profile a calibration set of these two counts with: the floor zone. With
     * the profile's own edge, a person whose tops read low would never complete the
     * calibration repetitions the personal zone is built from.
     */
    fun forCalibration(profile: RepetitionExercise): RepetitionExercise =
        profileWith(profile, StartZone(floorEdge(profile), StartZoneSource.FLOOR))

    /**
     * The user's usual top less a margin: the calibration's median start-position
     * angle minus the larger of its IQR and the hysteresis, on the side calibrated
     * from more repetitions - these are near-side-only profiles, so usually the one
     * side there is. On a tie, the stricter (higher) edge: a named choice, never an
     * average of the two sides.
     */
    private fun personalEdge(baseline: RepetitionBaseline, hysteresis: Double): Double {
        val left = baseline.peakValue.left
        val right = baseline.peakValue.right
        val leftEdge = if (left.isMeasured) left.median - maxOf(left.interquartileRange, hysteresis) else Double.NaN
        val rightEdge = if (right.isMeasured) right.median - maxOf(right.interquartileRange, hysteresis) else Double.NaN
        return when {
            leftEdge.isNaN() -> rightEdge
            rightEdge.isNaN() -> leftEdge
            left.count > right.count -> leftEdge
            right.count > left.count -> rightEdge
            else -> maxOf(leftEdge, rightEdge)
        }
    }

    /**
     * The profile a live set counts with - DECISIONS.md P3-82. A push-up calibration set counts with
     * the floor zone; a calibrated push-up with the user's own zone; anything else as it is.
     * [baseline] is the one stored for this exercise, or `null`.
     */
    fun countingProfile(profile: ExerciseProfile, baseline: CalibrationBaseline?, calibrating: Boolean): ExerciseProfile {
        if (profile !is RepetitionExercise || profile.id !in PROFILE_IDS) return profile
        if (calibrating) return forCalibration(profile)
        val personal = baseline as? RepetitionBaseline ?: return profile
        val zone = of(profile, personal)
        return if (zone.source == StartZoneSource.PROFILE) profile else profileWith(profile, zone)
    }

    /**
     * The library profile behind a personalised one, or [profile] itself. What the technique rules
     * and verdicts judge, so a personalised push-up is still the Tier A push-up (P3-82).
     */
    fun baseOf(profile: ExerciseProfile): ExerciseProfile = (profile as? WithStartZone)?.base ?: profile

    /** A profile whose start zone is personal: everything else is the profile's own, by delegation. */
    private class WithStartZone(
        val base: RepetitionExercise,
        override val repSignal: RepSignalSpec,
    ) : RepetitionExercise by base {
        override fun toString(): String = "WithStartZone(${base.id}, start ${repSignal.topThreshold - repSignal.hysteresis})"
    }
}
