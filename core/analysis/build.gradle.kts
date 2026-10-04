import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    js(IR) {
        moduleName = "kinesis-analysis"
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: this module's public signatures expose
            // domain types (Landmarks, ExerciseProfile, Finding), so consumers
            // must be able to see them.
            api(project(":core:domain"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.kinesis.core.analysis"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
// The per-frame allocation guards measure what the JIT allocates, and what the
// JIT eliminates depends on everything else that ran in the same JVM. Run after
// the P3-19 parity suite, the growth guard sometimes caught a one-off
// recompilation - flat before and after it, 1,952,000 bytes a window across 2.6
// million frames - between its two windows, and failed. So the guards run in a
// JVM of their own, where what they measure is the code under test. Nothing in
// them is loosened. `jvmTest` is finalized by this task, so `allTests` always
// runs it. DECISIONS.md P3-19.
val allocationGuards = "com.kinesis.core.analysis.PerFrameAllocationTest"
val jvmTestTask = tasks.named<Test>("jvmTest")
val jvmAllocationTest = tasks.register<Test>("jvmAllocationTest") {
    description = "Runs the per-frame allocation guards in a JVM of their own."
    group = "verification"
    testClassesDirs = jvmTestTask.get().testClassesDirs
    classpath = jvmTestTask.get().classpath
    filter { includeTestsMatching(allocationGuards) }
}
jvmTestTask.configure {
    filter { excludeTestsMatching(allocationGuards) }
    finalizedBy(jvmAllocationTest)
}
