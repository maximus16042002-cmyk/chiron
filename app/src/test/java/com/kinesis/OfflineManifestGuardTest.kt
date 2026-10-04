package com.kinesis

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Permanent guard - FINAL_ARCHITECTURE section 6 (as amended), CLAUDE.md
 * section 16 (as amended), DECISIONS.md P3-13 and P3-14.
 *
 * ### What this test used to assert, and why that changed
 *
 * P3-13 added it with one job: **no network permission at all**. It was written
 * because adding MediaPipe silently added `INTERNET` and `ACCESS_NETWORK_STATE`
 * to the merged manifest - not from MediaPipe, but from
 * `com.google.android.datatransport`, a telemetry upload backend three levels
 * down its dependency tree. Nothing in Kinesis asked for it and it appeared in
 * no diff a reviewer would read.
 *
 * P3-14 added Firebase Auth and Firestore by deliberate decision, which
 * legitimately needs network capability. That makes the original assertion
 * impossible to satisfy - but **the protection it provided is the entire point
 * and must survive**, so the job changes rather than the test disappearing:
 *
 * > Network capability may come **only** from the artifacts P3-14 names.
 * > Anything else that introduces it fails the build.
 *
 * That is strictly the more useful assertion. "No network at all" was easy to
 * satisfy and easy to lose in one commit; this one stays meaningful for the rest
 * of the project's life, and it is what notices when the next MediaPipe-shaped
 * surprise arrives.
 *
 * ### Why it reads the blame report rather than the manifest
 *
 * The merged manifest says *that* a permission exists. It does not say **who
 * asked for it**, and who asked is the whole question now that some askers are
 * legitimate. The manifest merger writes attribution to
 * `app/build/outputs/logs/manifest-merger-*-report.txt`, so that is what this
 * parses.
 *
 * When the report has not been built the test skips rather than passing: a guard
 * that quietly passes when it cannot see anything is worse than no guard.
 * `assemble` is part of this project's verification command, so a full run
 * always produces one.
 *
 * This test must not be weakened or deleted to make a build pass. Adding an
 * entry to [DECLARED_NETWORK_ARTIFACTS] *is* weakening it, and needs its own
 * DECISIONS.md entry saying why.
 */
class OfflineManifestGuardTest {

    @Test
    fun network_capability_comes_only_from_the_artifacts_the_decision_record_names() {
        var checkedAny = false
        val offences = mutableListOf<String>()

        for (report in blameReports()) {
            checkedAny = true
            val text = report.readText()
            for (permission in NETWORK_PERMISSIONS) {
                for (source in sourcesOf(text, permission)) {
                    val coordinate = source.substringBeforeLast(':')
                    if (coordinate !in DECLARED_NETWORK_ARTIFACTS) {
                        offences += permission + "  <-  " + source +
                            "   (" + report.name + ")"
                    }
                }
            }
        }

        assumeTrue(
            "no manifest-merger report has been built, so there was nothing to check - " +
                "run with `assemble` to exercise this guard",
            checkedAny,
        )

        assertTrue(
            "\nNetwork capability arrived from an artifact no decision entry declares:\n\n" +
                offences.joinToString("\n") { "    " + it } + "\n\n" +
                "CLAUDE.md section 16, as amended by DECISIONS.md P3-14, permits network\n" +
                "access only for Firebase Auth and Firestore sync of session records.\n" +
                "Everything else - analytics, crash reporting, telemetry - is still\n" +
                "forbidden, and P3-13 records the last time one arrived uninvited.\n\n" +
                "Exclude it at the dependency that pulls it in, the way P3-13 excluded\n" +
                "com.google.android.datatransport, so the code leaves the APK rather than\n" +
                "merely losing its manifest entry. If it genuinely belongs, add it to\n" +
                "DECLARED_NETWORK_ARTIFACTS and write the decision entry that justifies it.",
            offences.isEmpty(),
        )
    }

