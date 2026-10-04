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
    // -Pstride=N feeds every N-th frame, to imitate a lower result rate (P3-32). 1 by default.
    systemProperty("kinesis.stride", project.findProperty("stride")?.toString() ?: "1")
    // -Pgate=<degrees> and -Pbridge=<ms> override the outlier gate and the bridged gap, for
    // diagnostics (P3-33). Unset, the phone's own settings.
    project.findProperty("gate")?.let { systemProperty("kinesis.gate", it.toString()) }
    project.findProperty("bridge")?.let { systemProperty("kinesis.bridge", it.toString()) }
    // -PsitupTrunk=<top>,<bottom>: the sit-up driven by the trunk's inclination (P3-45), for evaluation.
    project.findProperty("situpTrunk")?.let { systemProperty("kinesis.situpTrunk", it.toString()) }
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
    // -PheldOut=true: P3-42's one held-out test of the frozen Layer 1 setting, and nothing else.
    args = listOfNotNull(
        project.findProperty("harness")?.toString() ?: "../../data/mm-fit-eval-p3-30-dev",
        project.findProperty("study")?.toString() ?: "../../data/p3-30-study",
        if (project.findProperty("heldOut")?.toString() == "true") "held-out" else null,
    )
    dependsOn(main.compileTaskProvider)
}

// The anomaly study's data, development subjects only (P3-34, P3-36, P3-37):
// gradlew :mmfit-eval:anomalyStudy -Pharness=<harness out dir> -Pstudy=<out dir>
tasks.register<JavaExec>("anomalyStudy") {
    group = "evaluation"
    val main = kotlin.jvm().compilations.getByName("main")
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.kinesis.tools.mmfit.AnomalyStudyKt")
    args = listOf(
        project.findProperty("harness")?.toString() ?: "../../data/mm-fit-eval-p3-33-ta-s1-base",
        project.findProperty("study")?.toString() ?: "../../data/p3-37-anomaly-study",
    )
    dependsOn(main.compileTaskProvider)
}

// How often Layer 2's step A rules speak on the development subjects (P3-43), descriptive only:
// gradlew :mmfit-eval:techniqueStudy -Pharness=<harness out dir> -Pstudy=<out dir>
tasks.register<JavaExec>("techniqueStudy") {
    group = "evaluation"
    val main = kotlin.jvm().compilations.getByName("main")
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.kinesis.tools.mmfit.TechniqueStudyKt")
    args = listOf(
        project.findProperty("harness")?.toString() ?: "../../data/mm-fit-eval-p3-33-ta-s1-base",
        project.findProperty("study")?.toString() ?: "../../data/p3-43-technique",
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

// P9 Part B: MovementContext feature extraction for the Exercise Verifier
// gradlew :mmfit-eval:extractVerifierDataset [-Plandmarks=...] [-Plabels=...] [-Pout=...]
tasks.register<JavaExec>("extractVerifierDataset") {
    group = "evaluation"
    description = "Extracts 145-feature MovementContext dataset for MM-Fit development workouts."
    val main = kotlin.jvm().compilations.getByName("main")
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.kinesis.tools.mmfit.ExtractVerifierDatasetKt")
    args = listOf(
        project.findProperty("landmarks")?.toString() ?: "../../data/mm-fit-mediapipe",
        project.findProperty("labels")?.toString() ?: "../../data/mm-fit",
        project.findProperty("out")?.toString() ?: "../../data/verifier",
    )
    dependsOn(main.compileTaskProvider)
}

