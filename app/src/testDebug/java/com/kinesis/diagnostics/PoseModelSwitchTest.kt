package com.kinesis.diagnostics

import com.kinesis.camera.PoseModelAsset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The debug-only switch between the lite and full pose models - DECISIONS.md
 * P3-21. Runs in `testDebugUnitTest`; its release twin is pinned by
 * `ReleaseHasNoModelSwitchTest`.
 */
class PoseModelSwitchTest {

    @Test
    fun a_debug_build_carrying_both_models_offers_lite_then_full() {
        assertEquals(
            listOf(PoseModelAsset.LITE, PoseModelAsset.FULL),
            switchablePoseModels(setOf(PoseModelAsset.FULL, "licenses", PoseModelAsset.LITE)),
        )
    }

    /** The full model is not committed; a build without it has nothing to switch to. */
    @Test
    fun without_the_full_model_there_is_only_lite() {
        assertEquals(listOf(PoseModelAsset.LITE), switchablePoseModels(setOf(PoseModelAsset.LITE)))
    }

    /** The owner decided on lite against full. The heavy model is not part of it. */
    @Test
    fun the_heavy_model_is_never_offered() {
        val offered = switchablePoseModels(setOf(PoseModelAsset.LITE, PoseModelAsset.FULL, PoseModelAsset.HEAVY))
        assertEquals(listOf(PoseModelAsset.LITE, PoseModelAsset.FULL), offered)
    }
}
