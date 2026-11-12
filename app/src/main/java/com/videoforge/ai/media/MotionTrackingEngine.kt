package com.videoforge.ai.media

import android.content.Context
import android.graphics.Bitmap
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
import com.videoforge.ai.model.LandmarkTrajectory
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.model.PointF2D
import com.videoforge.ai.model.PoseLandmarkItem
import com.videoforge.ai.model.TrackFrame
import com.videoforge.ai.model.TrackingSummary
import com.videoforge.ai.model.TrajectoryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Phase 2.4: Real Video Motion Tracking & Temporal Identity Association Engine
 *
 * Implements real temporal motion tracking across sampled video frames:
 * - Multi-criteria Hungarian/Greedy association: IoU, Center-Point Distance, Landmark Similarity & Face matching.
 * - Dynamic velocity calculation (px/s) & motion direction classification.
 * - Occlusion handling & short-term linear motion interpolation.
 * - Scale change (approaching/receding) and head movement Euler delta measurements.
 * - Landmark trajectories (Nose, Wrists, Ankles) preserved for motion curve visualization.
 * - Explicit local track attribution without claiming biometric persistent identity.
 */
object MotionTrackingEngine {

    private const val TAG = "MotionTrackingEngine"
    private const val MAX_MISSED_FRAMES_BEFORE_TERMINATION = 2
    private const val ASSOCIATION_AFFINITY_THRESHOLD = 0.28f

    data class TrackingAnalysisResult(
        val frameResults: List<FrameTrackingResult>,
        val trackingSummary: TrackingSummary,
        val uniqueFaceIds: Set<Int>,
        val uniquePersonIds: Set<Int>,
        val totalFramesProcessed: Int
    )

    private class ActiveTrackState(
        val trackId: Int,
        firstFrame: TrackFrame,
        val frameWidth: Int,
        val frameHeight: Int
    ) {
        val frames = mutableListOf<TrackFrame>().apply { add(firstFrame) }
        var consecutiveMissed = 0
        var interruptionCount = 0
        var velocityX = 0f // px/s
        var velocityY = 0f // px/s
        val trajectories = mutableMapOf<String, MutableList<TrajectoryPoint>>()

        init {
            updateTrajectories(firstFrame)
        }

        val lastFrame: TrackFrame get() = frames.last()

        fun updateTrajectories(frame: TrackFrame) {
            frame.landmarks.forEach { lm ->
                val list = trajectories.getOrPut(lm.landmarkType) { mutableListOf() }
                list.add(
                    TrajectoryPoint(
                        timestampMs = frame.timestampMs,
                        formattedTime = frame.formattedTime,
                        x = lm.position.x,
                        y = lm.position.y,
                        confidence = lm.inFrameLikelihood
                    )
                )
            }
        }
    }

