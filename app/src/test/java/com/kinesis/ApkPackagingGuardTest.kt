package com.kinesis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Permanent packaging and footprint guard - HANDOVER.md §8.3 item 5,
 * DECISIONS.md P3-58, PROJECT_STATUS.md §5.
 *
 * ### What this test asserts
 *
 * 1. [app/build.gradle.kts] configures explicit [ndk.abiFilters] targeting mobile
 *    ARM ABIs (`arm64-v8a`, `armeabi-v7a`), preventing accidental inclusion of
 *    x86 and x86_64 desktop emulator binaries. MediaPipe Tasks Vision does not
 *    provide emulator pose estimation, and x86/x86_64 binaries inflate the APK
 *    by ~34 MB of uncompressed native code.
 *
 * 2. When a packaged debug APK exists (produced by `assemble` or `assembleDebug`),
 *    this guard inspects its zip entries to prove that no x86/x86_64 native libraries
 *    are bundled into the APK artifact.
 */
class ApkPackagingGuardTest {

    @Test
    fun build_gradle_declares_arm_abi_filters_and_excludes_x86() {
        val buildGradle = File("app/build.gradle.kts")
            .takeIf { it.isFile }
            ?: File("build.gradle.kts")
        assertTrue("Could not locate app/build.gradle.kts", buildGradle.isFile)

        val text = buildGradle.readText()
        assertTrue("app/build.gradle.kts must declare ndk abiFilters", text.contains("abiFilters"))
        assertTrue("app/build.gradle.kts must include arm64-v8a", text.contains("\"arm64-v8a\""))
        assertFalse("app/build.gradle.kts must not include x86", text.contains("\"x86\""))
        assertFalse("app/build.gradle.kts must not include x86_64", text.contains("\"x86_64\""))
    }

    @Test
    fun built_apk_contains_only_allowed_native_abis() {
        val apk = File("app/build/outputs/apk/debug/app-debug.apk")
            .takeIf { it.isFile }
            ?: File("build/outputs/apk/debug/app-debug.apk")
            .takeIf { it.isFile }

        // Skip if APK has not been assembled yet in this build run
        if (apk == null || !apk.isFile) return

        ZipFile(apk).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            val hasArm64 = entries.any { it.startsWith("lib/arm64-v8a/") }
            val hasX86 = entries.any { it.startsWith("lib/x86/") }
            val hasX86_64 = entries.any { it.startsWith("lib/x86_64/") }

            assertTrue("APK must package arm64-v8a native libraries", hasArm64)
            assertFalse("APK must not package x86 native libraries", hasX86)
            assertFalse("APK must not package x86_64 native libraries", hasX86_64)
        }
    }
}
