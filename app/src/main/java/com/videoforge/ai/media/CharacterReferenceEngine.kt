package com.videoforge.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.videoforge.ai.model.CharacterImage
import com.videoforge.ai.model.FaceLandmarksSummary
import com.videoforge.ai.model.PointF2D
import com.videoforge.ai.model.PoseLandmarkItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object CharacterReferenceEngine {

    private const val TAG = "CharReferenceEngine"
    private const val MAX_DIMENSION = 2048 // Maximum pixel dimension for ML inference & crop

    suspend fun processReference(
        context: Context,
        image: CharacterImage
    ): CharacterImage = withContext(Dispatchers.IO) {
        val uriStr = image.uriString
        if (uriStr == null) {
            // Demo image or missing URI
            return@withContext image.copy(
                processingStatus = "processed",
                faceDetectionStatus = "detected",
                detectedOrientation = image.angleTag,
                errorMessage = null
            )
        }

        val uri = Uri.parse(uriStr)
        val faceCacheDir = File(context.cacheDir, "character_faces").apply { mkdirs() }

        // Configure ML Kit Face Detector (Accurate mode for reference photos)
        val faceOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        val faceDetector = FaceDetection.getClient(faceOptions)

        // Configure ML Kit Pose Detector (Single image mode for reference portraits)
        val poseOptions = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        val poseDetector = PoseDetection.getClient(poseOptions)

        var loadedBitmap: Bitmap? = null
        try {
            coroutineContext.ensureActive()

            // 1. Decode original image safely with EXIF rotation and downsampling if necessary
            loadedBitmap = decodeSampledBitmapFromUri(context, uri, MAX_DIMENSION)
            if (loadedBitmap == null) {
                return@withContext image.copy(
                    processingStatus = "error",
                    errorMessage = "Failed to decode image from storage"
                )
            }

            val imgWidth = loadedBitmap.width
            val imgHeight = loadedBitmap.height
            val inputImage = InputImage.fromBitmap(loadedBitmap, 0)

            // 2. Run Face Detection
            coroutineContext.ensureActive()
            val detectedFaces: List<Face> = try {
                awaitTask(faceDetector.process(inputImage))
            } catch (e: Exception) {
                Log.w(TAG, "Face detection failure on ${image.filename}: ${e.message}")
                emptyList()
            }

            // 3. Run Body Pose Detection
            coroutineContext.ensureActive()
            val detectedPose: Pose? = try {
                awaitTask(poseDetector.process(inputImage))
            } catch (e: Exception) {
                Log.w(TAG, "Pose detection failure on ${image.filename}: ${e.message}")
                null
            }

            // --- Process Face Data ---
            val faceStatus: String
            var faceBoxLeft = 0f
            var faceBoxTop = 0f
            var faceBoxRight = 0f
            var faceBoxBottom = 0f
            var detectedOrientation = "Unknown"
            var eulerX = 0f
            var eulerY = 0f
            var eulerZ = 0f
            var landmarksSummary: FaceLandmarksSummary? = null
            var faceConfidence = 0f
            var croppedFacePath: String? = null

            if (detectedFaces.isNotEmpty()) {
                faceStatus = "detected"
                // Pick largest face as primary character face
                val primaryFace = detectedFaces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
                val box = primaryFace.boundingBox

                faceBoxLeft = box.left.toFloat().coerceAtLeast(0f)
                faceBoxTop = box.top.toFloat().coerceAtLeast(0f)
                faceBoxRight = box.right.toFloat().coerceAtMost(imgWidth.toFloat())
                faceBoxBottom = box.bottom.toFloat().coerceAtMost(imgHeight.toFloat())

                eulerX = primaryFace.headEulerAngleX
                eulerY = primaryFace.headEulerAngleY
                eulerZ = primaryFace.headEulerAngleZ
                faceConfidence = 0.98f

                // Estimate orientation from actual Euler Yaw (Y)
                detectedOrientation = estimateFaceOrientation(eulerY, eulerX)

                // Extract landmarks
                landmarksSummary = FaceLandmarksSummary(
                    leftEye = primaryFace.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { PointF2D(it.x, it.y) },
                    rightEye = primaryFace.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { PointF2D(it.x, it.y) },
                    noseBase = primaryFace.getLandmark(FaceLandmark.NOSE_BASE)?.position?.let { PointF2D(it.x, it.y) },
                    mouthLeft = primaryFace.getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let { PointF2D(it.x, it.y) },
                    mouthRight = primaryFace.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let { PointF2D(it.x, it.y) },
                    mouthBottom = primaryFace.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position?.let { PointF2D(it.x, it.y) }
                )

                // Create normalized cropped face preview with 25% padding
                val cropFile = File(faceCacheDir, "face_crop_${image.imageId}.jpg")
                val croppedBmp = cropFaceWithPadding(loadedBitmap, box, 0.25f)
                if (croppedBmp != null) {
                    saveBitmapToJpeg(croppedBmp, cropFile)
                    croppedFacePath = cropFile.absolutePath
                    if (croppedBmp != loadedBitmap) {
                        croppedBmp.recycle()
                    }
                }
            } else {
                faceStatus = "no_face"
                detectedOrientation = "No Face Detected"
            }

            // --- Process Body Pose Data ---
            var bodyStatus = "unprocessed"
            val bodyLandmarkItems = mutableListOf<PoseLandmarkItem>()
            var bodyBoxLeft = 0f
            var bodyBoxTop = 0f
            var bodyBoxRight = 0f
            var bodyBoxBottom = 0f
            var isFullBody = false
            var bodyConfidence = 0f

            if (detectedPose != null) {
                val relevantLandmarks = detectedPose.allPoseLandmarks.filter { lm ->
                    isRelevantPoseLandmark(lm.landmarkType) && lm.inFrameLikelihood >= 0.25f
                }

                if (relevantLandmarks.size >= 4) {
                    bodyStatus = "detected"
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = Float.MIN_VALUE
                    var maxY = Float.MIN_VALUE

                    relevantLandmarks.forEach { lm ->
                        val pos = lm.position
                        if (pos.x < minX) minX = pos.x
                        if (pos.y < minY) minY = pos.y
                        if (pos.x > maxX) maxX = pos.x
                        if (pos.y > maxY) maxY = pos.y

                        bodyLandmarkItems.add(
                            PoseLandmarkItem(
                                landmarkType = getPoseLandmarkName(lm.landmarkType),
                                position = PointF2D(pos.x, pos.y),
                                inFrameLikelihood = lm.inFrameLikelihood
                            )
                        )
                    }

                    val padX = (maxX - minX) * 0.08f
                    val padY = (maxY - minY) * 0.08f
                    bodyBoxLeft = (minX - padX).coerceAtLeast(0f)
                    bodyBoxTop = (minY - padY).coerceAtLeast(0f)
                    bodyBoxRight = (maxX + padX).coerceAtMost(imgWidth.toFloat())
                    bodyBoxBottom = (maxY + padY).coerceAtMost(imgHeight.toFloat())

                    bodyConfidence = relevantLandmarks.map { it.inFrameLikelihood }.average().toFloat()

                    // Check if lower body (knees/ankles) + upper body (shoulders/hips) are present
                    val hasShoulders = relevantLandmarks.any { it.landmarkType == PoseLandmark.LEFT_SHOULDER || it.landmarkType == PoseLandmark.RIGHT_SHOULDER }
                    val hasHips = relevantLandmarks.any { it.landmarkType == PoseLandmark.LEFT_HIP || it.landmarkType == PoseLandmark.RIGHT_HIP }
                    val hasLegs = relevantLandmarks.any {
                        it.landmarkType == PoseLandmark.LEFT_KNEE || it.landmarkType == PoseLandmark.RIGHT_KNEE ||
                        it.landmarkType == PoseLandmark.LEFT_ANKLE || it.landmarkType == PoseLandmark.RIGHT_ANKLE
                    }
                    isFullBody = hasShoulders && hasHips && hasLegs
                } else {
                    bodyStatus = "no_body"
                }
            } else {
                bodyStatus = "no_body"
            }

            return@withContext image.copy(
                processingStatus = "processed",
                croppedFacePath = croppedFacePath,
                imageWidth = imgWidth,
                imageHeight = imgHeight,
                faceDetectionStatus = faceStatus,
                faceBoxLeft = faceBoxLeft,
                faceBoxTop = faceBoxTop,
                faceBoxRight = faceBoxRight,
                faceBoxBottom = faceBoxBottom,
                detectedOrientation = detectedOrientation,
                eulerX = eulerX,
                eulerY = eulerY,
                eulerZ = eulerZ,
                facialLandmarks = landmarksSummary,
                faceConfidence = faceConfidence,
                detectedFacesCount = detectedFaces.size,
                bodyDetectionStatus = bodyStatus,
                bodyLandmarks = bodyLandmarkItems,
                bodyBoxLeft = bodyBoxLeft,
                bodyBoxTop = bodyBoxTop,
                bodyBoxRight = bodyBoxRight,
                bodyBoxBottom = bodyBoxBottom,
                isFullBodyVisible = isFullBody,
                bodyConfidence = bodyConfidence,
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing reference image ${image.filename}", e)
            return@withContext image.copy(
                processingStatus = "error",
                errorMessage = e.localizedMessage ?: e.message ?: "Processing failed"
            )
        } finally {
            try {
                loadedBitmap?.recycle()
            } catch (ignored: Exception) {}
            try {
                faceDetector.close()
            } catch (ignored: Exception) {}
            try {
                poseDetector.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Estimates face orientation based on actual ML Kit head Euler Yaw (Y) and Pitch (X)
     */
    fun estimateFaceOrientation(yaw: Float, pitch: Float): String {
        if (abs(pitch) > 42f) {
            return if (pitch > 0) "Top Angle View" else "Low Angle View"
        }
        return when {
            yaw in -15f..15f -> "Front View"
            yaw in -45f..-15f -> "Left 3/4 View"
            yaw in 15f..45f -> "Right 3/4 View"
            yaw < -45f -> "Left Profile"
            yaw > 45f -> "Right Profile"
            else -> "Unknown Orientation"
        }
    }

    private fun isRelevantPoseLandmark(type: Int): Boolean {
        return when (type) {
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE -> true
            else -> false
        }
    }

    private fun getPoseLandmarkName(type: Int): String {
        return when (type) {
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

    private fun cropFaceWithPadding(bitmap: Bitmap, box: Rect, paddingFactor: Float): Bitmap? {
        val padW = (box.width() * paddingFactor).toInt()
        val padH = (box.height() * paddingFactor).toInt()

        val left = max(0, box.left - padW)
        val top = max(0, box.top - padH)
        val right = min(bitmap.width, box.right + padW)
        val bottom = min(bitmap.height, box.bottom + padH)

        val cropWidth = right - left
        val cropHeight = bottom - top

        return if (cropWidth > 10 && cropHeight > 10) {
            Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
        } else null
    }

    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        // 1. Read bounds only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }

        // 2. Compute sample size
        var inSampleSize = 1
        var w = options.outWidth
        var h = options.outHeight
        while (w > maxDim || h > maxDim) {
            inSampleSize *= 2
            w /= 2
            h /= 2
        }

        // 3. Decode scaled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null

        // 4. Adjust for EXIF orientation
        val rotation = getExifOrientationDegrees(context, uri)
        return if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }
    }

    private fun getExifOrientationDegrees(context: Context, uri: Uri): Int {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine EXIF orientation: ${e.message}")
        }
        return 0
    }

    private fun saveBitmapToJpeg(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save face preview to ${file.absolutePath}", e)
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
