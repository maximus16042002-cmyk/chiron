package com.kinesis.core.analysis.verifier

/**
 * Model parameters for the exercise mismatch verifier (P3-34, P3-52).
 *
 * Encapsulates the weights and normalization factors of a trained multinomial logistic regression
 * classifier over the 145-dimensional [MovementContext].
 */
data class ExerciseVerifierModel(
    val modelVersion: String,
    val classes: List<String>,
    val featureNames: List<String>,
    val scalerMean: DoubleArray,
    val scalerScale: DoubleArray,
    val intercepts: DoubleArray,
    val weights: Array<DoubleArray>,
    val selectedPMax: Double = DEFAULT_SELECTED_P_MAX,
    val predictedPMin: Double = DEFAULT_PREDICTED_P_MIN,
) {
    init {
        require(classes.isNotEmpty()) { "classes must not be empty" }
        require(featureNames.size == FEATURE_COUNT) {
            "featureNames size (${featureNames.size}) must equal FEATURE_COUNT ($FEATURE_COUNT)"
        }
        require(scalerMean.size == FEATURE_COUNT) {
            "scalerMean size (${scalerMean.size}) must equal FEATURE_COUNT ($FEATURE_COUNT)"
        }
        require(scalerScale.size == FEATURE_COUNT) {
            "scalerScale size (${scalerScale.size}) must equal FEATURE_COUNT ($FEATURE_COUNT)"
        }
        require(intercepts.size == classes.size) {
            "intercepts size (${intercepts.size}) must match classes count (${classes.size})"
        }
        require(weights.size == classes.size) {
            "weights size (${weights.size}) must match classes count (${classes.size})"
        }
        for (i in weights.indices) {
            require(weights[i].size == FEATURE_COUNT) {
                "weights[$i] size (${weights[i].size}) must equal FEATURE_COUNT ($FEATURE_COUNT)"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExerciseVerifierModel) return false
        if (modelVersion != other.modelVersion) return false
        if (classes != other.classes) return false
        if (featureNames != other.featureNames) return false
        if (!scalerMean.contentEquals(other.scalerMean)) return false
        if (!scalerScale.contentEquals(other.scalerScale)) return false
        if (!intercepts.contentEquals(other.intercepts)) return false
        if (weights.size != other.weights.size) return false
        for (i in weights.indices) {
            if (!weights[i].contentEquals(other.weights[i])) return false
        }
        if (selectedPMax != other.selectedPMax) return false
        if (predictedPMin != other.predictedPMin) return false
        return true
    }

    override fun hashCode(): Int {
        var result = modelVersion.hashCode()
        result = 31 * result + classes.hashCode()
        result = 31 * result + scalerMean.contentHashCode()
        result = 31 * result + scalerScale.contentHashCode()
        result = 31 * result + intercepts.contentHashCode()
        return result
    }

    companion object {
        const val FEATURE_COUNT: Int = 145
        const val DEFAULT_SELECTED_P_MAX: Double = 0.25
        const val DEFAULT_PREDICTED_P_MIN: Double = 0.50
    }
}
