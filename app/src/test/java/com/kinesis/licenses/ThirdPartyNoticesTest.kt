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

    /**
     * Debug builds may also carry the full model for the debug-only switch
     * (DECISIONS.md P3-21), so that folder is read too: a model in any APK this
     * project builds is named on its licences screen.
     */
    @Test
    fun every_pose_model_shipped_in_assets_is_named_in_the_model_notice() {
        val shipped = listOf(assets, File("src/debug/assets"))
            .flatMap { folder -> PoseModelAsset.CANDIDATES.filter { File(folder, it).isFile } }
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

    /**
     * A BSD licence asks for its copyright notice, its conditions and its
     * disclaimer to be reproduced in a binary distribution. P3-17 and P3-19 named
     * the two BSD components without their texts; DECISIONS.md P3-21 ships them.
     * Each text is checked for all of its parts, because a notice with the
     * copyright line missing - which is how Health Connect's own jar carries it -
     * does not meet the licence's first condition.
     */
    @Test
    fun every_bsd_component_ships_its_complete_licence_text() {
        val bsd = ThirdPartyNotices.bsdNotices
        assertTrue("the licences screen names no BSD component", bsd.size >= 2)
        for (notice in bsd) {
            val asset = notice.textAsset
            assertTrue(notice.component + " has no licence text", asset != null)
            val raw = File(assets, asset!!).readText()
            assertTrue(notice.component + ": no copyright line", raw.lineSequence().first().startsWith("Copyright "))
            // The two texts wrap their lines at different places, so the clauses
            // are compared with whitespace runs collapsed.
            val text = raw.replace(Regex("\\s+"), " ")
            assertTrue(notice.component + ": no grant", text.contains("Redistribution and use in source and binary forms"))
            assertTrue(notice.component + ": no source condition", text.contains("Redistributions of source code must retain"))
            assertTrue(notice.component + ": no binary condition", text.contains("Redistributions in binary form must reproduce"))
            assertTrue(notice.component + ": no endorsement clause", text.contains("may be used to endorse or promote"))
            assertTrue(notice.component + ": no disclaimer", text.contains("EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE."))
        }
        val components = bsd.joinToString { it.component }
        assertTrue("libyuv is not among the BSD notices: $components", components.contains("libyuv"))
        assertTrue("Protocol Buffers is not among the BSD notices: $components", components.contains("Protocol Buffers"))
    }

    /** The two notices that mention a BSD component must point at the text that now ships. */
    @Test
    fun the_notices_that_mention_bsd_code_name_the_text_on_this_screen() {
        for (notice in ThirdPartyNotices.software) {
            if (!notice.detail.contains("BSD")) continue
            assertTrue(
                notice.component + " mentions BSD code but not that its text is below",
                notice.detail.contains("text is shown below"),
            )
        }
    }

    @Test
    fun the_model_notice_carries_the_licence_and_the_cards_own_limits() {
        val model = ThirdPartyNotices.poseModel
        assertTrue(model.licence.contains("Apache License, Version 2.0"))
        assertTrue(model.detail.contains("2021-04-16"))
        assertTrue(model.detail.contains("life-critical"))
    }
}
