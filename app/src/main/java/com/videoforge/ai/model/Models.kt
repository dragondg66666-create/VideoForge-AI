package com.videoforge.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class SourceVideo(
    val filename: String,
    val uriString: String? = null,
    val sizeBytes: Long = 0,
    val contentType: String = "video/mp4",
    val durationMs: Long = 0,
    val durationSeconds: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val fps: Double = 24.0,
    val resolution: String = "1920x1080",
    val bitrate: Long = 0,
    val uploadedAt: String = ""
)

@Serializable
data class CharacterImage(
    val imageId: String,
    val filename: String,
    val uriString: String? = null,
    val sizeBytes: Long = 0,
    val angleTag: String = "Front View",
    val uploadedAt: String = "",

    // Phase 2.3: Real ML Kit Reference Processing Results
    val processingStatus: String = "unprocessed", // "unprocessed", "processing", "processed", "error"
    val croppedFacePath: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,

    // Face detection metrics
    val faceDetectionStatus: String = "unprocessed", // "detected", "no_face", "unprocessed", "error"
    val faceBoxLeft: Float = 0f,
    val faceBoxTop: Float = 0f,
    val faceBoxRight: Float = 0f,
    val faceBoxBottom: Float = 0f,
    val detectedOrientation: String = "Unknown", // "Front View", "Left 3/4 View", "Right 3/4 View", "Left Profile", "Right Profile", "Unknown"
    val eulerX: Float = 0f, // pitch
    val eulerY: Float = 0f, // yaw
    val eulerZ: Float = 0f, // roll
    val facialLandmarks: FaceLandmarksSummary? = null,
    val faceConfidence: Float = 0f,
    val detectedFacesCount: Int = 0,

    // Body pose detection metrics
    val bodyDetectionStatus: String = "unprocessed", // "detected", "no_body", "unprocessed", "error"
    val bodyLandmarks: List<PoseLandmarkItem> = emptyList(),
    val bodyBoxLeft: Float = 0f,
    val bodyBoxTop: Float = 0f,
    val bodyBoxRight: Float = 0f,
    val bodyBoxBottom: Float = 0f,
    val isFullBodyVisible: Boolean = false,
    val bodyConfidence: Float = 0f,

    val errorMessage: String? = null
)

@Serializable
data class ReferenceCoverage(
    val hasFrontView: Boolean = false,
    val hasLeftThreeQuarter: Boolean = false,
    val hasRightThreeQuarter: Boolean = false,
    val hasLeftProfile: Boolean = false,
    val hasRightProfile: Boolean = false,
    val hasFullBody: Boolean = false,
    val totalProcessed: Int = 0,
    val coveredCount: Int = 0,
    val totalRequired: Int = 6,
    val missingAngles: List<String> = emptyList()
) {
    val hasThreeQuarterView: Boolean get() = hasLeftThreeQuarter || hasRightThreeQuarter
    val hasProfileView: Boolean get() = hasLeftProfile || hasRightProfile
    val isRigReady: Boolean get() = coveredCount >= 3
}

@Serializable
data class CharacterProfile(
    val characterId: String,
    val name: String,
    val images: List<CharacterImage> = emptyList()
) {
    fun computeCoverage(): ReferenceCoverage {
        val validImages = images.filter { it.processingStatus == "processed" }
        val hasFront = validImages.any {
            it.faceDetectionStatus == "detected" && (it.detectedOrientation == "Front View" || it.angleTag == "Front View")
        }
        val hasLeft34 = validImages.any {
            it.faceDetectionStatus == "detected" && (it.detectedOrientation == "Left 3/4 View" || it.angleTag.contains("Left"))
        }
        val hasRight34 = validImages.any {
            it.faceDetectionStatus == "detected" && (it.detectedOrientation == "Right 3/4 View" || it.angleTag.contains("Right"))
        }
        val hasLeftProfile = validImages.any {
            it.faceDetectionStatus == "detected" && (it.detectedOrientation == "Left Profile" || it.angleTag.contains("Profile"))
        }
        val hasRightProfile = validImages.any {
            it.faceDetectionStatus == "detected" && it.detectedOrientation == "Right Profile"
        }
        val hasBody = validImages.any {
            it.bodyDetectionStatus == "detected" && it.isFullBodyVisible
        }

        val missing = mutableListOf<String>()
        if (!hasFront) missing.add("Front Face View (±15°)")
        if (!hasLeft34) missing.add("Left 3/4 Angle (-30°)")
        if (!hasRight34) missing.add("Right 3/4 Angle (+30°)")
        if (!hasLeftProfile) missing.add("Left Profile (-60° to -90°)")
        if (!hasRightProfile) missing.add("Right Profile (+60° to +90°)")
        if (!hasBody) missing.add("Full-Body Standing View")

        val covered = 6 - missing.size

        return ReferenceCoverage(
            hasFrontView = hasFront,
            hasLeftThreeQuarter = hasLeft34,
            hasRightThreeQuarter = hasRight34,
            hasLeftProfile = hasLeftProfile,
            hasRightProfile = hasRightProfile,
            hasFullBody = hasBody,
            totalProcessed = validImages.size,
            coveredCount = covered,
            totalRequired = 6,
            missingAngles = missing
        )
    }
}

