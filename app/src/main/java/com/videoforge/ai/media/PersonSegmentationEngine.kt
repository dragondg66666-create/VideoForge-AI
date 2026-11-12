package com.videoforge.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.videoforge.ai.model.MaskMetrics
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.model.SegmentationFrame
import com.videoforge.ai.model.SegmentationSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 2.5: Real Person Segmentation & Alpha Mask Generation Engine
 *
 * Integrates Google ML Kit Selfie Segmentation to extract real on-device foreground
 * probability masks from decoded video frames.
 *
 * ARCHITECTURAL NOTICE & MODEL CHARACTERISTICS:
 * - Google ML Kit Selfie Segmentation processes all human figures in the frame into a
 *   single unified foreground probability mask (0.0f = background, 1.0f = human foreground).
 * - This model does NOT perform multi-instance segmentation (it cannot separate Person A from Person B).
 * - Per requirements, we do NOT synthesize or fabricate per-person individual masks.
 * - Mask bounding boxes and coverage metrics represent the true segmented human foreground.
 * - Soft edge feathering is calculated via smooth linear transition around the threshold
 *   to eliminate jagged pixel artifacts.
 */
object PersonSegmentationEngine {

    private const val TAG = "PersonSegEngine"
    const val DEFAULT_THRESHOLD = 0.50f
    const val DEFAULT_FEATHER_RADIUS = 0.06f

    data class RawMaskProcessingResult(
        val maskBitmap: Bitmap,
        val cutoutBitmap: Bitmap,
        val metrics: MaskMetrics
    )

    data class SegmentationExecutionResult(
        val summary: SegmentationSummary,
        val totalFramesProcessed: Int
    )

    /**
     * Executes real ML Kit Selfie Segmentation across sampled video frames from the content Uri.
     */
    suspend fun segmentVideo(
        context: Context,
        uri: Uri,
        projectId: String,
        existingTracks: List<PersonTrack> = emptyList(),
        samplingIntervalMs: Long = 1000L,
        threshold: Float = DEFAULT_THRESHOLD,
        featherRadius: Float = DEFAULT_FEATHER_RADIUS,
        onProgress: suspend (progress: Float, detail: String) -> Unit
    ): SegmentationExecutionResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val segmentationFrames = mutableListOf<SegmentationFrame>()

        val segCacheDir = File(context.cacheDir, "segmentation/$projectId").apply {
            mkdirs()
        }

        // Initialize ML Kit Selfie Segmenter with STREAM_MODE and RawSizeMask enabled
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build()

        val segmenter: Segmenter = Segmentation.getClient(options)

