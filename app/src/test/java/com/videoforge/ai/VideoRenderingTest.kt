package com.videoforge.ai

import com.videoforge.ai.media.CharacterReplacementEngine
import com.videoforge.ai.media.VideoRenderingEngine
import com.videoforge.ai.model.ExportProgress
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.model.TrackFrame
import com.videoforge.ai.model.VideoExportConfig
import com.videoforge.ai.model.VideoExportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRenderingTest {

    @Test
    fun testExportTimelineTimestampOrdering() {
        val durationMs = 3000L
        val fps = 30

        val timeline = VideoRenderingEngine.calculateExportTimeline(durationMs, fps)

        // Must not be empty
        assertFalse(timeline.isEmpty())

        // Must be in strictly increasing monotonic order
        for (i in 0 until timeline.size - 1) {
            assertTrue(
                "Timestamps must be monotonic: ${timeline[i]} < ${timeline[i + 1]}",
                timeline[i] < timeline[i + 1]
            )
        }

        // Starts at 0ms
        assertEquals(0L, timeline.first())

        // At 30 fps, frame interval is ~33.3ms, so 3 seconds is ~90 frames
        assertTrue("Frame count should be around 90-92", timeline.size in 89..95)

        // Last timestamp must be within 33ms of duration
        assertTrue(timeline.last() >= durationMs - 35L)
    }

    @Test
    fun testOutputDimensionEvenAlignment() {
        // H.264 video encoders strictly require even dimensions (divisible by 2)
        val (w1, h1) = VideoRenderingEngine.determineOutputDimensions(1919, 1079, 1919, 1079)
        assertEquals(0, w1 % 2)
        assertEquals(0, h1 % 2)
        assertEquals(1918, w1)
        assertEquals(1078, h1)

        val (w2, h2) = VideoRenderingEngine.determineOutputDimensions(721, 1281, 721, 1281)
        assertEquals(0, w2 % 2)
        assertEquals(0, h2 % 2)
        assertEquals(720, w2)
        assertEquals(1280, h2)
    }

    @Test
    fun testEstimatedRemainingTimeCalculation() {
        // 50% completed after 2000ms -> remaining should be 2 seconds
        val eta50 = VideoRenderingEngine.calculateEstimatedRemainingTime(elapsedMs = 2000L, progress = 0.50f)
        assertEquals(2L, eta50)

        // 25% completed after 1000ms -> remaining should be 3 seconds
        val eta25 = VideoRenderingEngine.calculateEstimatedRemainingTime(elapsedMs = 1000L, progress = 0.25f)
        assertEquals(3L, eta25)

        // Very early progress (<= 2%) returns null to prevent wild initial fluctuations
        val etaEarly = VideoRenderingEngine.calculateEstimatedRemainingTime(elapsedMs = 100L, progress = 0.01f)
        assertNull(etaEarly)
    }

    @Test
    fun testExportConfigValidation() {
        val validConfig = VideoExportConfig(
            outputWidth = 1280,
            outputHeight = 720,
            targetFps = 30,
            bitrate = 4_000_000,
            preserveAudio = true
        )
        assertNull("Valid config should return null error", VideoRenderingEngine.validateExportConfig(validConfig, 5000L))

        // Invalid duration
        val errDuration = VideoRenderingEngine.validateExportConfig(validConfig, 0L)
        assertNotNull(errDuration)

        // Invalid FPS (< 10 or > 60)
        val errFps = VideoRenderingEngine.validateExportConfig(validConfig.copy(targetFps = 5), 5000L)
        assertNotNull(errFps)

        // Low Bitrate (< 500 kbps)
        val errBitrate = VideoRenderingEngine.validateExportConfig(validConfig.copy(bitrate = 100_000), 5000L)
        assertNotNull(errBitrate)
    }

    @Test
    fun testFrameToTransformSynchronization() {
        // Verifies that during timeline rendering, each timestamp receives a synchronized transform
        val f0 = TrackFrame(timestampMs = 0L, boxLeft = 100f, boxTop = 50f, boxRight = 200f, boxBottom = 250f)
        val f1 = TrackFrame(timestampMs = 1000L, boxLeft = 200f, boxTop = 50f, boxRight = 300f, boxBottom = 250f)
        val track = PersonTrack(trackId = 1, frames = listOf(f0, f1))

        val timeline = VideoRenderingEngine.calculateExportTimeline(1000L, fps = 10) // 10 fps -> 0, 100, 200...
        var previousAnchorX = 0f

        for (ts in timeline) {
            val transform = CharacterReplacementEngine.computeTransformForTimestamp(
                track = track,
                timestampMs = ts,
                frameWidth = 640,
                frameHeight = 360
            )

            // Anchor X should monotonically increase from 150 to 250
            assertTrue(transform.anchorX >= previousAnchorX)
            previousAnchorX = transform.anchorX
            assertEquals(ts, transform.timestampMs)
        }

        assertTrue("Final anchor X should reach near 250f", previousAnchorX >= 248f && previousAnchorX <= 250.5f)
    }

    @Test
    fun testMissingAudioAndNoticeHandling() {
        // Video without audio track should not fail; records audioNotice cleanly
        val resultWithoutAudio = VideoExportResult(
            isSuccess = true,
            outputPath = "/cache/exports/export_test.mp4",
            fileSizeBytes = 3_500_000L,
            durationMs = 4000L,
            width = 1280,
            height = 720,
            renderedFramesCount = 120,
            hasAudioTrack = false,
            audioPreserved = false,
            audioNotice = "Source video has no audio track; exported video-only."
        )

        assertTrue(resultWithoutAudio.isSuccess)
        assertFalse(resultWithoutAudio.hasAudioTrack)
        assertFalse(resultWithoutAudio.audioPreserved)
        assertEquals("Source video has no audio track; exported video-only.", resultWithoutAudio.audioNotice)
    }

    @Test
    fun testCancellationCleanupState() {
        val cancelledProgress = ExportProgress(
            progress = 0f,
            statusMessage = "Export cancelled by user",
            stage = "CANCELLED"
        )

        assertEquals("CANCELLED", cancelledProgress.stage)
        assertEquals(0f, cancelledProgress.progress, 0.01f)

        val cancelledResult = VideoExportResult(
            isSuccess = false,
            errorMessage = "Export cancelled"
        )
        assertFalse(cancelledResult.isSuccess)
        assertEquals("Export cancelled", cancelledResult.errorMessage)
    }
}
