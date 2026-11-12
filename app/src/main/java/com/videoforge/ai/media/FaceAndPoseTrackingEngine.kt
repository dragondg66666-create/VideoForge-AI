package com.videoforge.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.videoforge.ai.model.DetectedFaceTrack
import com.videoforge.ai.model.DetectedPersonTrack
import com.videoforge.ai.model.FaceLandmarksSummary
import com.videoforge.ai.model.FrameTrackingResult
import com.videoforge.ai.model.PointF2D
import com.videoforge.ai.model.PoseLandmarkItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object FaceAndPoseTrackingEngine {

    private const val TAG = "FacePoseTrackingEngine"

    data class TrackingAnalysisResult(
        val frameResults: List<FrameTrackingResult>,
        val uniqueFaceIds: Set<Int>,
        val uniquePersonIds: Set<Int>,
        val totalFramesProcessed: Int
    )

    suspend fun analyzeVideoTracks(
        context: Context,
        uri: Uri,
        projectId: String,
        samplingTimestampsMs: List<Long>,
        onProgress: suspend (progress: Float, detail: String) -> Unit
    ): TrackingAnalysisResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val cacheDir = File(context.cacheDir, "tracking/$projectId").apply { mkdirs() }

        // Configure ML Kit Face Detector with streaming tracking and all landmarks
        val faceOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
        val faceDetector = FaceDetection.getClient(faceOptions)

        // Configure ML Kit Pose Detector with stream mode for temporal tracking
        val poseOptions = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        val poseDetector: PoseDetector = PoseDetection.getClient(poseOptions)

        val frameResults = mutableListOf<FrameTrackingResult>()
        val allFaceIds = mutableSetOf<Int>()
        val allPersonIds = mutableSetOf<Int>()

        // Temporal tracker state across consecutive sampled frames
        var nextAutoPersonId = 1
        var previousFramePersons = listOf<DetectedPersonTrack>()

        try {
            retriever.setDataSource(context, uri)
            val totalFrames = samplingTimestampsMs.size.coerceAtLeast(1)

            for (index in samplingTimestampsMs.indices) {
                coroutineContext.ensureActive()

                val timestampMs = samplingTimestampsMs[index]
                val progress = (index.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)
                val timeLabel = SceneDetectionEngine.formatTime(timestampMs)
                onProgress(progress, "Tracking actors at $timeLabel (Frame ${index + 1}/$totalFrames)…")

                // Extract frame bitmap for ML inference (scaled for speed & low memory footprint)
                val frameBitmap = getFrameAt(retriever, timestampMs, targetWidth = 640, targetHeight = 360)
                if (frameBitmap != null) {
                    val frameWidth = frameBitmap.width
                    val frameHeight = frameBitmap.height

                    val inputImage = InputImage.fromBitmap(frameBitmap, 0)

                    // 1. Run ML Kit Face Detection
                    val faces: List<Face> = try {
                        awaitTask(faceDetector.process(inputImage))
                    } catch (e: Exception) {
                        Log.w(TAG, "Face detection error at $timestampMs ms: ${e.message}")
                        emptyList()
                    }

                    // 2. Run ML Kit Pose Detection
                    val pose: Pose? = try {
                        awaitTask(poseDetector.process(inputImage))
                    } catch (e: Exception) {
                        Log.w(TAG, "Pose detection error at $timestampMs ms: ${e.message}")
                        null
                    }

                    // Convert ML Kit faces to our data models
                    val detectedFaceTracks = faces.map { face ->
                        val trackingId = face.trackingId
                        if (trackingId != null) {
                            allFaceIds.add(trackingId)
                        }

                        val landmarksSummary = FaceLandmarksSummary(
                            leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { PointF2D(it.x, it.y) },
                            rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { PointF2D(it.x, it.y) },
                            noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position?.let { PointF2D(it.x, it.y) },
                            mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let { PointF2D(it.x, it.y) },
                            mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let { PointF2D(it.x, it.y) },
                            mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position?.let { PointF2D(it.x, it.y) }
                        )

                        val box = face.boundingBox
                        DetectedFaceTrack(
                            trackingId = trackingId,
                            boxLeft = box.left.toFloat(),
                            boxTop = box.top.toFloat(),
                            boxRight = box.right.toFloat(),
                            boxBottom = box.bottom.toFloat(),
                            headEulerAngleX = face.headEulerAngleX,
                            headEulerAngleY = face.headEulerAngleY,
                            headEulerAngleZ = face.headEulerAngleZ,
                            landmarks = landmarksSummary,
                            confidence = 0.95f
                        )
                    }

                    // Convert ML Kit pose landmarks to our models
                    val detectedPersonsInFrame = mutableListOf<DetectedPersonTrack>()
                    if (pose != null) {
                        val allLandmarks = pose.allPoseLandmarks
                        val relevantLandmarks = allLandmarks.filter { lm ->
                            isRelevantBodyLandmark(lm.landmarkType) && lm.inFrameLikelihood >= 0.25f
                        }

                        if (relevantLandmarks.isNotEmpty()) {
                            var minX = Float.MAX_VALUE
                            var minY = Float.MAX_VALUE
                            var maxX = Float.MIN_VALUE
                            var maxY = Float.MIN_VALUE

                            val landmarkItems = relevantLandmarks.map { lm ->
                                val pos = lm.position
                                if (pos.x < minX) minX = pos.x
                                if (pos.y < minY) minY = pos.y
                                if (pos.x > maxX) maxX = pos.x
                                if (pos.y > maxY) maxY = pos.y

                                PoseLandmarkItem(
                                    landmarkType = getLandmarkTypeName(lm.landmarkType),
                                    position = PointF2D(pos.x, pos.y),
                                    inFrameLikelihood = lm.inFrameLikelihood
                                )
                            }

                            // Compute bounding box with padding
                            val padX = (maxX - minX) * 0.1f
                            val padY = (maxY - minY) * 0.1f
                            val pBoxLeft = (minX - padX).coerceAtLeast(0f)
                            val pBoxTop = (minY - padY).coerceAtLeast(0f)
                            val pBoxRight = (maxX + padX).coerceAtMost(frameWidth.toFloat())
                            val pBoxBottom = (maxY + padY).coerceAtMost(frameHeight.toFloat())

                            // Associate face if bounding box overlaps with head region
                            val associatedFace = detectedFaceTracks.firstOrNull { f ->
                                val overlapX = max(0f, min(f.boxRight, pBoxRight) - max(f.boxLeft, pBoxLeft))
                                val overlapY = max(0f, min(f.boxBottom, pBoxBottom) - max(f.boxTop, pBoxTop))
                                overlapX * overlapY > 0f
                            }

                            // Sampled Temporal Association: match with previous frame person tracks
                            val assignedPersonId = associatePersonId(
                                currentBox = RectF(pBoxLeft, pBoxTop, pBoxRight, pBoxBottom),
                                previousPersons = previousFramePersons
                            ) ?: (nextAutoPersonId++)

                            allPersonIds.add(assignedPersonId)

                            val personTrack = DetectedPersonTrack(
                                personId = assignedPersonId,
                                boxLeft = pBoxLeft,
                                boxTop = pBoxTop,
                                boxRight = pBoxRight,
                                boxBottom = pBoxBottom,
                                landmarks = landmarkItems,
                                associatedFace = associatedFace,
                                confidence = (landmarkItems.map { it.inFrameLikelihood }.average().toFloat()).coerceIn(0.5f, 0.99f)
                            )
                            detectedPersonsInFrame.add(personTrack)
                        }
                    }

                    // Save sample keyframe thumbnail
                    val thumbFile = File(cacheDir, "track_frame_${timestampMs}.jpg")
                    saveBitmapToJpeg(frameBitmap, thumbFile)
                    frameBitmap.recycle()

                    previousFramePersons = detectedPersonsInFrame

                    frameResults.add(
                        FrameTrackingResult(
                            timestampMs = timestampMs,
                            formattedTime = SceneDetectionEngine.formatTime(timestampMs),
                            frameWidth = frameWidth,
                            frameHeight = frameHeight,
                            thumbnailPath = thumbFile.absolutePath,
                            detectedFaces = detectedFaceTracks,
                            detectedPersons = detectedPersonsInFrame
                        )
                    )
                }
            }

            onProgress(1.0f, "Inference complete: ${allFaceIds.size} faces and ${allPersonIds.size} person tracks detected.")

            TrackingAnalysisResult(
                frameResults = frameResults,
                uniqueFaceIds = allFaceIds,
                uniquePersonIds = allPersonIds,
                totalFramesProcessed = frameResults.size
            )
        } finally {
            try {
                faceDetector.close()
            } catch (ignored: Exception) {}
            try {
                poseDetector.close()
            } catch (ignored: Exception) {}
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun isRelevantBodyLandmark(type: Int): Boolean {
        return when (type) {
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_EYE, PoseLandmark.RIGHT_EYE,
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE -> true
            else -> false
        }
    }

    private fun getLandmarkTypeName(type: Int): String {
        return when (type) {
            PoseLandmark.NOSE -> "Nose"
            PoseLandmark.LEFT_EYE -> "Left Eye"
            PoseLandmark.RIGHT_EYE -> "Right Eye"
            PoseLandmark.LEFT_SHOULDER -> "Left Shoulder"
            PoseLandmark.RIGHT_SHOULDER -> "Right Shoulder"
            PoseLandmark.LEFT_ELBOW -> "Left Elbow"
            PoseLandmark.RIGHT_ELBOW -> "Right Elbow"
            PoseLandmark.LEFT_WRIST -> "Left Wrist"
            PoseLandmark.RIGHT_WRIST -> "Right Wrist"
            PoseLandmark.LEFT_HIP -> "Left Hip"
            PoseLandmark.RIGHT_HIP -> "Right Hip"
            PoseLandmark.LEFT_KNEE -> "Left Knee"
            PoseLandmark.RIGHT_KNEE -> "Right Knee"
            PoseLandmark.LEFT_ANKLE -> "Left Ankle"
            PoseLandmark.RIGHT_ANKLE -> "Right Ankle"
            else -> "Body Point $type"
        }
    }

    /**
     * Associates detections across consecutive sampled frames using bounding box Intersection-over-Union (IoU)
     */
    private fun associatePersonId(
        currentBox: RectF,
        previousPersons: List<DetectedPersonTrack>
    ): Int? {
        var bestId: Int? = null
        var maxIoU = 0.20f // Association threshold across sampled intervals

        for (prev in previousPersons) {
            val prevBox = RectF(prev.boxLeft, prev.boxTop, prev.boxRight, prev.boxBottom)
            val iou = calculateIoU(currentBox, prevBox)
            if (iou > maxIoU) {
                maxIoU = iou
                bestId = prev.personId
            }
        }
        return bestId
    }

    private fun calculateIoU(boxA: RectF, boxB: RectF): Float {
        val interLeft = max(boxA.left, boxB.left)
        val interTop = max(boxA.top, boxB.top)
        val interRight = min(boxA.right, boxB.right)
        val interBottom = min(boxA.bottom, boxB.bottom)

        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val areaA = (boxA.right - boxA.left) * (boxA.bottom - boxA.top)
        val areaB = (boxB.right - boxB.left) * (boxB.bottom - boxB.top)

        val unionArea = areaA + areaB - interArea
        return if (unionArea > 0f) interArea / unionArea else 0f
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
            Log.w(TAG, "Failed to get frame for tracking at $timestampMs ms: ${e.message}")
            null
        }
    }

    private fun saveBitmapToJpeg(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save tracking keyframe to ${file.absolutePath}", e)
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }.addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }.addOnCanceledListener {
            continuation.cancel()
        }
    }
}
