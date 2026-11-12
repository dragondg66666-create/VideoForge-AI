package com.videoforge.ai.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.videoforge.ai.model.SourceVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object VideoMetadataExtractor {

    private const val TAG = "VideoMetadataExtractor"

    private fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    suspend fun extractMetadata(
        context: Context,
        uri: Uri,
        filename: String,
        sizeBytes: Long
    ): SourceVideo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val durationSeconds = durationMs / 1000.0

            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            // If video is rotated 90 or 270 degrees, swap effective width & height for display
            val (effectiveWidth, effectiveHeight) = if (rotation == 90 || rotation == 270) {
                Pair(rawHeight, rawWidth)
            } else {
                Pair(rawWidth, rawHeight)
            }

            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"

            // Attempt to extract frame rate
            var fps = 24.0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val captureFpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                val parsedCapture = captureFpsStr?.toDoubleOrNull()
                if (parsedCapture != null && parsedCapture > 0.0) {
                    fps = parsedCapture
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && fps == 24.0) {
                val frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                val frameCount = frameCountStr?.toIntOrNull()
                if (frameCount != null && frameCount > 0 && durationSeconds > 0) {
                    val computedFps = frameCount / durationSeconds
                    if (computedFps in 10.0..120.0) {
                        fps = Math.round(computedFps * 100.0) / 100.0
                    }
                }
            }

            val resolutionLabel = "${effectiveWidth}x${effectiveHeight}${if (rotation != 0) " (${rotation}°)" else ""}"

            SourceVideo(
                filename = filename,
                uriString = uri.toString(),
                sizeBytes = sizeBytes,
                contentType = mimeType,
                durationMs = durationMs,
                durationSeconds = durationSeconds,
                width = effectiveWidth,
                height = effectiveHeight,
                rotation = rotation,
                fps = fps,
                resolution = resolutionLabel,
                bitrate = bitrate,
                uploadedAt = nowIso()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata for $uri: ${e.message}", e)
            // Graceful fallback with standard metadata
            SourceVideo(
                filename = filename,
                uriString = uri.toString(),
                sizeBytes = sizeBytes,
                contentType = "video/mp4",
                durationMs = 0L,
                durationSeconds = 0.0,
                width = 1920,
                height = 1080,
                rotation = 0,
                fps = 24.0,
                resolution = "1920x1080",
                bitrate = 0L,
                uploadedAt = nowIso()
            )
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }
}
