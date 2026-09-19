package com.kinesis.core.domain.landmark

/**
 * The 33 MediaPipe Pose landmarks, in MediaPipe's own index order.
 *
 * CLAUDE.md section 14: the analysis engine consumes MediaPipe landmarks only.
 * MM-Fit's bundled OpenPose skeletons are a different joint format - 17 joints,
 * OpenPose-derived - and are not interchangeable with these. MM-Fit evaluation
 * runs its RGB video through MediaPipe Pose first.
 *
 * **These names are anatomical, always.** CONTRACTS.md section 4: [LEFT_KNEE] is
 * the subject's left knee, regardless of which side of the image it appeared on.
 * Converting camera coordinates into this frame is the platform pose adapter's
 * job, and it happens before anything reaches the shared core.
 *
 * The ordinal is the MediaPipe index and is load-bearing: the JS facade takes
 * flat `DoubleArray`s in this order, golden files are recorded in it, and
 * persisted sessions are decoded with it. Reordering these entries silently
 * changes the meaning of every stored recording.
 */
enum class LandmarkId {
    NOSE,
    LEFT_EYE_INNER,
    LEFT_EYE,
    LEFT_EYE_OUTER,
    RIGHT_EYE_INNER,
    RIGHT_EYE,
    RIGHT_EYE_OUTER,
    LEFT_EAR,
    RIGHT_EAR,
    MOUTH_LEFT,
    MOUTH_RIGHT,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_PINKY,
    RIGHT_PINKY,
    LEFT_INDEX,
    RIGHT_INDEX,
    LEFT_THUMB,
    RIGHT_THUMB,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
    LEFT_HEEL,
    RIGHT_HEEL,
    LEFT_FOOT_INDEX,
    RIGHT_FOOT_INDEX,
    ;

    /** The MediaPipe index. Identical to [ordinal], named for what it means. */
    val index: Int get() = ordinal

    /**
     * The same body part on the other side, or this landmark itself if it sits
     * on the midline.
     *
     * This is anatomy, not camera geometry. It says which landmark is the
     * opposite-side counterpart and knows nothing about lenses or mirroring. The
     * platform pose adapter uses it when undoing a mirrored front-camera frame,
     * but the pairing itself is equally true of an unmirrored one.
     */
    val mirrored: LandmarkId
        get() = when (this) {
            NOSE -> NOSE
            LEFT_EYE_INNER -> RIGHT_EYE_INNER
            LEFT_EYE -> RIGHT_EYE
            LEFT_EYE_OUTER -> RIGHT_EYE_OUTER
            RIGHT_EYE_INNER -> LEFT_EYE_INNER
            RIGHT_EYE -> LEFT_EYE
            RIGHT_EYE_OUTER -> LEFT_EYE_OUTER
            LEFT_EAR -> RIGHT_EAR
            RIGHT_EAR -> LEFT_EAR
            MOUTH_LEFT -> MOUTH_RIGHT
            MOUTH_RIGHT -> MOUTH_LEFT
            LEFT_SHOULDER -> RIGHT_SHOULDER
            RIGHT_SHOULDER -> LEFT_SHOULDER
            LEFT_ELBOW -> RIGHT_ELBOW
            RIGHT_ELBOW -> LEFT_ELBOW
            LEFT_WRIST -> RIGHT_WRIST
            RIGHT_WRIST -> LEFT_WRIST
            LEFT_PINKY -> RIGHT_PINKY
            RIGHT_PINKY -> LEFT_PINKY
            LEFT_INDEX -> RIGHT_INDEX
            RIGHT_INDEX -> LEFT_INDEX
            LEFT_THUMB -> RIGHT_THUMB
            RIGHT_THUMB -> LEFT_THUMB
            LEFT_HIP -> RIGHT_HIP
            RIGHT_HIP -> LEFT_HIP
            LEFT_KNEE -> RIGHT_KNEE
            RIGHT_KNEE -> LEFT_KNEE
            LEFT_ANKLE -> RIGHT_ANKLE
            RIGHT_ANKLE -> LEFT_ANKLE
            LEFT_HEEL -> RIGHT_HEEL
            RIGHT_HEEL -> LEFT_HEEL
            LEFT_FOOT_INDEX -> RIGHT_FOOT_INDEX
            RIGHT_FOOT_INDEX -> LEFT_FOOT_INDEX
        }

    companion object {

        /** MediaPipe Pose emits exactly this many landmarks per frame. */
        const val COUNT: Int = 33

        fun fromIndex(index: Int): LandmarkId {
            require(index in 0 until COUNT) {
                "landmark index out of range: " + index + ", expected 0 until " + COUNT
            }
            return entries[index]
        }
    }
}
