// P7's MM-Fit evaluation harness - DECISIONS.md P3-27.
//
// JVM only: it runs the shared core's JVM target, which the parity suite holds
// to the same results as JavaScript and Android. It decides nothing about
// movement - it reads MediaPipe landmarks and MM-Fit's labels, feeds the
// phone's own analysis, and writes P5's export format.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:analysis"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// gradlew :mmfit-eval:runHarness -Plandmarks=<dir> -Plabels=<mm-fit root> -Pout=<dir>
//     [-Pworkouts=w00,w01,... | -Psplit=development]
// A split is read from subjects.json (DECISIONS.md P3-30): development, held-out, or P3-27's pair.
tasks.register<JavaExec>("runHarness") {
    group = "evaluation"
    description = "Runs every MM-Fit set through the analysis engine and writes P5 exports."
    val main = kotlin.jvm().compilations.getByName("main")
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.kinesis.tools.mmfit.HarnessKt")
    val split = project.findProperty("split")?.toString()
    args = listOf(
        project.findProperty("landmarks")?.toString() ?: "../../data/mm-fit-mediapipe",
        project.findProperty("labels")?.toString() ?: "../../data/mm-fit",
        project.findProperty("out")?.toString() ?: "../../data/mm-fit-eval",
        if (split != null) "split:$split" else project.findProperty("workouts")?.toString() ?: "",
    )
    dependsOn(main.compileTaskProvider)
}

// The personalisation study's data, development subjects only (P3-30):
// gradlew :mmfit-eval:personalisationStudy -Pharness=<harness out dir> -Pstudy=<out dir>
tasks.register<JavaExec>("personalisationStudy") {
    group = "evaluation"
    val main = kotlin.jvm().compilations.getByName("main")
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.kinesis.tools.mmfit.PersonalisationStudyKt")
    args = listOf(
        project.findProperty("harness")?.toString() ?: "../../data/mm-fit-eval-p3-30-dev",
        project.findProperty("study")?.toString() ?: "../../data/p3-30-study",
    )
    dependsOn(main.compileTaskProvider)
}

// A diagnostic: what near-side counting loses to how it combines the sides (P3-28).
tasks.register<JavaExec>("nearSideDiagnostic") {
    group = "evaluation"
    val main = kotlin.jvm().compilations.getByName("main")
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.kinesis.tools.mmfit.NearSideDiagnosticKt")
    args = listOf(
        project.findProperty("landmarks")?.toString() ?: "../../data/mm-fit-mediapipe",
        project.findProperty("labels")?.toString() ?: "../../data/mm-fit",
        project.findProperty("harness")?.toString() ?: "../../data/mm-fit-eval-p3-28-nearside",
    )
    dependsOn(main.compileTaskProvider)
}

// A diagnostic: one set's engine state, frame by frame.
// gradlew :mmfit-eval:traceSet -Pworkout=w00 -Pset=22 [-Pshift=0]
tasks.register<JavaExec>("traceSet") {
    group = "evaluation"
    val main = kotlin.jvm().compilations.getByName("main")
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.kinesis.tools.mmfit.TraceSetKt")
    args = listOf(
        project.findProperty("landmarks")?.toString() ?: "../../data/mm-fit-mediapipe",
        project.findProperty("labels")?.toString() ?: "../../data/mm-fit",
        project.findProperty("workout")?.toString() ?: "w00",
        project.findProperty("set")?.toString() ?: "1",
        project.findProperty("shift")?.toString() ?: "0",
    )
    dependsOn(main.compileTaskProvider)
}