    suspend fun analyzeMotionTracks(
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

        // Configure ML Kit Pose Detector in stream mode for video
        val poseOptions = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        val poseDetector: PoseDetector = PoseDetection.getClient(poseOptions)

        val frameResults = mutableListOf<FrameTrackingResult>()
        val allFaceIds = mutableSetOf<Int>()
        val allPersonIds = mutableSetOf<Int>()

        var nextTrackId = 1
        val activeTracks = mutableListOf<ActiveTrackState>()
        val finishedTracks = mutableListOf<ActiveTrackState>()

        try {
            retriever.setDataSource(context, uri)
            val totalFrames = samplingTimestampsMs.size.coerceAtLeast(1)

            for (index in samplingTimestampsMs.indices) {
                coroutineContext.ensureActive()

                val timestampMs = samplingTimestampsMs[index]
                val progress = (index.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)
                val timeLabel = SceneDetectionEngine.formatTime(timestampMs)
                onProgress(progress, "Motion tracking actors at $timeLabel (Frame ${index + 1}/$totalFrames)…")

                // Extract scaled frame bitmap for fast ML inference & low memory footprint
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

                    // Map detected faces
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

                    // Extract body landmark items from Pose
                    val currentDetections = mutableListOf<RawPerformerDetection>()
                    if (pose != null) {
                        val relevantLandmarks = pose.allPoseLandmarks.filter { lm ->
                            isRelevantBodyLandmark(lm.landmarkType) && lm.inFrameLikelihood >= 0.22f
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

                            val padX = (maxX - minX) * 0.12f
                            val padY = (maxY - minY) * 0.12f
                            val pBoxLeft = (minX - padX).coerceAtLeast(0f)
                            val pBoxTop = (minY - padY).coerceAtLeast(0f)
                            val pBoxRight = (maxX + padX).coerceAtMost(frameWidth.toFloat())
                            val pBoxBottom = (maxY + padY).coerceAtMost(frameHeight.toFloat())

                            // Associate face if overlapping
                            val associatedFace = detectedFaceTracks.firstOrNull { f ->
                                val overlapX = max(0f, min(f.boxRight, pBoxRight) - max(f.boxLeft, pBoxLeft))
                                val overlapY = max(0f, min(f.boxBottom, pBoxBottom) - max(f.boxTop, pBoxTop))
                                overlapX * overlapY > 0f
                            }

                            currentDetections.add(
                                RawPerformerDetection(
                                    box = RectF(pBoxLeft, pBoxTop, pBoxRight, pBoxBottom),
                                    landmarks = landmarkItems,
                                    associatedFace = associatedFace,
                                    confidence = landmarkItems.map { it.inFrameLikelihood }.average().toFloat().coerceIn(0.5f, 0.99f)
                                )
                            )
                        }
                    }

                    // Also check for isolated detected faces without body pose detection
                    detectedFaceTracks.forEach { face ->
                        val alreadyCovered = currentDetections.any { det ->
                            val overlapX = max(0f, min(face.boxRight, det.box.right) - max(face.boxLeft, det.box.left))
                            val overlapY = max(0f, min(face.boxBottom, det.box.bottom) - max(face.boxTop, det.box.top))
                            overlapX * overlapY > 0.4f * (face.boxRight - face.boxLeft) * (face.boxBottom - face.boxTop)
                        }
                        if (!alreadyCovered) {
                            val fWidth = face.boxRight - face.boxLeft
                            val fHeight = face.boxBottom - face.boxTop
                            val approxBodyBox = RectF(
                                (face.boxLeft - fWidth * 0.4f).coerceAtLeast(0f),
                                face.boxTop,
                                (face.boxRight + fWidth * 0.4f).coerceAtMost(frameWidth.toFloat()),
                                (face.boxBottom + fHeight * 2.8f).coerceAtMost(frameHeight.toFloat())
                            )
                            currentDetections.add(
                                RawPerformerDetection(
                                    box = approxBodyBox,
                                    landmarks = emptyList(),
                                    associatedFace = face,
                                    confidence = 0.88f
                                )
                            )
                        }
                    }

                    // Temporal association between activeTracks and currentDetections
                    val framePersons = associateAndAdvanceTracks(
                        timestampMs = timestampMs,
                        formattedTime = timeLabel,
                        frameWidth = frameWidth,
                        frameHeight = frameHeight,
                        detections = currentDetections,
                        activeTracks = activeTracks,
                        finishedTracks = finishedTracks,
                        nextTrackIdProvider = { nextTrackId++ }
                    )

                    framePersons.forEach { allPersonIds.add(it.personId) }

                    // Save sample keyframe thumbnail
                    val thumbFile = File(cacheDir, "track_frame_${timestampMs}.jpg")
                    saveBitmapToJpeg(frameBitmap, thumbFile)
                    frameBitmap.recycle()

                    frameResults.add(
                        FrameTrackingResult(
                            timestampMs = timestampMs,
                            formattedTime = timeLabel,
                            frameWidth = frameWidth,
                            frameHeight = frameHeight,
                            thumbnailPath = thumbFile.absolutePath,
                            detectedFaces = detectedFaceTracks,
                            detectedPersons = framePersons
                        )
                    )
                }
            }

            // Finalize remaining active tracks
            finishedTracks.addAll(activeTracks)
            activeTracks.clear()

            // Compute complete PersonTrack metrics & trajectories
            val personTracks = finishedTracks
                .filter { it.frames.size >= 1 }
                .map { state -> compilePersonTrack(state) }
                .sortedByDescending { it.totalFramesTracked }

            val trackingSummary = compileTrackingSummary(personTracks)

            onProgress(1.0f, "Temporal tracking complete: ${personTracks.size} tracks (${trackingSummary.activeTracksCount} continuous) identified.")

            TrackingAnalysisResult(
                frameResults = frameResults,
                trackingSummary = trackingSummary,
                uniqueFaceIds = allFaceIds,
                uniquePersonIds = allPersonIds,
                totalFramesProcessed = frameResults.size
            )
        } finally {
            try { faceDetector.close() } catch (ignored: Exception) {}
            try { poseDetector.close() } catch (ignored: Exception) {}
            try { retriever.release() } catch (ignored: Exception) {}
        }
    }

    private data class RawPerformerDetection(
        val box: RectF,
        val landmarks: List<PoseLandmarkItem>,
        val associatedFace: DetectedFaceTrack?,
        val confidence: Float
    )

    private fun associateAndAdvanceTracks(
        timestampMs: Long,
        formattedTime: String,
        frameWidth: Int,
        frameHeight: Int,
        detections: List<RawPerformerDetection>,
        activeTracks: MutableList<ActiveTrackState>,
        finishedTracks: MutableList<ActiveTrackState>,
        nextTrackIdProvider: () -> Int
    ): List<DetectedPersonTrack> {
        val diag = sqrt((frameWidth * frameWidth + frameHeight * frameHeight).toDouble()).toFloat().coerceAtLeast(1f)
        val matchedTracks = mutableSetOf<ActiveTrackState>()
        val matchedDetections = mutableSetOf<Int>()

        // 1. Compute pairwise affinities
        data class MatchPair(
            val track: ActiveTrackState,
            val detIndex: Int,
            val affinity: Float
        )
        val pairs = mutableListOf<MatchPair>()

        for (track in activeTracks) {
            val last = track.lastFrame
            val dtSec = ((timestampMs - last.timestampMs) / 1000f).coerceAtLeast(0.001f)

            // Predict next box center using velocity
            val predCenterX = (last.centerX + track.velocityX * dtSec).coerceIn(0f, frameWidth.toFloat())
            val predCenterY = (last.centerY + track.velocityY * dtSec).coerceIn(0f, frameHeight.toFloat())
            val halfW = (last.boxRight - last.boxLeft) / 2f
            val halfH = (last.boxBottom - last.boxTop) / 2f
            val predictedBox = RectF(predCenterX - halfW, predCenterY - halfH, predCenterX + halfW, predCenterY + halfH)

            detections.forEachIndexed { detIndex, det ->
                val detCenterX = (det.box.left + det.box.right) / 2f
                val detCenterY = (det.box.top + det.box.bottom) / 2f

                // Spatial distance score
                val dist = sqrt((detCenterX - predCenterX) * (detCenterX - predCenterX) + (detCenterY - predCenterY) * (detCenterY - predCenterY))
                val spatialScore = (1f - (dist / (diag * 0.45f))).coerceIn(0f, 1f)

                // IoU overlap with predicted and last boxes
                val iouPred = calculateIoU(det.box, predictedBox)
                val iouLast = calculateIoU(det.box, RectF(last.boxLeft, last.boxTop, last.boxRight, last.boxBottom))
                val iou = max(iouPred, iouLast)

                // Face matching bonus if ML Kit tracking IDs match
                var faceBonus = 0f
                if (det.associatedFace?.trackingId != null && last.associatedFace?.trackingId != null) {
                    if (det.associatedFace.trackingId == last.associatedFace.trackingId) {
                        faceBonus = 0.35f
                    }
                }

                // Pose anchor similarity (shoulders/hips)
                val landmarkSim = calculateLandmarkSimilarity(det.landmarks, last.landmarks, diag)

                val affinity = (0.40f * iou) + (0.35f * spatialScore) + (0.15f * landmarkSim) + faceBonus
                if (affinity >= ASSOCIATION_AFFINITY_THRESHOLD) {
                    pairs.add(MatchPair(track, detIndex, affinity))
                }
            }
        }

        // 2. Greedy bipartite matching
        pairs.sortByDescending { it.affinity }
        val outputPersons = mutableListOf<DetectedPersonTrack>()

        for (pair in pairs) {
            if (pair.track in matchedTracks || pair.detIndex in matchedDetections) continue

            matchedTracks.add(pair.track)
            matchedDetections.add(pair.detIndex)

            val track = pair.track
            val det = detections[pair.detIndex]
            val last = track.lastFrame
            val dtSec = ((timestampMs - last.timestampMs) / 1000f).coerceAtLeast(0.001f)

            val curCenterX = (det.box.left + det.box.right) / 2f
            val curCenterY = (det.box.top + det.box.bottom) / 2f

            // Calculate instantaneous & smoothed velocity
            val instVx = (curCenterX - last.centerX) / dtSec
            val instVy = (curCenterY - last.centerY) / dtSec
            track.velocityX = (0.6f * instVx) + (0.4f * track.velocityX)
            track.velocityY = (0.6f * instVy) + (0.4f * track.velocityY)
            val speed = sqrt(instVx * instVx + instVy * instVy)

            // Scale change from previous bounding box
            val prevArea = (last.boxRight - last.boxLeft) * (last.boxBottom - last.boxTop)
            val curArea = (det.box.right - det.box.left) * (det.box.bottom - det.box.top)
            val scaleChange = if (prevArea > 0f) ((curArea - prevArea) / prevArea) * 100f else 0f

            val trackFrame = TrackFrame(
                timestampMs = timestampMs,
                formattedTime = formattedTime,
                boxLeft = det.box.left,
                boxTop = det.box.top,
                boxRight = det.box.right,
                boxBottom = det.box.bottom,
                centerX = curCenterX,
                centerY = curCenterY,
                speedPxPerSec = speed,
                instantVelocityX = instVx,
                instantVelocityY = instVy,
                scaleChangeFromPrev = scaleChange,
                headEulerY = det.associatedFace?.headEulerAngleY ?: last.headEulerY,
                headEulerX = det.associatedFace?.headEulerAngleX ?: last.headEulerX,
                headEulerZ = det.associatedFace?.headEulerAngleZ ?: last.headEulerZ,
                associatedFace = det.associatedFace ?: last.associatedFace,
                landmarks = det.landmarks,
                confidence = det.confidence,
                isInterpolated = false,
                status = "DETECTED"
            )

            track.frames.add(trackFrame)
            track.updateTrajectories(trackFrame)
            track.consecutiveMissed = 0

            outputPersons.add(
                DetectedPersonTrack(
                    personId = track.trackId,
                    boxLeft = det.box.left,
                    boxTop = det.box.top,
                    boxRight = det.box.right,
                    boxBottom = det.box.bottom,
                    landmarks = det.landmarks,
                    associatedFace = det.associatedFace,
                    confidence = det.confidence,
                    status = "DETECTED",
                    speedPxPerSec = speed,
                    instantVelocityX = instVx,
                    instantVelocityY = instVy
                )
            )
        }

        // 3. Handle unmatched active tracks (occlusion / missed detection)
        val iterator = activeTracks.iterator()
        while (iterator.hasNext()) {
            val track = iterator.next()
            if (track !in matchedTracks) {
                track.consecutiveMissed++
                track.interruptionCount++

                if (track.consecutiveMissed <= MAX_MISSED_FRAMES_BEFORE_TERMINATION) {
                    // Interpolate position using linear motion prediction
                    val last = track.lastFrame
                    val dtSec = ((timestampMs - last.timestampMs) / 1000f).coerceAtLeast(0.001f)
                    val newCenterX = (last.centerX + track.velocityX * dtSec).coerceIn(0f, frameWidth.toFloat())
                    val newCenterY = (last.centerY + track.velocityY * dtSec).coerceIn(0f, frameHeight.toFloat())
                    val halfW = (last.boxRight - last.boxLeft) / 2f
                    val halfH = (last.boxBottom - last.boxTop) / 2f
                    val interpLeft = (newCenterX - halfW).coerceAtLeast(0f)
                    val interpTop = (newCenterY - halfH).coerceAtLeast(0f)
                    val interpRight = (newCenterX + halfW).coerceAtMost(frameWidth.toFloat())
                    val interpBottom = (newCenterY + halfH).coerceAtMost(frameHeight.toFloat())

                    val interpFrame = TrackFrame(
                        timestampMs = timestampMs,
                        formattedTime = formattedTime,
                        boxLeft = interpLeft,
                        boxTop = interpTop,
                        boxRight = interpRight,
                        boxBottom = interpBottom,
                        centerX = newCenterX,
                        centerY = newCenterY,
                        speedPxPerSec = sqrt(track.velocityX * track.velocityX + track.velocityY * track.velocityY),
                        instantVelocityX = track.velocityX,
                        instantVelocityY = track.velocityY,
                        scaleChangeFromPrev = 0f,
                        headEulerY = last.headEulerY,
                        headEulerX = last.headEulerX,
                        headEulerZ = last.headEulerZ,
                        associatedFace = last.associatedFace,
                        landmarks = last.landmarks,
                        confidence = (last.confidence * 0.75f).coerceAtLeast(0.4f),
                        isInterpolated = true,
                        status = "INTERPOLATED_OCCLUDED"
                    )
                    track.frames.add(interpFrame)
                    track.updateTrajectories(interpFrame)

                    outputPersons.add(
                        DetectedPersonTrack(
                            personId = track.trackId,
                            boxLeft = interpLeft,
                            boxTop = interpTop,
                            boxRight = interpRight,
                            boxBottom = interpBottom,
                            landmarks = last.landmarks,
                            associatedFace = last.associatedFace,
                            confidence = interpFrame.confidence,
                            status = "INTERPOLATED_OCCLUDED",
                            speedPxPerSec = interpFrame.speedPxPerSec,
                            instantVelocityX = track.velocityX,
                            instantVelocityY = track.velocityY
                        )
                    )
                } else {
                    // Track exceeded maximum missed intervals -> terminate
                    finishedTracks.add(track)
                    iterator.remove()
                }
            }
        }

        // 4. Handle unmatched detections -> initialize new tracks
        detections.forEachIndexed { detIndex, det ->
            if (detIndex !in matchedDetections) {
                val newId = nextTrackIdProvider()
                val curCenterX = (det.box.left + det.box.right) / 2f
                val curCenterY = (det.box.top + det.box.bottom) / 2f

                val firstFrame = TrackFrame(
                    timestampMs = timestampMs,
                    formattedTime = formattedTime,
                    boxLeft = det.box.left,
                    boxTop = det.box.top,
                    boxRight = det.box.right,
                    boxBottom = det.box.bottom,
                    centerX = curCenterX,
                    centerY = curCenterY,
                    speedPxPerSec = 0f,
                    instantVelocityX = 0f,
                    instantVelocityY = 0f,
                    scaleChangeFromPrev = 0f,
                    headEulerY = det.associatedFace?.headEulerAngleY ?: 0f,
                    headEulerX = det.associatedFace?.headEulerAngleX ?: 0f,
                    headEulerZ = det.associatedFace?.headEulerAngleZ ?: 0f,
                    associatedFace = det.associatedFace,
                    landmarks = det.landmarks,
                    confidence = det.confidence,
                    isInterpolated = false,
                    status = "DETECTED"
                )

                val newTrack = ActiveTrackState(
                    trackId = newId,
                    firstFrame = firstFrame,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight
                )
                activeTracks.add(newTrack)

                outputPersons.add(
                    DetectedPersonTrack(
                        personId = newId,
                        boxLeft = det.box.left,
                        boxTop = det.box.top,
                        boxRight = det.box.right,
                        boxBottom = det.box.bottom,
                        landmarks = det.landmarks,
                        associatedFace = det.associatedFace,
                        confidence = det.confidence,
                        status = "DETECTED",
                        speedPxPerSec = 0f,
                        instantVelocityX = 0f,
                        instantVelocityY = 0f
                    )
                )
            }
        }

        return outputPersons
    }

    private fun compilePersonTrack(state: ActiveTrackState): PersonTrack {
        val nonInterp = state.frames.filter { !it.isInterpolated }
        val allFrames = state.frames

        val firstFrame = allFrames.first()
        val lastFrame = allFrames.last()
        val durationMs = (lastFrame.timestampMs - firstFrame.timestampMs).coerceAtLeast(0L)

        val netDx = lastFrame.centerX - firstFrame.centerX
        val netDy = lastFrame.centerY - firstFrame.centerY
        val netDistance = sqrt(netDx * netDx + netDy * netDy)

        val avgSpeed = if (allFrames.size > 1) {
            allFrames.map { it.speedPxPerSec }.average().toFloat()
        } else 0f

        val maxSpeed = allFrames.maxOfOrNull { it.speedPxPerSec } ?: 0f

        val motionDir = classifyMotionDirection(netDx, netDy, avgSpeed)

        // Scale change across duration
        val firstArea = (firstFrame.boxRight - firstFrame.boxLeft) * (firstFrame.boxBottom - firstFrame.boxTop)
        val lastArea = (lastFrame.boxRight - lastFrame.boxLeft) * (lastFrame.boxBottom - lastFrame.boxTop)
        val overallScaleChange = if (firstArea > 0f) ((lastArea - firstArea) / firstArea) * 100f else 0f

        // Landmark trajectories with computed total path length
        val trajectoryList = state.trajectories.map { (name, points) ->
            var pathLen = 0f
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                pathLen += sqrt(dx * dx + dy * dy)
            }
            LandmarkTrajectory(
                landmarkName = name,
                points = points,
                totalPathLengthPx = pathLen
            )
        }

        val avgConfidence = if (nonInterp.isNotEmpty()) {
            nonInterp.map { it.confidence }.average().toFloat()
        } else 0.85f

        return PersonTrack(
            trackId = state.trackId,
            label = "Track #${state.trackId} (Spatial/Motion Associated)",
            firstSeenMs = firstFrame.timestampMs,
            lastSeenMs = lastFrame.timestampMs,
            durationMs = durationMs,
            totalFramesTracked = allFrames.size,
            averageConfidence = avgConfidence,
            motionDirection = motionDir,
            averageSpeedPxPerSec = avgSpeed,
            maxSpeedPxPerSec = maxSpeed,
            netDisplacementX = netDx,
            netDisplacementY = netDy,
            scaleChangePercent = overallScaleChange,
            interruptionCount = state.interruptionCount,
            frames = allFrames,
            trajectories = trajectoryList
        )
    }

    private fun compileTrackingSummary(tracks: List<PersonTrack>): TrackingSummary {
        val total = tracks.size
        val continuousTracks = tracks.filter { it.totalFramesTracked >= 2 }
        val activeCount = continuousTracks.size

        val avgDuration = if (continuousTracks.isNotEmpty()) {
            continuousTracks.map { it.durationMs }.average().toLong()
        } else 0L

        val maxDuration = tracks.maxOfOrNull { it.durationMs } ?: 0L
        val totalInterruptions = tracks.sumOf { it.interruptionCount }

        // Find most common motion direction
        val directionCounts = continuousTracks.groupingBy { it.motionDirection.split("(").first().trim() }.eachCount()
        val predominant = directionCounts.maxByOrNull { it.value }?.key ?: "Stationary"

        return TrackingSummary(
            totalTracks = total,
            activeTracksCount = activeCount,
            averageTrackDurationMs = avgDuration,
            maxTrackDurationMs = maxDuration,
            totalInterruptions = totalInterruptions,
            predominantMotion = predominant,
            tracks = tracks
        )
    }

    private fun classifyMotionDirection(dx: Float, dy: Float, avgSpeed: Float): String {
        val netDist = sqrt(dx * dx + dy * dy)
        if (netDist < 18f && avgSpeed < 25f) {
            return "Stationary / In-Place"
        }

        val angleRad = atan2(-dy.toDouble(), dx.toDouble()) // standard Cartesian angle (Y is inverted in image coordinates)
        val angleDeg = Math.toDegrees(angleRad)

        val dirLabel = when {
            angleDeg in -35.0..35.0 -> "Moving Right"
            angleDeg in 35.0..70.0 -> "Diagonal Up-Right"
            angleDeg in 70.0..110.0 -> "Ascending / Moving Up"
            angleDeg in 110.0..145.0 -> "Diagonal Up-Left"
            angleDeg in 145.0..180.0 || angleDeg in -180.0..-145.0 -> "Moving Left"
            angleDeg in -145.0..-110.0 -> "Diagonal Down-Left"
            angleDeg in -110.0..-70.0 -> "Descending / Moving Down"
            else -> "Diagonal Down-Right"
        }

        return String.format(Locale.US, "%s (%.0f px/s)", dirLabel, avgSpeed)
    }

    private fun calculateLandmarkSimilarity(
        landmarksA: List<PoseLandmarkItem>,
        landmarksB: List<PoseLandmarkItem>,
        diag: Float
    ): Float {
        if (landmarksA.isEmpty() || landmarksB.isEmpty()) return 0.5f

        val mapB = landmarksB.associateBy { it.landmarkType }
        val commonNames = listOf("Left Shoulder", "Right Shoulder", "Left Hip", "Right Hip", "Nose")
        var matched = 0
        var totalDist = 0f

        for (name in commonNames) {
            val a = landmarksA.find { it.landmarkType == name }
            val b = mapB[name]
            if (a != null && b != null) {
                val dx = a.position.x - b.position.x
                val dy = a.position.y - b.position.y
                totalDist += sqrt(dx * dx + dy * dy)
                matched++
            }
        }

        if (matched == 0) return 0.5f
        val avgDist = totalDist / matched
        return (1f - (avgDist / (diag * 0.3f))).coerceIn(0f, 1f)
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
            else -> "Joint $type"
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