    /**
     * Kinesis's own manifest still declares no network permission.
     *
     * Every byte of network capability in this app is attributable to a named
     * third-party artifact. If Kinesis itself ever declares one, something is
     * being done directly rather than through the sync path P3-14 sanctioned,
     * and that deserves to be noticed.
     */
    @Test
    fun kinesis_own_manifest_declares_no_network_permission_of_its_own() {
        val manifest = File("src/main/AndroidManifest.xml")
            .takeIf { it.isFile }
            ?: File("app/src/main/AndroidManifest.xml")
        assumeTrue("could not locate the app manifest", manifest.isFile)

        val text = manifest.readText()
        for (permission in NETWORK_PERMISSIONS) {
            assertTrue(
                manifest.path + " declares " + permission + " directly. Network " +
                    "capability should arrive only from the declared sync artifacts, so " +
                    "that it stays traceable.",
                !text.contains(permission),
            )
        }
    }

    /**
     * DECISIONS.md P3-65: Android's cloud backup would copy every account's database to the
     * user's Google Drive - a path off the phone that CLAUDE.md section 16 does not permit and the
     * app never disclosed. Session history reaches the account through sync instead.
     */
    @Test
    fun cloud_backup_of_the_apps_data_is_off() {
        val manifest = File("src/main/AndroidManifest.xml")
            .takeIf { it.isFile }
            ?: File("app/src/main/AndroidManifest.xml")
        assumeTrue("could not locate the app manifest", manifest.isFile)

        assertTrue(
            manifest.path + " must set android:allowBackup=\"false\"",
            manifest.readText().contains("android:allowBackup=\"false\""),
        )
    }

    // --- Parsing ---------------------------------------------------------------------

    /**
     * The artifacts that declared a given permission, as `group:name:version`.
     *
     * The report's shape, confirmed by reading one rather than assumed: a
     * heading is a column-0 line containing `#`; the lines beneath it until the
     * next column-0 heading are that element's sources, written as
     * `ADDED from [artifact] path` or `MERGED from [artifact] path`.
     * Tab-indented lines belong to attributes of the element and are skipped -
     * they name the same artifacts and would only add noise.
     *
     * An earlier version of this matched on the whole file with a regex and
     * over-captured, attributing `ACCESS_NETWORK_STATE` to Room among others.
     * It is line-based now because the report is line-structured.
     */
    private fun sourcesOf(report: String, permission: String): Set<String> {
        val lines = report.lines()
        val start = lines.indexOfFirst { it == "uses-permission#" + permission }
        if (start < 0) return emptySet()

        val sources = LinkedHashSet<String>()
        for (index in start + 1 until lines.size) {
            val line = lines[index]
            if (line.isEmpty()) continue
            if (line[0].isWhitespace()) continue          // an attribute's own block
            val match = SOURCE.find(line)
            if (match != null) {
                sources += match.groupValues[1]
                continue
            }
            break                                          // the next element heading
        }
        return sources
    }

    private fun blameReports(): List<File> {
        val logs = File("build/outputs/logs").takeIf { it.isDirectory }
            ?: File("app/build/outputs/logs").takeIf { it.isDirectory }
            ?: return emptyList()
        return logs.listFiles { f -> f.isFile && f.name.startsWith("manifest-merger") }
            .orEmpty()
            .toList()
    }

    private companion object {

        val NETWORK_PERMISSIONS = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
        )

        val SOURCE = Regex("""(?:ADDED|MERGED) from \[([^\]]+)\]""")

        /**
         * The only artifacts permitted to bring network capability, as
         * `group:name` - version-agnostic, so a routine bump does not fail the
         * build but a *new* artifact does.
         *
         * Every entry is here because DECISIONS.md P3-14 names it:
         *
         * - **firebase-auth**, **firebase-firestore** - the two products the
         *   amendment sanctions.
         * - **recaptcha** - an unavoidable transitive of `firebase-auth`, which
         *   uses it for bot protection on authentication endpoints. Listed
         *   explicitly rather than silently tolerated: it is the one passenger
         *   in this batch that was not asked for, and naming it is what keeps it
         *   visible.
         * - **grpc-android** - Firestore's transport.
         * - **work-runtime** - WorkManager, which needs `ACCESS_NETWORK_STATE`
         *   to honour a network constraint on the retry queue. It carries no
         *   `INTERNET` of its own.
         *
         * Adding to this list is an architecture change, not a fix.
         */
        val DECLARED_NETWORK_ARTIFACTS = setOf(
            "com.google.firebase:firebase-auth",
            "com.google.firebase:firebase-firestore",
            "com.google.android.recaptcha:recaptcha",
            "io.grpc:grpc-android",
            "androidx.work:work-runtime",
        )
    }
}
