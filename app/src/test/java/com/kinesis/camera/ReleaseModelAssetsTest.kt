package com.kinesis.camera

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Release builds carry the lite pose model and no other - DECISIONS.md P3-21.
 *
 * The full model may sit in `src/debug/assets` for the debug-only switch. A copy
 * in `src/main/assets` or `src/release/assets` would ship in the release APK,
 * so this reads both folders, as the build would.
 */
class ReleaseModelAssetsTest {

    @Test
    fun the_only_pose_model_a_release_build_can_package_is_lite() {
        val shipped = listOf("src/main/assets", "src/release/assets")
            .map(::File)
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles().orEmpty().map { it.name } }
            .filter { it.endsWith(".task") }
        assertEquals(listOf(PoseModelAsset.LITE), shipped)
    }
}