        try {
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            if (durationMs <= 0L) {
                Log.w(TAG, "Video duration missing or zero; aborting segmentation")
                return@withContext SegmentationExecutionResult(
                    summary = SegmentationSummary(),
                    totalFramesProcessed = 0
                )
            }

            // Cap frames to prevent out-of-memory while providing sufficient coverage
            val targetInterval = max(500L, samplingIntervalMs)
            val timestamps = mutableListOf<Long>()
            var t = 0L
            while (t < durationMs) {
                timestamps.add(t)
                t += targetInterval
            }
            if (timestamps.isEmpty() || timestamps.last() < durationMs - 200L) {
                timestamps.add(max(0L, durationMs - 100L))
            }

            val totalSteps = timestamps.size
            Log.d(TAG, "Starting segmentation on $totalSteps frames (duration: ${durationMs}ms)")

            var cumulativeCoverage = 0f

            for ((index, timestampMs) in timestamps.withIndex()) {
                coroutineContext.ensureActive()

                val stepProgress = (index.toFloat() / totalSteps.toFloat()) * 0.95f
                val formattedTime = formatTimestamp(timestampMs)
                onProgress(stepProgress, "Generating person alpha mask $index/$totalSteps ($formattedTime)…")

                // Extract frame bitmap at timestamp (target resolution 640x360 for high-fidelity segmentation without OOM)
                val frameBitmap = getFrameAt(retriever, timestampMs, targetWidth = 640, targetHeight = 360)
                if (frameBitmap == null) {
                    Log.w(TAG, "Frame extraction failed at $timestampMs ms")
                    continue
                }

                try {
                    // Run real ML Kit Selfie Segmentation
                    val inputImage = InputImage.fromBitmap(frameBitmap, 0)
                    val mask = runSegmentationInference(segmenter, inputImage)

                    // Process raw float buffer into alpha mask and foreground cutout
                    val processedResult = processSegmentationMask(
                        mask = mask,
                        sourceBitmap = frameBitmap,
                        threshold = threshold,
                        featherRadius = featherRadius
                    )

                    // Save artifacts to disk
                    val frameBaseName = "seg_${timestampMs}_${System.currentTimeMillis()}"
                    val sourceFile = File(segCacheDir, "${frameBaseName}_source.jpg")
                    val maskFile = File(segCacheDir, "${frameBaseName}_mask.png")
                    val cutoutFile = File(segCacheDir, "${frameBaseName}_cutout.png")

                    saveBitmapToFile(frameBitmap, sourceFile, Bitmap.CompressFormat.JPEG, 85)
                    saveBitmapToFile(processedResult.maskBitmap, maskFile, Bitmap.CompressFormat.PNG, 100)
                    saveBitmapToFile(processedResult.cutoutBitmap, cutoutFile, Bitmap.CompressFormat.PNG, 100)

                    // Find intersecting person tracks around this timestamp
                    val associatedTracks = findIntersectingTracks(
                        timestampMs = timestampMs,
                        tracks = existingTracks,
                        maskMetrics = processedResult.metrics
                    )

                    val segFrame = SegmentationFrame(
                        timestampMs = timestampMs,
                        formattedTime = formattedTime,
                        sourceFramePath = sourceFile.absolutePath,
                        maskPath = maskFile.absolutePath,
                        foregroundCutoutPath = cutoutFile.absolutePath,
                        confidenceThreshold = threshold,
                        featherRadius = featherRadius,
                        metrics = processedResult.metrics,
                        associatedTrackIds = associatedTracks
                    )

                    segmentationFrames.add(segFrame)
                    cumulativeCoverage += processedResult.metrics.coveragePercent

                    // Clean up native bitmaps for this frame
                    processedResult.maskBitmap.recycle()
                    processedResult.cutoutBitmap.recycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Segmentation failed for frame at $timestampMs ms", e)
                } finally {
                    frameBitmap.recycle()
                }
            }

            val avgCoverage = if (segmentationFrames.isNotEmpty()) {
                cumulativeCoverage / segmentationFrames.size
            } else 0f

            val summary = SegmentationSummary(
                totalFramesSegmented = segmentationFrames.size,
                averageCoveragePercent = avgCoverage,
                modelType = "Google ML Kit Selfie Segmentation (Stream Raw Float Mask)",
                segmentationNotice = "ML Kit produces a unified foreground probability mask for all persons in frame. Model does not perform multi-instance person separation.",
                frames = segmentationFrames
            )

            onProgress(1.0f, "Completed person segmentation: ${segmentationFrames.size} alpha masks generated")

            SegmentationExecutionResult(
                summary = summary,
                totalFramesProcessed = segmentationFrames.size
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing retriever", e)
            }
            try {
                segmenter.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing segmenter", e)
            }
        }
    }

    /**
     * Executes ML Kit segmentation task synchronously via coroutine continuation.
     */
    private suspend fun runSegmentationInference(
        segmenter: Segmenter,
        inputImage: InputImage
    ): SegmentationMask = suspendCancellableCoroutine { continuation ->
        val task: Task<SegmentationMask> = segmenter.process(inputImage)
        task.addOnSuccessListener { mask ->
            if (continuation.isActive) {
                continuation.resume(mask)
            }
        }.addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
    }

    /**
     * Converts ML Kit float probability buffer into:
     * 1) Monochrome grayscale mask bitmap (White = foreground, Black = background)
     * 2) Cutout bitmap (Source RGB preserved with calculated alpha)
     * 3) Mask metrics (coverage %, bounding box, avg confidence)
     */
    fun processSegmentationMask(
        mask: SegmentationMask,
        sourceBitmap: Bitmap,
        threshold: Float = DEFAULT_THRESHOLD,
        featherRadius: Float = DEFAULT_FEATHER_RADIUS
    ): RawMaskProcessingResult {
        val maskWidth = mask.width
        val maskHeight = mask.height
        val totalPixels = maskWidth * maskHeight

        val buffer: ByteBuffer = mask.buffer
        buffer.rewind()
        val floatBuffer: FloatBuffer = buffer.asFloatBuffer()

        // Create monochrome mask pixels and cutout pixels
        val maskPixels = IntArray(totalPixels)
        val cutoutPixels = IntArray(totalPixels)

        // Read source pixels matching mask dimensions
        val scaledSource: Bitmap = if (sourceBitmap.width != maskWidth || sourceBitmap.height != maskHeight) {
            Bitmap.createScaledBitmap(sourceBitmap, maskWidth, maskHeight, true)
        } else {
            sourceBitmap
        }

        val sourcePixels = IntArray(totalPixels)
        scaledSource.getPixels(sourcePixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

        var foregroundCount = 0
        var confidenceSum = 0f

        var minX = maskWidth
        var maxX = 0
        var minY = maskHeight
        var maxY = 0

        val lowerBound = max(0f, threshold - featherRadius)
        val upperBound = min(1f, threshold + featherRadius)
        val featherSpan = max(0.001f, upperBound - lowerBound)

        for (y in 0 until maskHeight) {
            val rowOffset = y * maskWidth
            for (x in 0 until maskWidth) {
                val index = rowOffset + x
                val prob = floatBuffer.get(index)

                // Feathered alpha calculation
                val alpha: Int = when {
                    prob <= lowerBound -> 0
                    prob >= upperBound -> 255
                    else -> {
                        val factor = (prob - lowerBound) / featherSpan
                        (factor * 255f).toInt().coerceIn(0, 255)
                    }
                }

                // If pixel is considered foreground
                if (prob >= threshold) {
                    foregroundCount++
                    confidenceSum += prob
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }

                // Monochrome mask: Grayscale luminance = alpha
                maskPixels[index] = Color.argb(255, alpha, alpha, alpha)

                // Cutout: original RGB + calculated feathered alpha
                val srcColor = sourcePixels[index]
                val r = Color.red(srcColor)
                val g = Color.green(srcColor)
                val b = Color.blue(srcColor)
                cutoutPixels[index] = Color.argb(alpha, r, g, b)
            }
        }

        // Clean up temporary scaled source if allocated
        if (scaledSource != sourceBitmap) {
            scaledSource.recycle()
        }

        val coveragePercent = if (totalPixels > 0) {
            (foregroundCount.toFloat() / totalPixels.toFloat()) * 100f
        } else 0f

        val avgConfidence = if (foregroundCount > 0) {
            confidenceSum / foregroundCount
        } else 0f

        val (normLeft, normTop, normRight, normBottom) = if (foregroundCount > 0 && maxX >= minX && maxY >= minY) {
            listOf(
                minX.toFloat() / maskWidth,
                minY.toFloat() / maskHeight,
                (maxX + 1).toFloat() / maskWidth,
                (maxY + 1).toFloat() / maskHeight
            )
        } else {
            listOf(0f, 0f, 0f, 0f)
        }

        val metrics = MaskMetrics(
            maskWidth = maskWidth,
            maskHeight = maskHeight,
            coveragePercent = coveragePercent,
            foregroundPixelCount = foregroundCount,
            boundingBoxLeft = normLeft,
            boundingBoxTop = normTop,
            boundingBoxRight = normRight,
            boundingBoxBottom = normBottom,
            averageConfidence = avgConfidence
        )

        val maskBitmap = Bitmap.createBitmap(maskPixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val cutoutBitmap = Bitmap.createBitmap(cutoutPixels, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)

        return RawMaskProcessingResult(
            maskBitmap = maskBitmap,
            cutoutBitmap = cutoutBitmap,
            metrics = metrics
        )
    }

    /**
     * Calculates soft feathered alpha value for a given probability.
     */
    fun computeFeatheredAlpha(
        probability: Float,
        threshold: Float = DEFAULT_THRESHOLD,
        featherRadius: Float = DEFAULT_FEATHER_RADIUS
    ): Int {
        val lower = max(0f, threshold - featherRadius)
        val upper = min(1f, threshold + featherRadius)
        val span = max(0.001f, upper - lower)

        return when {
            probability <= lower -> 0
            probability >= upper -> 255
            else -> {
                val factor = (probability - lower) / span
                (factor * 255f).toInt().coerceIn(0, 255)
            }
        }
    }

    /**
     * Determines which person tracks intersect with the foreground mask bounding box.
     */
    private fun findIntersectingTracks(
        timestampMs: Long,
        tracks: List<PersonTrack>,
        maskMetrics: MaskMetrics
    ): List<Int> {
        if (maskMetrics.foregroundPixelCount == 0 || tracks.isEmpty()) return emptyList()

        val maskRect = RectF(
            maskMetrics.boundingBoxLeft,
            maskMetrics.boundingBoxTop,
            maskMetrics.boundingBoxRight,
            maskMetrics.boundingBoxBottom
        )

        val matchedIds = mutableListOf<Int>()
        for (track in tracks) {
            // Find frame closest to timestamp within 500ms
            val closestFrame = track.frames.minByOrNull { kotlin.math.abs(it.timestampMs - timestampMs) }
            if (closestFrame != null && kotlin.math.abs(closestFrame.timestampMs - timestampMs) <= 750L) {
                val personRect = RectF(
                    closestFrame.boxLeft,
                    closestFrame.boxTop,
                    closestFrame.boxRight,
                    closestFrame.boxBottom
                )
                if (RectF.intersects(maskRect, personRect)) {
                    matchedIds.add(track.trackId)
                }
            }
        }
        return matchedIds
    }

    private fun getFrameAt(
        retriever: MediaMetadataRetriever,
        timeMs: Long,
        targetWidth: Int = 640,
        targetHeight: Int = 360
    ): Bitmap? {
        val timeUs = timeMs * 1000L
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get frame at $timeMs ms", e)
            null
        }
    }

    private fun saveBitmapToFile(
        bitmap: Bitmap,
        file: File,
        format: Bitmap.CompressFormat,
        quality: Int
    ) {
        FileOutputStream(file).use { out ->
            bitmap.compress(format, quality, out)
            out.flush()
        }
    }

    private fun formatTimestamp(timeMs: Long): String {
        val totalSec = timeMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val ms = timeMs % 1000
        return String.format(Locale.US, "%02d:%02d.%03d", min, sec, ms)
    }
}
