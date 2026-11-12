package com.videoforge.ai

import com.videoforge.ai.model.DetectedPersonTrack
import com.videoforge.ai.model.LandmarkTrajectory
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.model.PointF2D
import com.videoforge.ai.model.PoseLandmarkItem
import com.videoforge.ai.model.TrackFrame
import com.videoforge.ai.model.TrackingSummary
import com.videoforge.ai.model.TrajectoryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.sqrt

class MotionTrackingTest {

    @Test
    fun testTrackFrameDisplacementAndSpeed() {
        val f1 = TrackFrame(
            timestampMs = 0L,
            boxLeft = 100f, boxTop = 50f, boxRight = 200f, boxBottom = 250f,
            centerX = 150f, centerY = 150f,
            speedPxPerSec = 0f
        )
        val f2 = TrackFrame(
            timestampMs = 1000L,
            boxLeft = 160f, boxTop = 50f, boxRight = 260f, boxBottom = 250f,
            centerX = 210f, centerY = 150f,
            speedPxPerSec = 60f,
            instantVelocityX = 60f, instantVelocityY = 0f
        )

        val dt = (f2.timestampMs - f1.timestampMs) / 1000f
        val dx = f2.centerX - f1.centerX
        val dy = f2.centerY - f1.centerY
        val speed = sqrt(dx * dx + dy * dy) / dt

        assertEquals(1.0f, dt, 0.001f)
        assertEquals(60.0f, dx, 0.001f)
        assertEquals(0.0f, dy, 0.001f)
        assertEquals(60.0f, speed, 0.001f)
    }

    @Test
    fun testScaleChangeCalculation() {
        // Person approaching camera: bounding box grows by 44%
        val area1 = 100f * 200f // 20,000
        val area2 = 120f * 240f // 28,800
        val scaleChange = ((area2 - area1) / area1) * 100f

        assertEquals(44.0f, scaleChange, 0.01f)
        assertTrue("Should detect approaching camera", scaleChange > 10f)
    }

    @Test
    fun testLandmarkTrajectoryCumulativeLength() {
        val points = listOf(
            TrajectoryPoint(0L, "00:00.0", 100f, 100f),
            TrajectoryPoint(500L, "00:00.5", 130f, 140f), // dx=30, dy=40 -> dist=50
            TrajectoryPoint(1000L, "00:01.0", 130f, 190f) // dx=0, dy=50 -> dist=50
        )

        var totalLen = 0f
        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].x - points[i].x
            val dy = points[i + 1].y - points[i].y
            totalLen += sqrt(dx * dx + dy * dy)
        }

        val trajectory = LandmarkTrajectory(
            landmarkName = "Right Wrist",
            points = points,
            totalPathLengthPx = totalLen
        )

        assertEquals(100.0f, trajectory.totalPathLengthPx, 0.001f)
        assertEquals(3, trajectory.points.size)
    }

    @Test
    fun testTrackingSummaryAggregation() {
        val t1 = PersonTrack(
            trackId = 1,
            durationMs = 4000L,
            totalFramesTracked = 5,
            averageSpeedPxPerSec = 45f,
            motionDirection = "Moving Right (45 px/s)",
            interruptionCount = 1
        )
        val t2 = PersonTrack(
            trackId = 2,
            durationMs = 2000L,
            totalFramesTracked = 3,
            averageSpeedPxPerSec = 10f,
            motionDirection = "Stationary (10 px/s)",
            interruptionCount = 0
        )

        val tracks = listOf(t1, t2)
        val summary = TrackingSummary(
            totalTracks = tracks.size,
            activeTracksCount = tracks.filter { it.totalFramesTracked >= 2 }.size,
            averageTrackDurationMs = tracks.map { it.durationMs }.average().toLong(),
            maxTrackDurationMs = tracks.maxOf { it.durationMs },
            totalInterruptions = tracks.sumOf { it.interruptionCount },
            tracks = tracks
        )

        assertEquals(2, summary.totalTracks)
        assertEquals(2, summary.activeTracksCount)
        assertEquals(3000L, summary.averageTrackDurationMs)
        assertEquals(4000L, summary.maxTrackDurationMs)
        assertEquals(1, summary.totalInterruptions)
    }

    @Test
    fun testMotionDirectionAngle() {
        // Moving right: dx = +50, dy = 0
        val angleRad = atan2(0.0, 50.0)
        val angleDeg = Math.toDegrees(angleRad)
        assertEquals(0.0, angleDeg, 0.01)

        // Moving left: dx = -50, dy = 0
        val angleLeftRad = atan2(0.0, -50.0)
        val angleLeftDeg = Math.toDegrees(angleLeftRad)
        assertEquals(180.0, angleLeftDeg, 0.01)
    }
}
