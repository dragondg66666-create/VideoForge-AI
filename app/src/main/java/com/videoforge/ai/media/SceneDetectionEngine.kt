package com.videoforge.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.videoforge.ai.model.DetectedScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

object SceneDetectionEngine {

    private const val TAG = "SceneDetectionEngine"
    private const val DEFAULT_CUT_THRESHOLD = 0.36f // Normalized Manhattan distance threshold
    private const val MIN_SCENE_DURATION_MS = 1000L  // Minimum time between scene cuts

    data class DetectionResult(
        val scenes: List<DetectedScene>,
        val totalFramesSampled: Int,
        val sceneCutsCount: Int,
        val averageMotionDiff: Float
    )

    suspend fun detectScenes(
        context: Context,
        uri: Uri,
        projectId: String,
        samplingIntervalMs: Long = 500L,
        cutThreshold: Float = DEFAULT_CUT_THRESHOLD,
        onProgress: suspend (progress: Float, detail: String) -> Unit
    ): DetectionResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val detectedScenes = mutableListOf<DetectedScene>()
        var framesSampled = 0
        var totalDiffSum = 0f

        val cacheDir = File(context.cacheDir, "keyframes/$projectId").apply {
            mkdirs()
        }

        try {
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            if (durationMs <= 0L) {
                throw IllegalArgumentException("Cannot determine video duration or video track is empty")
            }

            onProgress(0.05f, "Preparing frame sampler for ${(durationMs / 1000.0).toInt()}s footage…")

            // Capture initial scene (Scene 1) at t=0
            var previousHistogram: FloatArray? = null
            var lastSceneCutTimestamp = 0L

            val firstFrameBitmap = getFrameAt(retriever, 0L, targetWidth = 480, targetHeight = 270)
            if (firstFrameBitmap != null) {
                val thumbFile = File(cacheDir, "scene_01_${System.currentTimeMillis()}.jpg")
                saveBitmapToJpeg(firstFrameBitmap, thumbFile)
                detectedScenes.add(
                    DetectedScene(
                        sceneIndex = 1,
                        timestampMs = 0L,
                        formattedTime = formatTime(0L),
                        thumbnailPath = thumbFile.absolutePath,
                        diffScore = 1.0f,
                        description = "Scene 01: Initial master anchor"
                    )
                )
                previousHistogram = computeColorHistogram(firstFrameBitmap)
                framesSampled++
            }

            // Sampling loop across the video duration
            val totalSteps = (durationMs / samplingIntervalMs).coerceAtLeast(1L)
            var currentTimestamp = samplingIntervalMs

            while (currentTimestamp < durationMs) {
                coroutineContext.ensureActive()

                val progress = (currentTimestamp.toFloat() / durationMs.toFloat()).coerceIn(0.1f, 0.95f)
                val timeLabel = formatTime(currentTimestamp)
                onProgress(progress, "Analyzing frame at $timeLabel (Sample #${framesSampled + 1})")

                // Extract lightweight scaled frame for fast histogram calculation
                val sampleBitmap = getFrameAt(retriever, currentTimestamp, targetWidth = 160, targetHeight = 90)
                if (sampleBitmap != null) {
                    val currentHistogram = computeColorHistogram(sampleBitmap)
                    framesSampled++

                    if (previousHistogram != null) {
                        val diff = computeHistogramDifference(previousHistogram, currentHistogram)
                        totalDiffSum += diff

                        val timeSinceLastCut = currentTimestamp - lastSceneCutTimestamp
                        if (diff >= cutThreshold && timeSinceLastCut >= MIN_SCENE_DURATION_MS) {
                            val sceneIdx = detectedScenes.size + 1
                            val cutTimestamp = currentTimestamp
                            lastSceneCutTimestamp = cutTimestamp

                            // Extract higher quality thumbnail for the detected scene cut
                            val keyframe = getFrameAt(retriever, cutTimestamp, targetWidth = 480, targetHeight = 270)
                                ?: sampleBitmap

                            val thumbFile = File(cacheDir, "scene_${sceneIdx}_${cutTimestamp}.jpg")
                            saveBitmapToJpeg(keyframe, thumbFile)

                            detectedScenes.add(
                                DetectedScene(
                                    sceneIndex = sceneIdx,
                                    timestampMs = cutTimestamp,
                                    formattedTime = formatTime(cutTimestamp),
                                    thumbnailPath = thumbFile.absolutePath,
                                    diffScore = diff,
                                    description = "Scene ${String.format("%02d", sceneIdx)}: Cut detected (Δ ${(diff * 100).toInt()}%)"
                                )
                            )
                        }
                    }
                    previousHistogram = currentHistogram
                    sampleBitmap.recycle()
                }

                currentTimestamp += samplingIntervalMs
            }

            onProgress(1.0f, "Completed analysis: ${detectedScenes.size} scenes detected across $framesSampled frames.")

            val avgDiff = if (framesSampled > 1) totalDiffSum / (framesSampled - 1) else 0f

            DetectionResult(
                scenes = detectedScenes,
                totalFramesSampled = framesSampled,
                sceneCutsCount = (detectedScenes.size - 1).coerceAtLeast(0),
                averageMotionDiff = avgDiff
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }
    }

    private fun getFrameAt(
        retriever: MediaMetadataRetriever,
        timestampMs: Long,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val timeUs = timestampMs * 1000L
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            } else {
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (raw != null && (raw.width != targetWidth || raw.height != targetHeight)) {
                    Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true)
                } else raw
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get frame at $timestampMs ms: ${e.message}")
            null
        }
    }

    /**
     * Computes a normalized 48-bin RGB color histogram (16 bins for Red, 16 for Green, 16 for Blue).
     * Sum of histogram bins equals 1.0.
     */
    private fun computeColorHistogram(bitmap: Bitmap): FloatArray {
        val binsPerChannel = 16
        val totalBins = binsPerChannel * 3
        val histogram = FloatArray(totalBins)
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        if (totalPixels == 0) return histogram

        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            val r = (Color.red(pixel) * binsPerChannel) / 256
            val g = (Color.green(pixel) * binsPerChannel) / 256
            val b = (Color.blue(pixel) * binsPerChannel) / 256

            histogram[r.coerceIn(0, binsPerChannel - 1)]++
            histogram[binsPerChannel + g.coerceIn(0, binsPerChannel - 1)]++
            histogram[binsPerChannel * 2 + b.coerceIn(0, binsPerChannel - 1)]++
        }

        // Normalize
        val normalizationFactor = 1.0f / (totalPixels * 3)
        for (i in 0 until totalBins) {
            histogram[i] *= normalizationFactor
        }

        return histogram
    }

    /**
     * Calculates the normalized Manhattan distance between two normalized histograms.
     * Output is between 0.0 (identical) and 1.0 (completely different colors).
     */
    private fun computeHistogramDifference(h1: FloatArray, h2: FloatArray): Float {
        var distance = 0f
        val len = minOf(h1.size, h2.size)
        for (i in 0 until len) {
            distance += kotlin.math.abs(h1[i] - h2[i])
        }
        return (distance * 0.5f).coerceIn(0f, 1f)
    }

    private fun saveBitmapToJpeg(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save keyframe bitmap to ${file.absolutePath}", e)
        }
    }

    fun formatTime(timestampMs: Long): String {
        val totalSeconds = timestampMs / 1000
        val millis = timestampMs % 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%02d:%02d:%02d.%01d", hours, minutes, seconds, millis / 100)
        } else {
            String.format("%02d:%02d.%01d", minutes, seconds, millis / 100)
        }
    }
}
