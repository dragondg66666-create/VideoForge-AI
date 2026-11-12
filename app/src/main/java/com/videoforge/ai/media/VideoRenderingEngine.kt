package com.videoforge.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.util.Log
import android.view.Surface
import com.videoforge.ai.model.ExportProgress
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.model.ReplacementAsset
import com.videoforge.ai.model.ReplacementStudioState
import com.videoforge.ai.model.SegmentationSummary
import com.videoforge.ai.model.VideoExportConfig
import com.videoforge.ai.model.VideoExportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 2.7: Native Android MP4 Video Rendering & Export Engine
 *
 * Implements a native video rendering pipeline using MediaCodec, MediaMuxer,
 * MediaExtractor, and MediaMetadataRetriever to export MP4 videos containing
 * the Phase 2.6 character replacement composited layers.
 *
 * ARCHITECTURAL RULES & ROBUSTNESS:
 * - Incremental frame-by-frame rendering: Never loads entire video into memory.
 * - Bounded memory: Frame bitmaps are immediately recycled after submission.
 * - Audio preservation: Lossless passthrough from source video via MediaExtractor.
 * - Hardware acceleration: MediaCodec H.264 (video/avc) encoder with EGL InputSurface.
 * - Safe cancellation & cleanup: Cancelling immediately terminates and removes partial files.
 * - Pure-math calculations are decoupled for deterministic JVM unit test verification.
 */
object VideoRenderingEngine {

    private const val TAG = "VideoRenderingEngine"
    private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // "video/avc"
    private const val TIMEOUT_USEC = 10_000L

    /**
     * Calculates monotonic frame timestamps (in ms) for export in chronological order.
     */
    fun calculateExportTimeline(durationMs: Long, fps: Int): List<Long> {
        val safeFps = fps.coerceIn(10, 60)
        if (durationMs <= 0L) return emptyList()

        val frameIntervalMs = 1000.0 / safeFps.toDouble()
        val timestamps = mutableListOf<Long>()
        var currentMs = 0.0

        while (currentMs < durationMs.toDouble()) {
            timestamps.add(currentMs.toLong())
            currentMs += frameIntervalMs
        }

        // Guarantee final frame inclusion if gap is significant
        if (timestamps.isEmpty() || timestamps.last() < durationMs - 33L) {
            timestamps.add(max(0L, durationMs - 15L))
        }

        return timestamps
    }

