package com.kinesis.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The export's FileProvider reaches the export and nothing else - DECISIONS.md
 * P3-25, CLAUDE.md section 16.
 *
 * It is the one door through which another app can read a Kinesis file. Two
 * settings keep it narrow, and each is a one-word edit away from wide open:
 * `exported="false"`, so only a grant from the user's own share opens it; and a
 * paths file naming the cache's `exports/` folder alone, so that grant cannot
 * reach the database or anything else the app keeps.
 */
class ExportProviderGuardTest {

    private fun file(relative: String): File =
        File("src/main/$relative").takeIf { it.isFile } ?: File("app/src/main/$relative")

    @Test
    fun the_provider_is_not_exported_and_grants_per_share_only() {
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file("AndroidManifest.xml"))
        val providers = manifest.getElementsByTagName("provider")
        assertEquals("the export's provider should be the only one Kinesis declares", 1, providers.length)
        val provider = providers.item(0) as org.w3c.dom.Element
        fun attribute(name: String) = provider.getAttributeNS("http://schemas.android.com/apk/res/android", name)
        assertEquals("androidx.core.content.FileProvider", attribute("name"))
        assertEquals("false", attribute("exported"))
        assertEquals("true", attribute("grantUriPermissions"))
        assertEquals("\${applicationId}.exports", attribute("authorities"))
    }

    @Test
    fun the_provider_can_reach_the_export_folder_and_nothing_else() {
        val paths = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(file("res/xml/export_paths.xml")).documentElement
        val entries = (0 until paths.childNodes.length).map { paths.childNodes.item(it) }
            .filterIsInstance<org.w3c.dom.Element>()
        assertEquals("one path entry, for the export folder", 1, entries.size)
        val entry = entries.single()
        assertEquals("cache-path", entry.tagName)
        assertEquals("exports/", entry.getAttribute("path"))
        assertTrue("a path of '.' or '/' would share the whole cache", entry.getAttribute("path").trim('/').isNotEmpty())
    }
}