@Serializable
data class DetectedScene(
    val sceneIndex: Int,
    val timestampMs: Long,
    val formattedTime: String,
    val thumbnailPath: String? = null,
    val diffScore: Float = 0f,
    val description: String = ""
)

@Serializable
data class PointF2D(
    val x: Float,
    val y: Float
)

@Serializable
data class FaceLandmarksSummary(
    val leftEye: PointF2D? = null,
    val rightEye: PointF2D? = null,
    val noseBase: PointF2D? = null,
    val mouthLeft: PointF2D? = null,
    val mouthRight: PointF2D? = null,
    val mouthBottom: PointF2D? = null
)

@Serializable
data class DetectedFaceTrack(
    val trackingId: Int? = null,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val headEulerAngleX: Float = 0f, // pitch
    val headEulerAngleY: Float = 0f, // yaw
    val headEulerAngleZ: Float = 0f, // roll
    val landmarks: FaceLandmarksSummary? = null,
    val confidence: Float = 0.95f
)

@Serializable
data class PoseLandmarkItem(
    val landmarkType: String,
    val position: PointF2D,
    val inFrameLikelihood: Float
)

@Serializable
data class DetectedPersonTrack(
    val personId: Int,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val landmarks: List<PoseLandmarkItem> = emptyList(),
    val associatedFace: DetectedFaceTrack? = null,
    val confidence: Float = 0.9f,
    val status: String = "DETECTED",
    val speedPxPerSec: Float = 0f,
    val instantVelocityX: Float = 0f,
    val instantVelocityY: Float = 0f
)

@Serializable
data class TrajectoryPoint(
    val timestampMs: Long,
    val formattedTime: String = "",
    val x: Float,
    val y: Float,
    val confidence: Float = 1.0f
)

@Serializable
data class LandmarkTrajectory(
    val landmarkName: String,
    val points: List<TrajectoryPoint> = emptyList(),
    val totalPathLengthPx: Float = 0f
)

@Serializable
data class TrackFrame(
    val timestampMs: Long,
    val formattedTime: String = "",
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val centerX: Float = (boxLeft + boxRight) / 2f,
    val centerY: Float = (boxTop + boxBottom) / 2f,
    val speedPxPerSec: Float = 0f,
    val instantVelocityX: Float = 0f,
    val instantVelocityY: Float = 0f,
    val scaleChangeFromPrev: Float = 0f,
    val headEulerY: Float = 0f,
    val headEulerX: Float = 0f,
    val headEulerZ: Float = 0f,
    val associatedFace: DetectedFaceTrack? = null,
    val landmarks: List<PoseLandmarkItem> = emptyList(),
    val confidence: Float = 0.9f,
    val isInterpolated: Boolean = false,
    val status: String = "DETECTED" // "DETECTED", "INTERPOLATED_OCCLUDED"
)

