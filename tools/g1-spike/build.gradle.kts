plugins {
    kotlin("multiplatform") version "2.1.21"
}

repositories { mavenCentral() }

kotlin {
    jvm {
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }
    js(IR) {
        moduleName = "kinesis"
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    sourceSets {
        val commonMain by getting
        val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "failed")
    }
}
