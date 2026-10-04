package com.kinesis

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The launcher icon - DECISIONS.md P3-62. Until then the manifest named no icon, so the phone
 * showed Android's default (found by lint in P3-61).
 *
 * Read from the source files, as `OfflineManifestGuardTest` reads the manifest, so a missing or
 * renamed resource fails here rather than on a phone.
 */
class IconGuardTest {

    private fun res(path: String): File =
        File("src/main/res/$path").takeIf { it.isFile } ?: File("app/src/main/res/$path")

    private fun manifest(): String =
        (File("src/main/AndroidManifest.xml").takeIf { it.isFile } ?: File("app/src/main/AndroidManifest.xml")).readText()

    @Test
    fun the_manifest_names_the_launcher_icon() {
        assertTrue(
            "the <application> element must set android:icon=\"@mipmap/ic_launcher\"",
            manifest().contains("android:icon=\"@mipmap/ic_launcher\""),
        )
    }

    @Test
    fun the_adaptive_icon_has_a_background_a_foreground_and_a_monochrome_layer() {
        val adaptive = res("mipmap-anydpi-v26/ic_launcher.xml")
        assertTrue("missing " + adaptive, adaptive.isFile)
        val text = adaptive.readText()
        for (layer in listOf("<background", "<foreground", "<monochrome")) {
            assertTrue("the adaptive icon has no $layer layer", text.contains(layer))
        }
    }

    /** Android 7 (minSdk 24) cannot read an adaptive icon, so the plain mipmap must exist too. */
    @Test
    fun there_is_a_fallback_for_android_7() {
        assertTrue(res("mipmap/ic_launcher.xml").isFile)
    }

    @Test
    fun every_drawable_the_icons_name_exists() {
        val named = Regex("@drawable/([a-z0-9_]+)")
        for (path in listOf("mipmap-anydpi-v26/ic_launcher.xml", "mipmap/ic_launcher.xml")) {
            val file = res(path)
            assertTrue("missing $file", file.isFile)
            for (match in named.findAll(file.readText())) {
                val name = match.groupValues[1]
                assertTrue("$path names @drawable/$name, which does not exist", res("drawable/$name.xml").isFile)
            }
        }
    }
}