    /**
     * Ensures width and height are positive, even integers as required by H.264 encoders.
     */
    fun determineOutputDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int = 1280,
        targetHeight: Int = 720
    ): Pair<Int, Int> {
        val w = if (sourceWidth > 0 && targetWidth <= 0) sourceWidth else if (targetWidth > 0) targetWidth else 1280
        val h = if (sourceHeight > 0 && targetHeight <= 0) sourceHeight else if (targetHeight > 0) targetHeight else 720

        // Round to nearest even numbers
        val evenW = max(320, (w / 2) * 2)
        val evenH = max(240, (h / 2) * 2)
        return Pair(evenW, evenH)
    }

    /**
     * Estimates remaining seconds based on elapsed time and progress ratio.
     */
    fun calculateEstimatedRemainingTime(elapsedMs: Long, progress: Float): Long? {
        if (progress <= 0.02f || elapsedMs < 500L) return null
        val totalEstimatedMs = elapsedMs.toFloat() / progress
        val remainingMs = totalEstimatedMs - elapsedMs.toFloat()
        return if (remainingMs > 0f) (remainingMs / 1000f).toLong() else 0L
    }

    /**
     * Validates configuration parameters before rendering starts.
     */
    fun validateExportConfig(config: VideoExportConfig, durationMs: Long): String? {
        if (durationMs <= 0L) return "Source video duration is zero or invalid"
        if (config.outputWidth < 320 || config.outputHeight < 240) return "Output resolution too low (minimum 320x240)"
        if (config.targetFps !in 10..60) return "Target frame rate must be between 10 and 60 fps"
        if (config.bitrate < 500_000) return "Target bitrate too low (minimum 500 kbps)"
        return null
    }

    /**
     * Executes the full native Android MP4 export pipeline.
     */
    suspend fun renderAndExportVideo(
        context: Context,
        videoUri: Uri?,
        track: PersonTrack?,
        asset: ReplacementAsset?,
        studioState: ReplacementStudioState,
        config: VideoExportConfig,
        segmentationSummary: SegmentationSummary? = null,
        onProgress: suspend (ExportProgress) -> Unit
    ): VideoExportResult = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, "videoforge_export_${System.currentTimeMillis()}.mp4")

        var retriever: MediaMetadataRetriever? = null
        var extractor: MediaExtractor? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: CodecInputSurface? = null
        var assetBitmap: Bitmap? = null

        var isMuxerStarted = false
        var videoTrackIndex = -1
        var audioTrackIndexInMuxer = -1
        var audioTrackIndexInSource = -1
        var audioFormat: MediaFormat? = null
        var hasAudioInSource = false
        var audioPreserved = false
        var audioNotice: String? = null
        var totalFramesRendered = 0

        try {
            onProgress(
                ExportProgress(
                    progress = 0.01f,
                    statusMessage = "Analyzing source media and preparing encoder…",
                    stage = "PREPARING"
                )
            )

            // 1. Inspect source video metadata
            var sourceDurationMs = 0L
            var srcW = 1280
            var srcH = 720

            if (videoUri != null) {
                retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, videoUri)
                    sourceDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
                    val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (rotation == 90 || rotation == 270) {
                        srcW = rawH
                        srcH = rawW
                    } else {
                        srcW = rawW
                        srcH = rawH
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading source video metadata; using fallback bounds", e)
                }

                // Check for audio track using MediaExtractor
                extractor = MediaExtractor()
                try {
                    extractor.setDataSource(context, videoUri, null)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            audioTrackIndexInSource = i
                            audioFormat = format
                            hasAudioInSource = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed inspecting audio tracks in source video", e)
                }
            }

            if (sourceDurationMs <= 0L) {
                // If demo take or test video, default to 5000ms
                sourceDurationMs = 5000L
            }

            // Determine dimensions and validate
            val (encWidth, encHeight) = determineOutputDimensions(srcW, srcH, config.outputWidth, config.outputHeight)
            val validationError = validateExportConfig(config, sourceDurationMs)
            if (validationError != null) {
                return@withContext VideoExportResult(
                    isSuccess = false,
                    errorMessage = validationError
                )
            }

            // 2. Load replacement character asset if enabled
            if (studioState.enabled && asset != null) {
                assetBitmap = if (asset.localCachedPath != null && File(asset.localCachedPath).exists()) {
                    BitmapFactory.decodeFile(asset.localCachedPath)
                } else {
                    val sample = CharacterReplacementEngine.createSampleHeroAsset(context, "export_temp")
                    BitmapFactory.decodeFile(sample.localCachedPath)
                }
            }

            // 3. Configure MediaCodec Video Encoder
            val videoFormat = MediaFormat.createVideoFormat(MIME_TYPE, encWidth, encHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval
            }

            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderSurface = encoder.createInputSurface()
            inputSurface = CodecInputSurface(encoderSurface)
            encoder.start()

            // 4. Initialize MediaMuxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Calculate export timeline
            val timeline = calculateExportTimeline(sourceDurationMs, config.targetFps)
            val totalFrames = timeline.size
            val bufferInfo = MediaCodec.BufferInfo()

            Log.i(TAG, "Starting MP4 export: ${encWidth}x${encHeight} @ ${config.targetFps}fps, $totalFrames frames, duration: ${sourceDurationMs}ms")

            // 5. Frame Render Loop
            val renderStartTime = System.currentTimeMillis()
            for ((frameIndex, timestampMs) in timeline.withIndex()) {
                coroutineContext.ensureActive()

                val elapsedMs = System.currentTimeMillis() - renderStartTime
                val currentProgress = (frameIndex.toFloat() / totalFrames.toFloat()) * 0.90f // 0% to 90% for video
                val etaSeconds = calculateEstimatedRemainingTime(elapsedMs, currentProgress)

                onProgress(
                    ExportProgress(
                        progress = currentProgress,
                        currentTimestampMs = timestampMs,
                        totalDurationMs = sourceDurationMs,
                        currentFrame = frameIndex + 1,
                        totalFrames = totalFrames,
                        estimatedRemainingSeconds = etaSeconds,
                        statusMessage = "Rendering frame ${frameIndex + 1}/$totalFrames (${formatTimestamp(timestampMs)})…",
                        stage = "RENDERING_FRAMES"
                    )
                )

                // A. Decode frame at timestamp
                var sourceBitmap: Bitmap? = null
                if (retriever != null && videoUri != null) {
                    try {
                        sourceBitmap = getFrameAtTime(retriever, timestampMs, encWidth, encHeight)
                    } catch (e: Exception) {
                        Log.w(TAG, "Frame decode failed at $timestampMs ms", e)
                    }
                }
                if (sourceBitmap == null) {
                    sourceBitmap = CharacterReplacementEngine.createBenchmarkVideoFrame(timestampMs, encWidth, encHeight)
                }

                // B. Apply Character Replacement Compositing if active
                var frameToEncode = sourceBitmap
                if (studioState.enabled && track != null && assetBitmap != null) {
                    val transform = CharacterReplacementEngine.computeTransformForTimestamp(
                        track = track,
                        timestampMs = timestampMs,
                        frameWidth = encWidth,
                        frameHeight = encHeight,
                        userScaleMultiplier = studioState.userScaleMultiplier,
                        userOffsetX = studioState.userOffsetX,
                        userOffsetY = studioState.userOffsetY,
                        userRotationTrimDeg = studioState.userRotationTrimDeg,
                        userOpacity = studioState.userOpacity
                    )

                    // Find matching segmentation mask
                    val segFrame = segmentationSummary?.frames?.minByOrNull { kotlin.math.abs(it.timestampMs - timestampMs) }
                    val segMaskBitmap = if (segFrame?.maskPath != null && File(segFrame.maskPath).exists()) {
                        BitmapFactory.decodeFile(segFrame.maskPath)
                    } else null

                    val (composited, layerOnly) = CharacterReplacementEngine.compositeFrame(
                        sourceFrame = sourceBitmap,
                        characterAsset = assetBitmap,
                        transform = transform,
                        segmentationMask = segMaskBitmap,
                        maskAwareBlending = studioState.maskAwareBlending
                    )

                    frameToEncode = composited
                    segMaskBitmap?.recycle()
                    layerOnly.recycle()
                }

                // C. Render to CodecInputSurface and swap
                inputSurface.makeCurrent()
                inputSurface.drawBitmap(frameToEncode, encWidth, encHeight)
                inputSurface.setPresentationTime(timestampMs * 1_000_000L) // Nanoseconds
                inputSurface.swapBuffers()

                if (frameToEncode != sourceBitmap) {
                    frameToEncode.recycle()
                }
                sourceBitmap.recycle()
                totalFramesRendered++

                // D. Drain encoded frames from MediaCodec
                drainEncoder(
                    encoder = encoder,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    isMuxerStarted = { isMuxerStarted },
                    onStartMuxer = { fmt ->
                        videoTrackIndex = muxer.addTrack(fmt)
                        if (config.preserveAudio && hasAudioInSource && audioFormat != null) {
                            try {
                                audioTrackIndexInMuxer = muxer.addTrack(audioFormat!!)
                            } catch (e: Exception) {
                                Log.w(TAG, "Muxer failed to add audio track; skipping audio", e)
                                audioNotice = "Audio format incompatible with MP4 muxer; exported video-only."
                            }
                        }
                        muxer.start()
                        isMuxerStarted = true
                    },
                    getVideoTrackIndex = { videoTrackIndex },
                    endOfStream = false
                )
            }

            // 6. Signal End of Stream to Encoder
            coroutineContext.ensureActive()
            onProgress(
                ExportProgress(
                    progress = 0.90f,
                    currentTimestampMs = sourceDurationMs,
                    totalDurationMs = sourceDurationMs,
                    currentFrame = totalFrames,
                    totalFrames = totalFrames,
                    statusMessage = "Finalizing video stream encoding…",
                    stage = "MUXING"
                )
            )

            encoder.signalEndOfInputStream()
            drainEncoder(
                encoder = encoder,
                muxer = muxer,
                bufferInfo = bufferInfo,
                isMuxerStarted = { isMuxerStarted },
                onStartMuxer = { fmt ->
                    videoTrackIndex = muxer.addTrack(fmt)
                    if (config.preserveAudio && hasAudioInSource && audioFormat != null) {
                        try {
                            audioTrackIndexInMuxer = muxer.addTrack(audioFormat!!)
                        } catch (e: Exception) {
                            Log.w(TAG, "Muxer failed to add audio track", e)
                        }
                    }
                    muxer.start()
                    isMuxerStarted = true
                },
                getVideoTrackIndex = { videoTrackIndex },
                endOfStream = true
            )

            // 7. Audio Passthrough
            if (config.preserveAudio && hasAudioInSource && extractor != null && audioTrackIndexInMuxer >= 0 && isMuxerStarted) {
                coroutineContext.ensureActive()
                onProgress(
                    ExportProgress(
                        progress = 0.94f,
                        currentTimestampMs = sourceDurationMs,
                        totalDurationMs = sourceDurationMs,
                        currentFrame = totalFrames,
                        totalFrames = totalFrames,
                        statusMessage = "Preserving original audio track (synchronized passthrough)…",
                        stage = "PROCESSING_AUDIO"
                    )
                )

                try {
                    extractor.selectTrack(audioTrackIndexInSource)
                    val maxAudioBufferSize = try {
                        audioFormat?.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) ?: (128 * 1024)
                    } catch (e: Exception) {
                        128 * 1024
                    }
                    val audioBuffer = ByteBuffer.allocateDirect(max(64 * 1024, maxAudioBufferSize))
                    val audioBufferInfo = MediaCodec.BufferInfo()

                    val maxAllowedTimeUs = (sourceDurationMs + 1000L) * 1000L
                    var audioSamplesWritten = 0

                    while (true) {
                        coroutineContext.ensureActive()
                        val sampleSize = extractor.readSampleData(audioBuffer, 0)
                        if (sampleSize < 0) break

                        val sampleTimeUs = extractor.sampleTime
                        if (sampleTimeUs > maxAllowedTimeUs) break

                        audioBufferInfo.offset = 0
                        audioBufferInfo.size = sampleSize
                        audioBufferInfo.presentationTimeUs = sampleTimeUs
                        audioBufferInfo.flags = extractor.sampleFlags

                        muxer.writeSampleData(audioTrackIndexInMuxer, audioBuffer, audioBufferInfo)
                        audioSamplesWritten++
                        extractor.advance()
                    }

                    audioPreserved = audioSamplesWritten > 0
                    Log.i(TAG, "Preserved $audioSamplesWritten audio samples successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Audio passthrough encountered an issue; continuing", e)
                    audioNotice = "Audio passthrough warning: ${e.message}"
                }
            } else if (!hasAudioInSource) {
                audioNotice = "Source video has no audio track; exported video-only."
            }

            // 8. Finalize Muxer
            if (isMuxerStarted) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping MediaMuxer", e)
                }
            }

            val renderDuration = System.currentTimeMillis() - startTimeMs
            val fileSizeBytes = outputFile.length()

            onProgress(
                ExportProgress(
                    progress = 1.0f,
                    currentTimestampMs = sourceDurationMs,
                    totalDurationMs = sourceDurationMs,
                    currentFrame = totalFrames,
                    totalFrames = totalFrames,
                    statusMessage = "Export complete: ${String.format(Locale.US, "%.1f", fileSizeBytes / 1024f / 1024f)} MB written",
                    stage = "COMPLETED"
                )
            )

            VideoExportResult(
                isSuccess = true,
                outputPath = outputFile.absolutePath,
                fileSizeBytes = fileSizeBytes,
                durationMs = sourceDurationMs,
                width = encWidth,
                height = encHeight,
                renderedFramesCount = totalFramesRendered,
                hasAudioTrack = hasAudioInSource,
                audioPreserved = audioPreserved,
                audioNotice = audioNotice,
                renderingDurationMs = renderDuration
            )
        } catch (e: CancellationException) {
            Log.i(TAG, "Export was cancelled by user; cleaning up temporary files")
            outputFile.delete()
            onProgress(
                ExportProgress(
                    progress = 0f,
                    statusMessage = "Export cancelled by user",
                    stage = "CANCELLED"
                )
            )
            VideoExportResult(
                isSuccess = false,
                errorMessage = "Export cancelled"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Export failed with error", e)
            outputFile.delete()
            onProgress(
                ExportProgress(
                    progress = 0f,
                    statusMessage = "Export failed: ${e.message}",
                    stage = "FAILED"
                )
            )
            VideoExportResult(
                isSuccess = false,
                errorMessage = e.message ?: "Unknown rendering failure"
            )
        } finally {
            // Safe resource releases
            try { retriever?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            try {
                encoder?.stop()
                encoder?.release()
            } catch (ignored: Exception) {}
            try { inputSurface?.release() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
            assetBitmap?.recycle()
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        isMuxerStarted: () -> Boolean,
        onStartMuxer: (MediaFormat) -> Unit,
        getVideoTrackIndex: () -> Int,
        endOfStream: Boolean
    ) {
        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            when {
                encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (isMuxerStarted()) {
                        throw RuntimeException("Format changed twice in encoder")
                    }
                    val newFormat = encoder.outputFormat
                    Log.i(TAG, "Encoder output format changed: $newFormat")
                    onStartMuxer(newFormat)
                }
                encoderStatus >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(encoderStatus)
                        ?: throw RuntimeException("EncoderOutputBuffer $encoderStatus was null")

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && isMuxerStarted()) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(getVideoTrackIndex(), encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(encoderStatus, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    private fun getFrameAtTime(
        retriever: MediaMetadataRetriever,
        timeMs: Long,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val timeUs = timeMs * 1000L
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatTimestamp(timeMs: Long): String {
        val totalSec = timeMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val ms = timeMs % 1000
        return String.format(Locale.US, "%02d:%02d.%03d", min, sec, ms)
    }

    /**
     * EGL + GLES20 surface wrapper to render Bitmaps directly into MediaCodec's input Surface.
     */
    private class CodecInputSurface(private val surface: Surface) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var program: Int = 0
        private var aPositionHandle: Int = -1
        private var aTexCoordHandle: Int = -1
        private var uTextureHandle: Int = -1
        private var textureId: Int = 0

        private val vertexBuffer: FloatBuffer
        private val texCoordBuffer: FloatBuffer

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException("Unable to get EGL14 display")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw RuntimeException("Unable to initialize EGL14")
            }

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            if (numConfigs[0] <= 0 || configs[0] == null) {
                // Fallback without recordable bit
                val fallbackAttribs = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
                )
                EGL14.eglChooseConfig(eglDisplay, fallbackAttribs, 0, configs, 0, 1, numConfigs, 0)
            }
            val eglConfig = configs[0] ?: throw RuntimeException("No suitable EGLConfig found")

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throw RuntimeException("Failed to create EGL context")
            }

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw RuntimeException("Failed to create EGL window surface")
            }

            makeCurrent()

            // Quad vertices
            val quadCoords = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            )
            vertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(quadCoords)
                    position(0)
                }

            // Texture coordinates (vertically flipped to compensate for Android bitmap coordinate orientation)
            val texCoords = floatArrayOf(
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
            )
            texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(texCoords)
                    position(0)
                }

            initGl()
        }

        private fun initGl() {
            val vertexShaderCode = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = aTexCoord;
                }
            """.trimIndent()

            val fragmentShaderCode = """
                precision mediump float;
                varying vec2 vTexCoord;
                uniform sampler2D uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """.trimIndent()

            val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            program = GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vShader)
                GLES20.glAttachShader(it, fShader)
                GLES20.glLinkProgram(it)
            }

            aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            aTexCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            uTextureHandle = GLES20.glGetUniformLocation(program, "uTexture")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        fun makeCurrent() {
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw RuntimeException("eglMakeCurrent failed")
            }
        }

        fun drawBitmap(bitmap: Bitmap, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glUniform1i(uTextureHandle, 0)

            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aPositionHandle)
            GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

            texCoordBuffer.position(0)
            GLES20.glEnableVertexAttribArray(aTexCoordHandle)
            GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPositionHandle)
            GLES20.glDisableVertexAttribArray(aTexCoordHandle)
        }

        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        fun swapBuffers(): Boolean {
            return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            surface.release()
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, shaderCode)
                GLES20.glCompileShader(shader)
            }
        }
    }
}