@Serializable
data class PersonTrack(
    val trackId: Int,
    val label: String = "Track #$trackId",
    val firstSeenMs: Long = 0L,
    val lastSeenMs: Long = 0L,
    val durationMs: Long = 0L,
    val totalFramesTracked: Int = 0,
    val averageConfidence: Float = 0.9f,
    val motionDirection: String = "Stationary",
    val averageSpeedPxPerSec: Float = 0f,
    val maxSpeedPxPerSec: Float = 0f,
    val netDisplacementX: Float = 0f,
    val netDisplacementY: Float = 0f,
    val scaleChangePercent: Float = 0f,
    val interruptionCount: Int = 0,
    val frames: List<TrackFrame> = emptyList(),
    val trajectories: List<LandmarkTrajectory> = emptyList()
)

@Serializable
data class TrackingSummary(
    val totalTracks: Int = 0,
    val activeTracksCount: Int = 0,
    val averageTrackDurationMs: Long = 0L,
    val maxTrackDurationMs: Long = 0L,
    val totalInterruptions: Int = 0,
    val predominantMotion: String = "Stationary",
    val tracks: List<PersonTrack> = emptyList()
)

@Serializable
data class FrameTrackingResult(
    val timestampMs: Long,
    val formattedTime: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val thumbnailPath: String? = null,
    val detectedFaces: List<DetectedFaceTrack> = emptyList(),
    val detectedPersons: List<DetectedPersonTrack> = emptyList()
)

@Serializable
data class MaskMetrics(
    val maskWidth: Int = 0,
    val maskHeight: Int = 0,
    val coveragePercent: Float = 0f,
    val foregroundPixelCount: Int = 0,
    val boundingBoxLeft: Float = 0f,
    val boundingBoxTop: Float = 0f,
    val boundingBoxRight: Float = 0f,
    val boundingBoxBottom: Float = 0f,
    val averageConfidence: Float = 0f
)

@Serializable
data class SegmentationFrame(
    val timestampMs: Long,
    val formattedTime: String,
    val sourceFramePath: String? = null,
    val maskPath: String? = null,
    val foregroundCutoutPath: String? = null,
    val confidenceThreshold: Float = 0.5f,
    val featherRadius: Float = 0.05f,
    val metrics: MaskMetrics = MaskMetrics(),
    val associatedTrackIds: List<Int> = emptyList()
)

@Serializable
data class SegmentationSummary(
    val totalFramesSegmented: Int = 0,
    val averageCoveragePercent: Float = 0f,
    val modelType: String = "Google ML Kit Selfie Segmentation (Raw Float Mask)",
    val segmentationNotice: String = "Unified foreground probability mask for all persons in frame. Model does not perform multi-instance person separation.",
    val frames: List<SegmentationFrame> = emptyList()
)

@Serializable
data class AnalysisState(
    val status: String = "not_started", // "not_started", "analyzing", "completed", "error"
    val message: String? = null,
    val progress: Float = 0f,
    val progressDetail: String = "",
    val framesAnalyzed: Int = 0,
    val detectedScenes: List<DetectedScene> = emptyList(),
    val trackingResults: List<FrameTrackingResult> = emptyList(),
    val trackingSummary: TrackingSummary = TrackingSummary(),
    val personTracks: List<PersonTrack> = emptyList(),
    val segmentation: SegmentationSummary = SegmentationSummary(),
    val totalMasksGenerated: Int = 0,
    val uniqueFacesCount: Int = 0,
    val uniquePersonsCount: Int = 0,
    val detectedFaces: Int = 0,
    val sceneCuts: Int = 0,
    val detectedFps: Double = 24.0,
    val resolution: String = "1080p FHD",
    val motionIntensity: String = "Moderate",
    val audioTrackDetected: Boolean = true,
    val keyframesCount: Int = 0,
    val updatedAt: String = ""
)

@Serializable
data class PreservationSettings(
    val originalAudio: Boolean = true,
    val originalTiming: Boolean = true,
    val originalBackground: Boolean = true
)

@Serializable
data class ReplacementAsset(
    val assetId: String,
    val filename: String,
    val uriString: String? = null,
    val localCachedPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val hasAlphaChannel: Boolean = true,
    val transparentPixelRatio: Float = 0f,
    val isFullyOpaqueWarning: Boolean = false,
    val validationMessage: String = "",
    val isSampleAsset: Boolean = false
)

