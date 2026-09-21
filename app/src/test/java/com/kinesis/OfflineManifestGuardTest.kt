package com.kinesis

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Permanent guard - FINAL_ARCHITECTURE section 6, CLAUDE.md section 16,
 * DECISIONS.md P3-13.
 *
 * *"No backend, no auth, no cloud, no sync."* *"MUST: remain offline-first."*
 *
 * ### Why this test exists at all
 *
 * Adding MediaPipe in P3-13 silently added `INTERNET` and `ACCESS_NETWORK_STATE`
 * to the merged manifest. Not from MediaPipe itself - from
 * `com.google.android.datatransport:transport-backend-cct`, a telemetry upload
 * backend three levels down its dependency tree. Nothing in Kinesis asked for
 * it, nothing in the source mentions it, and it would not have appeared in any
 * review of the diff.
 *
 * That is the shape of this failure: a hard architectural line crossed by a
 * transitive dependency of a transitive dependency, invisible everywhere except
 * the merged manifest. The permissions are excluded now, and this is what
 * notices if a version bump brings them back.
 *
 * ### Why it reads a build artefact
 *
 * The declaration that matters is the *merged* one - the app's own manifest was
 * never the problem. So this reads what the manifest merger produced. When that
 * artefact has not been built the test skips rather than passing, because a
 * guard that quietly passes when it cannot see anything is worse than no guard:
 * `assemble` is part of this project's verification command, so a full run
 * always has it.
 *
 * This test must not be weakened or deleted to make a build pass.
 */
class OfflineManifestGuardTest {

    private val forbidden = listOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
    )

    @Test
    fun the_merged_manifest_grants_no_network_capability() {
        var checkedAny = false

        for (manifest in mergedManifests()) {
            checkedAny = true
            val text = manifest.readText()
            for (permission in forbidden) {
                assertTrue(
                    "\n" + manifest.path + " declares " + permission + ".\n" +
                        "FINAL_ARCHITECTURE section 6 is \"No backend, no auth, no cloud, no " +
                        "sync\" and CLAUDE.md section 16 is offline-first.\n" +
                        "This almost certainly arrived transitively rather than from Kinesis's " +
                        "own manifest - P3-13 records the last time, when MediaPipe pulled in " +
                        "com.google.android.datatransport.\n" +
                        "Find the source with the manifest-merger blame report in " +
                        "app/build/outputs/logs/, then exclude it rather than stripping the " +
                        "permission, so the code goes too.",
                    !text.contains(permission),
                )
            }
        }

        assumeTrue(
            "no merged manifest has been built, so there was nothing to check - " +
                "run with `assemble` to exercise this guard",
            checkedAny,
        )
    }

    private fun mergedManifests(): List<File> {
        val root = File("build/intermediates").takeIf { it.isDirectory }
            ?: File("app/build/intermediates").takeIf { it.isDirectory }
            ?: return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .filter { it.path.replace('\\', '/').contains("/merged_manifest") }
            .toList()
    }
}
