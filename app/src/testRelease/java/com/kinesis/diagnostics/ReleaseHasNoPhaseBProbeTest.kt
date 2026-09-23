package com.kinesis.diagnostics

import com.kinesis.core.domain.landmark.LandmarkId
import com.kinesis.core.domain.landmark.RequiredLandmarks
import com.kinesis.pose.CameraFacing
import org.junit.Assert.assertNull
import org.junit.Test

/** Release builds carry no Phase B probe - DECISIONS.md P3-17. Runs in testReleaseUnitTest. */
class ReleaseHasNoPhaseBProbeTest {

    @Test
    fun a_release_build_gets_no_probe() {
        assertNull(phaseBProbeOrNull(RequiredLandmarks.of(LandmarkId.LEFT_KNEE), CameraFacing.FRONT) { })
    }
}