@Serializable
data class CompositingTransform(
    val timestampMs: Long = 0L,
    val targetTrackId: Int = 0,
    val sourceBoxLeft: Float = 0f,
    val sourceBoxTop: Float = 0f,
    val sourceBoxRight: Float = 0f,
    val sourceBoxBottom: Float = 0f,
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
    val targetWidth: Float = 0f,
    val targetHeight: Float = 0f,
    val baseScale: Float = 1.0f,
    val rotationDeg: Float = 0f,
    val userScaleMultiplier: Float = 1.0f,
    val userOffsetX: Float = 0f,
    val userOffsetY: Float = 0f,
    val userRotationTrimDeg: Float = 0f,
    val userOpacity: Float = 1.0f,
    val trackStatus: String = "ACTIVE",
    val isOccluded: Boolean = false
)

@Serializable
data class CompositedPreviewFrame(
    val timestampMs: Long,
    val formattedTime: String,
    val sourceFramePath: String? = null,
    val compositedFramePath: String? = null,
    val layerOnlyPath: String? = null,
    val targetTrackId: Int = 0,
    val transform: CompositingTransform = CompositingTransform(timestampMs, targetTrackId),
    val maskAwareClippingApplied: Boolean = false,
    val modelNotice: String = "Unified segmentation mask: Per-track bounding box attribution applied without multi-instance separation."
)

@Serializable
data class VideoExportConfig(
    val outputWidth: Int = 1280,
    val outputHeight: Int = 720,
    val targetFps: Int = 30,
    val bitrate: Int = 4_000_000,
    val preserveAudio: Boolean = true,
    val qualityPreset: String = "HIGH" // "HIGH", "BALANCED", "DRAFT"
)

@Serializable
data class ExportProgress(
    val progress: Float = 0f,
    val currentTimestampMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val estimatedRemainingSeconds: Long? = null,
    val statusMessage: String = "",
    val stage: String = "IDLE" // "IDLE", "PREPARING", "RENDERING_FRAMES", "PROCESSING_AUDIO", "MUXING", "COMPLETED", "FAILED", "CANCELLED"
)

@Serializable
data class VideoExportResult(
    val isSuccess: Boolean = false,
    val outputPath: String? = null,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val renderedFramesCount: Int = 0,
    val hasAudioTrack: Boolean = false,
    val audioPreserved: Boolean = false,
    val audioNotice: String? = null,
    val errorMessage: String? = null,
    val renderingDurationMs: Long = 0L
)

@Serializable
data class ReplacementStudioState(
    val enabled: Boolean = true,
    val targetTrackId: Int? = null,
    val activeAsset: ReplacementAsset? = null,
    val userScaleMultiplier: Float = 1.0f,
    val userOffsetX: Float = 0f,
    val userOffsetY: Float = 0f,
    val userRotationTrimDeg: Float = 0f,
    val userOpacity: Float = 1.0f,
    val maskAwareBlending: Boolean = true,
    val previewMode: String = "COMPOSITED", // "COMPOSITED", "SIDE_BY_SIDE", "LAYER_ONLY", "ORIGINAL"
    val splitSliderRatio: Float = 0.5f,
    val selectedTimestampMs: Long = 0L,
    val cachedFrames: List<CompositedPreviewFrame> = emptyList(),
    val exportConfig: VideoExportConfig = VideoExportConfig(),
    val lastExportResult: VideoExportResult? = null
)

@Serializable
data class Project(
    val projectId: String,
    val name: String,
    val description: String = "",
    val createdAt: String,
    val updatedAt: String,
    val sourceVideo: SourceVideo? = null,
    val characters: List<CharacterProfile> = emptyList(),
    val scenes: List<String> = emptyList(),
    val analysis: AnalysisState = AnalysisState(),
    val preservation: PreservationSettings = PreservationSettings(),
    val replacementStudio: ReplacementStudioState = ReplacementStudioState()
)

@Serializable
data class ProjectPackageExport(
    val schemaVersion: String = "1.0",
    val projectId: String,
    val name: String,
    val description: String,
    val createdAt: String,
    val updatedAt: String,
    val sourceVideo: SourceVideo?,
    val characters: List<CharacterProfile>,
    val scenes: List<String>,
    val analysis: AnalysisState,
    val preservation: PreservationSettings,
    val replacementStudio: ReplacementStudioState = ReplacementStudioState()
)
