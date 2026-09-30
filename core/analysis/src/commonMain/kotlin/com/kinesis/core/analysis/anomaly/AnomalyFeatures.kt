package com.kinesis.core.analysis.anomaly

import com.kinesis.core.analysis.deviation.DeviationPolicy
import com.kinesis.core.analysis.deviation.RepFeatures
import com.kinesis.core.analysis.profile.RepetitionExercise
import com.kinesis.core.domain.bilateral.Side
import com.kinesis.core.domain.rep.RepRecord

/**
 * Which history a repetition belongs to - DECISIONS.md P3-34: its exercise, the side it was
 * attributed to, and the sides it measured. An alternating curl's two arms, or a near-side
 * exercise filmed from either side, never share a model: their features are not the same
 * quantities.
 */
data class AnomalyKey(
    val exerciseId: String,
    val attributedSide: Side?,
    val measuresLeft: Boolean,
    val measuresRight: Boolean,
)

/**
 * What Layer 4 sees of one completed repetition - DECISIONS.md P3-34.
 *
 * - **F1, Layer 1's scalars** ([RepFeatures]): each measured side's range of motion, the lifting
 *   and lowering time, and left minus right when both sides were measured.
 * - **F2, the shape:** each measured side's resampled curve, measured from its start value
 *   towards the far position and divided by its own range of motion, so the shape is kept apart
 *   from the size. It is averaged down to [SHAPE_POINTS] points.
 *
 * Sides stay apart throughout (CLAUDE.md section 3). Post-rep path: once per repetition.
 */
object AnomalyFeatures {

    const val SHAPE_POINTS: Int = 16

    fun keyOf(profile: RepetitionExercise, rep: RepRecord): AnomalyKey = AnomalyKey(
        exerciseId = profile.id,
        attributedSide = rep.attributedSide,
        measuresLeft = rep.rangeOfMotion.left.isFinite(),
        measuresRight = rep.rangeOfMotion.right.isFinite(),
    )

    /** F1, then F2 if [withShape]; `null` if any of them was not measured. */
    fun of(profile: RepetitionExercise, rep: RepRecord, withShape: Boolean): DoubleArray? {
        val key = keyOf(profile, rep)
        val scalars = RepFeatures.of(profile, rep)
        val values = ArrayList<Double>()
        if (key.measuresLeft) values += scalars.rangeOfMotion.left
        if (key.measuresRight) values += scalars.rangeOfMotion.right
        values += scalars.liftingMs
        values += scalars.loweringMs
        if (key.measuresLeft && key.measuresRight) values += scalars.leftMinusRight
        if (withShape) {
            if (key.measuresLeft) addShape(profile, rep.resampledLeft, rep.rangeOfMotion.left, values)
            if (key.measuresRight) addShape(profile, rep.resampledRight, rep.rangeOfMotion.right, values)
        }
        if (values.any { !it.isFinite() }) return null
        return values.toDoubleArray()
    }

    /**
     * The floor under each feature's scale, in the order [of] builds them - DECISIONS.md P3-36.
     * Layer 1's floors for F1; [SHAPE_FLOOR] for every shape point, which has no floor elsewhere.
     */
    fun floorsOf(key: AnomalyKey, withShape: Boolean, policy: DeviationPolicy = DeviationPolicy.PROVISIONAL): DoubleArray {
        val floors = ArrayList<Double>()
        if (key.measuresLeft) floors += policy.rangeOfMotionFloorDeg
        if (key.measuresRight) floors += policy.rangeOfMotionFloorDeg
        floors += policy.timeFloorMs
        floors += policy.timeFloorMs
        if (key.measuresLeft && key.measuresRight) floors += policy.asymmetryFloorDeg
        if (withShape) {
            if (key.measuresLeft) repeat(SHAPE_POINTS) { floors += SHAPE_FLOOR }
            if (key.measuresRight) repeat(SHAPE_POINTS) { floors += SHAPE_FLOOR }
        }
        return floors.toDoubleArray()
    }

    /** 2% of the side's own range: fixed in P3-36, before any run. */
    const val SHAPE_FLOOR: Double = 0.02

    /** One side's curve: from its start, towards the far position, over its range, in [SHAPE_POINTS]. */
    private fun addShape(profile: RepetitionExercise, curve: DoubleArray, rangeOfMotion: Double, into: MutableList<Double>) {
        if (curve.isEmpty() || curve.size % SHAPE_POINTS != 0 || !(rangeOfMotion > 0.0)) {
            into += Double.NaN
            return
        }
        val towardsFar = if (profile.repSignal.topIsGreater) -1.0 else 1.0
        val start = curve[0]
        val perPoint = curve.size / SHAPE_POINTS
        for (point in 0 until SHAPE_POINTS) {
            var sum = 0.0
            for (i in 0 until perPoint) sum += (curve[point * perPoint + i] - start) * towardsFar / rangeOfMotion
            into += sum / perPoint
        }
    }
}
