package com.kinesis.core.analysis

import com.kinesis.core.analysis.kinematics.JointAngleId
import com.kinesis.core.analysis.kinematics.MeasurementPlane
import com.kinesis.core.analysis.profile.AnalysedSides
import com.kinesis.core.analysis.profile.Contraction
import com.kinesis.core.analysis.profile.RepSignalSpec
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.analysis.profile.RepetitionSemantics
import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.core.domain.profile.CameraView
import com.kinesis.core.domain.profile.Posture
import com.kinesis.core.domain.profile.TierAProfile

/**
 * A test-only profile: the squat as it was until DECISIONS.md P3-68, both knees paired.
 *
 * P3-68 made the shipped squat count the near knee only. Until then it was the profile every test
 * of the paired path used - pairing, asymmetry, paired calibration, Layer 1's asymmetry rule - and
 * those tests drive knee angles. The paired path itself is unchanged and still ships (the lateral
 * raise, glute bridge, lunge, shoulder press and leg raise use it), so its tests stay, on this
 * fixture, with their synthetic data untouched. It is not in `ExerciseLibrary` and never reaches
 * the app.
 */
class PairedKneeFixture : TierAProfile(), RepetitionExercise {

    override val id: String = "paired_knee_fixture"
    override val displayName: String = "Paired knee fixture"
    override val requiredView: CameraView = CameraView.SIDE_ON
    override val posture: Posture = Posture.STANDING

    override val repetitionSemantics: RepetitionSemantics = RepetitionSemantics.SIMULTANEOUS
    override val analysedSides: AnalysedSides = AnalysedSides.BOTH
    override val awayFromStart: Contraction = Contraction.ECCENTRIC

    override val repSignal: RepSignalSpec = RepSignalSpec(
        joint = JointAngleId.KNEE,
        plane = MeasurementPlane.IMAGE_2D,
        topThreshold = 160.0,
        bottomThreshold = 90.0,
        hysteresis = 5.0,
        minRepDurationMs = 600.0,
        maxRepDurationMs = 15_000.0,
    )

    override val requiredLandmarks: RequiredLandmarks = RequiredLandmarks.of(
        LandmarkId.LEFT_HIP, LandmarkId.RIGHT_HIP,
        LandmarkId.LEFT_KNEE, LandmarkId.RIGHT_KNEE,
        LandmarkId.LEFT_ANKLE, LandmarkId.RIGHT_ANKLE,
    )

    companion object {
        val instance: PairedKneeFixture = PairedKneeFixture()
    }
}
