package com.kinesis.licenses

/** One component the APK ships, and the terms it ships under. */
data class ThirdPartyNotice(
    val component: String,
    val licence: String,
    val detail: String = "",
)

/**
 * What the licences screen shows - DECISIONS.md P3-17.
 *
 * Every licence name below was read from the artifact's own POM, or for the
 * model from its model card, not assumed. The Apache 2.0 text ships verbatim
 * as an asset, because section 4(a) asks that recipients get a copy of it.
 */
object ThirdPartyNotices {

    /** The canonical Apache License 2.0 text, shipped unmodified. */
    const val APACHE_LICENSE_ASSET = "licenses/APACHE-2.0.txt"

    val poseModel = ThirdPartyNotice(
        component = "MediaPipe BlazePose GHUM 3D pose landmark model, Lite (pose_landmarker_lite.task)",
        licence = "Apache License, Version 2.0",
        detail = "By Valentin Bazarevsky, Ivan Grishchenko and Eduard Gabriel Bazavan, Google. " +
            "Model card dated 2021-04-16. Distributed unmodified. " +
            "The model card states the model is not intended for human life-critical decisions.",
    )

    /** The app's direct dependencies, with the licence each one's POM declares. */
    val software: List<ThirdPartyNotice> = listOf(
        ThirdPartyNotice("Kotlin standard library 2.1.21", "Apache License, Version 2.0"),
        ThirdPartyNotice("AndroidX Core, Lifecycle, Activity", "Apache License, Version 2.0"),
        ThirdPartyNotice("Jetpack Compose UI 1.7.6 and Material 3 1.3.1", "Apache License, Version 2.0"),
        ThirdPartyNotice("AndroidX Health Connect client 1.0.0-alpha11", "Apache License, Version 2.0"),
        ThirdPartyNotice("AndroidX Room 2.7.1", "Apache License, Version 2.0"),
        ThirdPartyNotice(
            "AndroidX CameraX 1.4.1",
            "Apache License, Version 2.0",
            "camera-core also declares a BSD licence, for the libyuv image library it includes.",
        ),
        ThirdPartyNotice("MediaPipe Tasks Vision 0.10.21", "Apache License, Version 2.0"),
        ThirdPartyNotice("Firebase Firestore 25.1.1", "Apache License, Version 2.0"),
        ThirdPartyNotice(
            "Firebase Authentication 23.1.0",
            "Android Software Development Kit License",
            "Google's proprietary Android SDK licence, not an open-source one.",
        ),
        ThirdPartyNotice("AndroidX WorkManager 2.10.0", "Apache License, Version 2.0"),
    )
}
