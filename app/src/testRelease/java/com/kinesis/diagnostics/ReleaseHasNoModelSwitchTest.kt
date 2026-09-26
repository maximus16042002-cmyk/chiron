package com.kinesis.diagnostics

import com.kinesis.camera.PoseModelAsset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Release builds keep the lite pose model only - DECISIONS.md P3-21. Runs in
 * `testReleaseUnitTest`.
 */
class ReleaseHasNoModelSwitchTest {

    @Test
    fun a_release_build_offers_no_model_to_switch_to_even_if_one_were_present() {
        assertEquals(
            emptyList<String>(),
            switchablePoseModels(setOf(PoseModelAsset.LITE, PoseModelAsset.FULL, PoseModelAsset.HEAVY)),
        )
    }
}
