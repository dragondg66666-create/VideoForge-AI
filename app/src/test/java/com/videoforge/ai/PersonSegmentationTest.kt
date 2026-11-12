package com.videoforge.ai

import com.videoforge.ai.media.PersonSegmentationEngine
import com.videoforge.ai.model.MaskMetrics
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.model.SegmentationFrame
import com.videoforge.ai.model.SegmentationSummary
import com.videoforge.ai.model.TrackFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonSegmentationTest {

    @Test
    fun testFeatheredAlphaUnderLowerBound() {
        val threshold = 0.50f
        val featherRadius = 0.06f

        // Lower bound is 0.44f. Any prob <= 0.44f must return alpha 0
        val alphaZero = PersonSegmentationEngine.computeFeatheredAlpha(0.35f, threshold, featherRadius)
        assertEquals(0, alphaZero)

        val alphaBoundary = PersonSegmentationEngine.computeFeatheredAlpha(0.44f, threshold, featherRadius)
        assertEquals(0, alphaBoundary)
    }

    @Test
    fun testFeatheredAlphaOverUpperBound() {
        val threshold = 0.50f
        val featherRadius = 0.06f

        // Upper bound is 0.56f. Any prob >= 0.56f must return alpha 255
        val alphaFull = PersonSegmentationEngine.computeFeatheredAlpha(0.75f, threshold, featherRadius)
        assertEquals(255, alphaFull)

        val alphaBoundary = PersonSegmentationEngine.computeFeatheredAlpha(0.56f, threshold, featherRadius)
        assertEquals(255, alphaBoundary)
    }

    @Test
    fun testFeatheredAlphaSoftTransition() {
        val threshold = 0.50f
        val featherRadius = 0.06f

        // Exactly at threshold (0.50f), factor should be 0.5 -> ~127
        val alphaMid = PersonSegmentationEngine.computeFeatheredAlpha(0.50f, threshold, featherRadius)
        assertTrue("Alpha at midpoint should be around 127, got $alphaMid", alphaMid in 126..128)

        // Monotonic increase check across the feathering window
        val a1 = PersonSegmentationEngine.computeFeatheredAlpha(0.45f, threshold, featherRadius)
        val a2 = PersonSegmentationEngine.computeFeatheredAlpha(0.48f, threshold, featherRadius)
        val a3 = PersonSegmentationEngine.computeFeatheredAlpha(0.52f, threshold, featherRadius)
        val a4 = PersonSegmentationEngine.computeFeatheredAlpha(0.55f, threshold, featherRadius)

        assertTrue(a1 < a2)
        assertTrue(a2 < a3)
        assertTrue(a3 < a4)
    }

    @Test
    fun testMaskCoverageMetrics() {
        val width = 640
        val height = 360
        val totalPixels = width * height // 230,400

        val foregroundCount = 46080 // 20%
        val coverage = (foregroundCount.toFloat() / totalPixels.toFloat()) * 100f

        assertEquals(20.0f, coverage, 0.001f)

        val metrics = MaskMetrics(
            maskWidth = width,
            maskHeight = height,
            coveragePercent = coverage,
            foregroundPixelCount = foregroundCount,
            boundingBoxLeft = 0.20f,
            boundingBoxTop = 0.15f,
            boundingBoxRight = 0.55f,
            boundingBoxBottom = 0.90f,
            averageConfidence = 0.93f
        )

        assertEquals(640, metrics.maskWidth)
        assertEquals(360, metrics.maskHeight)
        assertEquals(20.0f, metrics.coveragePercent, 0.01f)
        assertEquals(0.93f, metrics.averageConfidence, 0.01f)
    }

    @Test
    fun testUnifiedForegroundModelNoticePreservation() {
        val summary = SegmentationSummary(
            totalFramesSegmented = 3,
            averageCoveragePercent = 21.4f,
            modelType = "Google ML Kit Selfie Segmentation",
            segmentationNotice = "ML Kit produces a unified foreground probability mask for all persons in frame. Model does not perform multi-instance person separation.",
            frames = emptyList()
        )

        assertTrue("Must declare unified foreground model", summary.modelType.contains("ML Kit"))
        assertTrue("Must state non-multi-instance constraint", summary.segmentationNotice.contains("does not perform multi-instance"))
    }
}
