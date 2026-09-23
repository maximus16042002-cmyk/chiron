package com.kinesis.licenses

import com.kinesis.camera.PoseModelAsset
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The in-app licences screen must describe what the APK actually ships -
 * DECISIONS.md P3-17. A notice for a model that is not the one in `assets/`, or
 * a truncated licence text, is a notice in name only.
 */
class ThirdPartyNoticesTest {

    /** Unit tests run with the module directory as the working directory. */
    private val assets = File("src/main/assets")

    @Test
    fun every_pose_model_shipped_in_assets_is_named_in_the_model_notice() {
        val shipped = PoseModelAsset.CANDIDATES.filter { File(assets, it).isFile }
        assertTrue("no pose model in " + assets.absolutePath + ", so this proves nothing", shipped.isNotEmpty())
        for (model in shipped) {
            assertTrue(
                model + " ships in the APK but the licences screen does not name it",
                ThirdPartyNotices.poseModel.component.contains(model) ||
                    ThirdPartyNotices.poseModel.detail.contains(model),
            )
        }
    }

    /** Apache 2.0 section 4(a): recipients get a copy of the licence - the whole of it. */
    @Test
    fun the_apache_licence_shipped_is_the_complete_text() {
        val text = File(assets, ThirdPartyNotices.APACHE_LICENSE_ASSET).readText()
        assertTrue(text.contains("Apache License"))
        assertTrue(text.contains("Version 2.0, January 2004"))
        for (section in 1..9) {
            assertTrue("section $section is missing", text.contains("\n   $section. "))
        }
        assertTrue(text.contains("END OF TERMS AND CONDITIONS"))
    }

    @Test
    fun the_model_notice_carries_the_licence_and_the_cards_own_limits() {
        val model = ThirdPartyNotices.poseModel
        assertTrue(model.licence.contains("Apache License, Version 2.0"))
        assertTrue(model.detail.contains("2021-04-16"))
        assertTrue(model.detail.contains("life-critical"))
    }
}
