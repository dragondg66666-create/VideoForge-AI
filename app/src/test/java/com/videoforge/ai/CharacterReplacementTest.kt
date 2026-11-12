package com.videoforge.ai

import com.videoforge.ai.media.CharacterReplacementEngine
import com.videoforge.ai.model.CompositingTransform
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.model.PointF2D
import com.videoforge.ai.model.PoseLandmarkItem
import com.videoforge.ai.model.ReplacementAsset
import com.videoforge.ai.model.TrackFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CharacterReplacementTest {

    @Test
    fun testTransformTemporalInterpolation() {
        val f1 = TrackFrame(
            timestampMs = 1000L,
            boxLeft = 100f, boxTop = 50f, boxRight = 200f, boxBottom = 250f,
            headEulerZ = 2f,
            status = "DETECTED"
        )
        val f2 = TrackFrame(
            timestampMs = 2000L,
            boxLeft = 160f, boxTop = 70f, boxRight = 280f, boxBottom = 290f,
            headEulerZ = 6f,
            status = "DETECTED"
        )

        val track = PersonTrack(
            trackId = 1,
            frames = listOf(f1, f2)
        )

        // Interpolate at t = 1500ms (midpoint, alpha = 0.5)
        val transform = CharacterReplacementEngine.computeTransformForTimestamp(
            track = track,
            timestampMs = 1500L,
            frameWidth = 640,
            frameHeight = 360,
            userScaleMultiplier = 1.0f,
            userOffsetX = 0f,
            userOffsetY = 0f,
            userRotationTrimDeg = 0f
        )

        // Expected box:
        // left: 100 + 0.5 * 60 = 130
        // top: 50 + 0.5 * 20 = 60
        // right: 200 + 0.5 * 80 = 240
        // bottom: 250 + 0.5 * 40 = 270
        // center X = (130 + 240) / 2 = 185
        // bottom anchor Y = 270
        // width = 240 - 130 = 110
        // height = 270 - 60 = 210
        // rotation = 2 + 0.5 * 4 = 4 deg

        assertEquals(185.0f, transform.anchorX, 0.5f)
        assertEquals(270.0f, transform.anchorY, 0.5f)
        assertEquals(110.0f, transform.targetWidth, 0.5f)
        assertEquals(210.0f, transform.targetHeight, 0.5f)
        assertEquals(4.0f, transform.rotationDeg, 0.1f)
        assertEquals("ACTIVE", transform.trackStatus)
        assertFalse(transform.isOccluded)
    }

    @Test
    fun testUserScaleAndOffsetCalibration() {
        val f = TrackFrame(
            timestampMs = 0L,
            boxLeft = 200f, boxTop = 100f, boxRight = 300f, boxBottom = 300f,
            headEulerZ = 0f
        )
        val track = PersonTrack(trackId = 1, frames = listOf(f))

        val transform = CharacterReplacementEngine.computeTransformForTimestamp(
            track = track,
            timestampMs = 0L,
            frameWidth = 640,
            frameHeight = 360,
            userScaleMultiplier = 1.25f,
            userOffsetX = 15f,
            userOffsetY = -10f,
            userRotationTrimDeg = 5f,
            userOpacity = 0.85f
        )

        // Base center X = 250 + 15 = 265
        // Base bottom Y = 300 - 10 = 290
        assertEquals(265.0f, transform.anchorX, 0.1f)
        assertEquals(290.0f, transform.anchorY, 0.1f)
        assertEquals(1.25f, transform.userScaleMultiplier, 0.01f)
        assertEquals(5.0f, transform.rotationDeg, 0.1f)
        assertEquals(0.85f, transform.userOpacity, 0.01f)
    }

    @Test
    fun testRotationClampingSafety() {
        val f = TrackFrame(
            timestampMs = 0L,
            boxLeft = 100f, boxTop = 50f, boxRight = 200f, boxBottom = 250f,
            headEulerZ = 45f // Excessive head roll
        )
        val track = PersonTrack(trackId = 1, frames = listOf(f))

        val transform = CharacterReplacementEngine.computeTransformForTimestamp(
            track = track,
            timestampMs = 0L,
            frameWidth = 640,
            frameHeight = 360,
            userRotationTrimDeg = 10f
        )

        // 45 + 10 = 55 deg, must be clamped to max 35 deg to prevent unnatural flipping
        assertEquals(35.0f, transform.rotationDeg, 0.1f)
    }

    @Test
    fun testMissingTrackAndOcclusionHandling() {
        val f1 = TrackFrame(
            timestampMs = 1000L,
            boxLeft = 100f, boxTop = 50f, boxRight = 200f, boxBottom = 250f,
            isInterpolated = true,
            status = "INTERPOLATED_OCCLUDED"
        )
        val track = PersonTrack(trackId = 2, frames = listOf(f1))

        // When frame is interpolated / occluded
        val transform = CharacterReplacementEngine.computeTransformForTimestamp(
            track = track,
            timestampMs = 1000L,
            frameWidth = 640,
            frameHeight = 360
        )

        assertTrue("Should report occluded", transform.isOccluded)

        // When timestamp is long past last tracked frame (>1200ms)
        val lostTransform = CharacterReplacementEngine.computeTransformForTimestamp(
            track = track,
            timestampMs = 3000L,
            frameWidth = 640,
            frameHeight = 360
        )

        assertEquals("LOST", lostTransform.trackStatus)
    }

    @Test
    fun testReplacementAssetValidationFields() {
        val asset = ReplacementAsset(
            assetId = "test_asset_01",
            filename = "hero_cutout.png",
            uriString = "content://media/external/images/123",
            width = 512,
            height = 1024,
            hasAlphaChannel = true,
            transparentPixelRatio = 46.5f,
            isFullyOpaqueWarning = false,
            validationMessage = "Valid transparent cutout"
        )

        assertTrue(asset.hasAlphaChannel)
        assertFalse(asset.isFullyOpaqueWarning)
        assertTrue(asset.transparentPixelRatio > 20f)
        assertEquals(512, asset.width)
        assertEquals(1024, asset.height)
    }

    @Test
    fun testHeightMatchingScaleAndFeetAnchor() {
        // Test performer approaching camera: height increases from 180px to 300px
        val fNear = TrackFrame(
            timestampMs = 0L,
            boxLeft = 200f, boxTop = 60f, boxRight = 320f, boxBottom = 360f, // height = 300px
            headEulerZ = 0f
        )
        val track = PersonTrack(trackId = 1, frames = listOf(fNear))

        val transform = CharacterReplacementEngine.computeTransformForTimestamp(
            track = track,
            timestampMs = 0L,
            frameWidth = 640,
            frameHeight = 360,
            userScaleMultiplier = 1.0f
        )

        // Target height must be exactly 300px
        assertEquals(300f, transform.targetHeight, 0.1f)
        // Feet anchor Y must be at boxBottom = 360px
        assertEquals(360f, transform.anchorY, 0.1f)
        // Center anchor X must be (200 + 320)/2 = 260px
        assertEquals(260f, transform.anchorX, 0.1f)

        // For a character asset of native height 600px, base scale would be 300 / 600 = 0.5f
        val assetNativeHeight = 600f
        val calculatedScale = (transform.targetHeight / assetNativeHeight) * transform.userScaleMultiplier
        assertEquals(0.5f, calculatedScale, 0.01f)
    }

    @Test
    fun testMultiFrameTemporalTrajectoryProgression() {
        // Multi-point trajectory across 3 timestamps
        val t0 = TrackFrame(timestampMs = 0L, boxLeft = 100f, boxTop = 50f, boxRight = 200f, boxBottom = 250f)
        val t1 = TrackFrame(timestampMs = 1000L, boxLeft = 150f, boxTop = 60f, boxRight = 250f, boxBottom = 260f)
        val t2 = TrackFrame(timestampMs = 2000L, boxLeft = 200f, boxTop = 70f, boxRight = 300f, boxBottom = 270f)

        val track = PersonTrack(trackId = 1, frames = listOf(t0, t1, t2))

        // Query at t = 500ms (halfway between t0 and t1)
        val trMid1 = CharacterReplacementEngine.computeTransformForTimestamp(
            track = track,
            timestampMs = 500L,
            frameWidth = 640,
            frameHeight = 360
        )
        // Query at t = 1500ms (halfway between t1 and t2)
        val trMid2 = CharacterReplacementEngine.computeTransformForTimestamp(
            track = track,
            timestampMs = 1500L,
            frameWidth = 640,
            frameHeight = 360
        )

        // Center X at t=0 is 150, at t=1000 is 200 -> at t=500 must be 175
        assertEquals(175f, trMid1.anchorX, 0.5f)
        // Center X at t=1000 is 200, at t=2000 is 250 -> at t=1500 must be 225
        assertEquals(225f, trMid2.anchorX, 0.5f)

        // Monotonic motion progress verification
        assertTrue(trMid2.anchorX > trMid1.anchorX)
        assertTrue(trMid1.anchorX > 150f)
    }
}
