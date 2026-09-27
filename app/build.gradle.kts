plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.kinesis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kinesis"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Debug-signed but not debuggable, and keeping the Phase B readout: frame
        // rates and timings measured on a debuggable build are not the product's.
        // Never shipped. DECISIONS.md P3-19.
        create("profiling") {
            initWith(getByName("debug"))
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    sourceSets {
        // The Phase B probe lives in src/debug; the profiling build keeps it,
        // and the full model the debug-only model switch offers (P3-21), so the
        // two can be timed live on a non-debuggable build too.
        getByName("profiling").java.srcDir("src/debug/java")
        getByName("profiling").assets.srcDir("src/debug/assets")
        // Room's exported schemas, which the migration test builds old
        // databases from. DECISIONS.md P3-22.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
    // connectedAndroidTest runs against debug unless asked otherwise, e.g.
    // -Pkinesis.testBuildType=profiling for the P3-19 benchmark.
    testBuildType = (project.findProperty("kinesis.testBuildType") as String?) ?: "debug"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// Every Room schema version is exported and committed, so each migration can be
// tested from the real old schema. DECISIONS.md P3-22.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:analysis"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Read-only on-device health context - DECISIONS.md P3-11.
    implementation(libs.androidx.health.connect)

    // Local-only session storage - DECISIONS.md P3-12.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Camera capture and on-device pose estimation - DECISIONS.md P3-13.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision) {
        // MediaPipe pulls Google's datatransport telemetry backend, which
        // declares INTERNET and ACCESS_NETWORK_STATE. FINAL_ARCHITECTURE
        // section 6 is "No backend, no auth, no cloud, no sync" and CLAUDE.md
        // section 16 is offline-first, so the capability is removed rather
        // than merely unused. Pose estimation itself is entirely on-device and
        // needs none of it. DECISIONS.md P3-13.
        exclude(group = "com.google.android.datatransport")
        exclude(group = "com.google.firebase")
    }

    // Account-based sync of session records - DECISIONS.md P3-14.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    // Test-only: builds a version 1 database from its exported schema and checks
    // the migration. Not in the APK. DECISIONS.md P3-22.
    androidTestImplementation(libs.androidx.room.testing)
}