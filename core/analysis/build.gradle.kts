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
    compileSdk = 35

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

// Android Lint is disabled on this module.
//
// It is a Kotlin Multiplatform library with no Android sources, resources or
// manifest logic, so lint has nothing here to find. It is disabled because
// AGP 8.7.3's bundled lint cannot read Java 25 class files and aborts with
// "A failure occurred while executing AndroidLintWorkAction > 25.0.3" when the
// Gradle daemon runs on Android Studio's JBR 25 (pinned in
// gradle/gradle-daemon-jvm.properties). `lint { abortOnError = false }` does
// not help: this is a worker exception, not a lint finding.
//
// :app keeps its lint. Remove this block when AGP is bumped past the JDK 25
// incompatibility. See DECISIONS.md P1-1.
tasks.matching { it.name.startsWith("lint") }.configureEach {
    enabled = false
}
