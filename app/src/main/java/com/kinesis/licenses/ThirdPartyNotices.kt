package com.kinesis.licenses

/** One component the APK ships, and the terms it ships under. */
data class ThirdPartyNotice(
    val component: String,
    val licence: String,
    val detail: String = "",
    /** The full licence text, as an asset path, where the licence asks for it to be reproduced. */
    val textAsset: String? = null,
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
            "The model card states the model is not intended for human life-critical decisions. " +
            "Debug builds may also carry the Full model (pose_landmarker_full.task), from the same " +
            "card and under the same licence; release builds carry Lite only.",
    )

    /** The app's direct dependencies, with the licence each one's POM declares. */
    val software: List<ThirdPartyNotice> = listOf(
        ThirdPartyNotice("Kotlin standard library 2.1.21", "Apache License, Version 2.0"),
        ThirdPartyNotice("AndroidX Core, Lifecycle, Activity", "Apache License, Version 2.0"),
        ThirdPartyNotice("Jetpack Compose UI 1.7.6 and Material 3 1.3.1", "Apache License, Version 2.0"),
        ThirdPartyNotice(
            "AndroidX Health Connect client 1.1.0-beta01",
            "Apache License, Version 2.0",
            "It includes a repackaged copy of Google's Protocol Buffers, under the BSD 3-Clause " +
                "licence, whose text is shown below.",
        ),
        ThirdPartyNotice("AndroidX Room 2.7.1", "Apache License, Version 2.0"),
        ThirdPartyNotice(
            "AndroidX CameraX 1.4.1",
            "Apache License, Version 2.0",
            "camera-core also declares a BSD licence, for the libyuv image library it includes. " +
                "That licence's text is shown below.",
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

    /**
     * The components under a BSD licence, each with its full text - DECISIONS.md
     * P3-21. A BSD licence asks for the copyright notice, the conditions and the
     * disclaimer to be reproduced with a binary distribution, so the text ships
     * as an asset, verbatim.
     *
     * Where each text came from, since neither artifact carries a usable copy:
     * CameraX's AAR carries no libyuv text at all, and Health Connect's jar
     * carries the Protocol Buffers terms without their copyright line.
     *
     * - libyuv: `LICENSE` from libyuv's own repository,
     *   chromium.googlesource.com/libyuv/libyuv, branch `main`. camera-core's POM
     *   names that repository's `README.chromium`, which names `LICENSE` as the
     *   licence file.
     * - Protocol Buffers: `LICENSE` from github.com/protocolbuffers/protobuf at
     *   tag `v26.1`, the release of `protobuf-javalite` 4.26.1, the version the
     *   release APK resolves.
     */
    val bsdNotices: List<ThirdPartyNotice> = listOf(
        ThirdPartyNotice(
            component = "libyuv (in AndroidX CameraX camera-core 1.4.1)",
            licence = "BSD 3-Clause",
            detail = "Copyright 2011 The LibYuv Project Authors.",
            textAsset = "licenses/BSD-3-Clause-libyuv.txt",
        ),
        ThirdPartyNotice(
            component = "Protocol Buffers (protobuf-javalite 4.26.1, through MediaPipe and Firebase " +
                "Firestore; and the copy repackaged in AndroidX Health Connect)",
            licence = "BSD 3-Clause",
            detail = "Copyright 2008 Google Inc.",
            textAsset = "licenses/BSD-3-Clause-protobuf.txt",
        ),
    )
}
