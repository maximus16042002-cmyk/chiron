package com.kinesis.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The debug-only full-model switch - DECISIONS.md P3-21. The live screen uses
 * the full model only when the owner has pushed one; anything else keeps lite.
 */
class DebugPoseModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun no_external_files_directory_keeps_the_bundled_model() {
        assertNull(debugPoseModelOverride(null))
    }

    @Test
    fun no_pushed_file_keeps_the_bundled_model() {
        assertNull(debugPoseModelOverride(folder.root))
    }

    @Test
    fun an_empty_pushed_file_keeps_the_bundled_model() {
        File(folder.newFolder("bench"), FULL_MODEL_FILE).createNewFile()
        assertNull(debugPoseModelOverride(folder.root))
    }

    @Test
    fun a_pushed_full_model_is_used_by_its_absolute_path() {
        val file = File(folder.newFolder("bench"), FULL_MODEL_FILE).apply { writeBytes(byteArrayOf(1)) }
        assertEquals(file.absolutePath, debugPoseModelOverride(folder.root))
    }
}
