package com.videoforge.ai.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videoforge.ai.data.ProjectRepository
import com.videoforge.ai.media.CharacterReferenceEngine
import com.videoforge.ai.media.CharacterReplacementEngine
import com.videoforge.ai.media.FaceAndPoseTrackingEngine
import com.videoforge.ai.media.MotionTrackingEngine
import com.videoforge.ai.media.PersonSegmentationEngine
import com.videoforge.ai.media.SceneDetectionEngine
import com.videoforge.ai.media.VideoMetadataExtractor
import com.videoforge.ai.media.VideoRenderingEngine
import com.videoforge.ai.model.AnalysisState
import com.videoforge.ai.model.CharacterImage
import com.videoforge.ai.model.CharacterProfile
import com.videoforge.ai.model.CompositedPreviewFrame
import com.videoforge.ai.model.CompositingTransform
import com.videoforge.ai.model.ExportProgress
import com.videoforge.ai.model.VideoExportConfig
import com.videoforge.ai.model.VideoExportResult
import com.videoforge.ai.model.DetectedFaceTrack
import com.videoforge.ai.model.DetectedPersonTrack
import com.videoforge.ai.model.DetectedScene
import com.videoforge.ai.model.FrameTrackingResult
import com.videoforge.ai.model.LandmarkTrajectory
import com.videoforge.ai.model.MaskMetrics
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.model.PointF2D
import com.videoforge.ai.model.PoseLandmarkItem
import com.videoforge.ai.model.PreservationSettings
import com.videoforge.ai.model.Project
import com.videoforge.ai.model.ReplacementAsset
import com.videoforge.ai.model.ReplacementStudioState
import com.videoforge.ai.model.SegmentationFrame
import com.videoforge.ai.model.SegmentationSummary
import com.videoforge.ai.model.SourceVideo
import com.videoforge.ai.model.TrackFrame
import com.videoforge.ai.model.TrackingSummary
import com.videoforge.ai.model.TrajectoryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class AppTab {
    ANALYZER,
    STUDIO,
    OVERVIEW
}

class VideoForgeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _activeProject = MutableStateFlow<Project?>(null)
    val activeProject: StateFlow<Project?> = _activeProject.asStateFlow()

    private val _currentTab = MutableStateFlow(AppTab.ANALYZER)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _activeCharacterId = MutableStateFlow("character_01")
    val activeCharacterId: StateFlow<String> = _activeCharacterId.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisProgress = MutableStateFlow(0f)
    val analysisProgress: StateFlow<Float> = _analysisProgress.asStateFlow()

    private val _analysisDetail = MutableStateFlow("")
    val analysisDetail: StateFlow<String> = _analysisDetail.asStateFlow()

    private val _targetSeekTimestampMs = MutableStateFlow<Long?>(null)
    val targetSeekTimestampMs: StateFlow<Long?> = _targetSeekTimestampMs.asStateFlow()

    private val _selectedTrackingFrameTimestamp = MutableStateFlow<Long?>(null)
    val selectedTrackingFrameTimestamp: StateFlow<Long?> = _selectedTrackingFrameTimestamp.asStateFlow()

    private val _selectedPersonId = MutableStateFlow<Int?>(null)
    val selectedPersonId: StateFlow<Int?> = _selectedPersonId.asStateFlow()

    private val _selectedTrackId = MutableStateFlow<Int?>(null)
    val selectedTrackId: StateFlow<Int?> = _selectedTrackId.asStateFlow()

    // Phase 2.5: Real Person Segmentation & Alpha Mask State
    private val _selectedSegmentationTimestamp = MutableStateFlow<Long?>(null)
    val selectedSegmentationTimestamp: StateFlow<Long?> = _selectedSegmentationTimestamp.asStateFlow()

    private val _segmentationDisplayMode = MutableStateFlow("OVERLAY") // "OVERLAY", "MASK", "CUTOUT", "SOURCE"
    val segmentationDisplayMode: StateFlow<String> = _segmentationDisplayMode.asStateFlow()

    private val _maskConfidenceThreshold = MutableStateFlow(PersonSegmentationEngine.DEFAULT_THRESHOLD)
    val maskConfidenceThreshold: StateFlow<Float> = _maskConfidenceThreshold.asStateFlow()

    private val _edgeFeatherRadius = MutableStateFlow(PersonSegmentationEngine.DEFAULT_FEATHER_RADIUS)
    val edgeFeatherRadius: StateFlow<Float> = _edgeFeatherRadius.asStateFlow()

    // Phase 2.6: Real Character Replacement & Compositing Engine State
    private val _replacementEnabled = MutableStateFlow(true)
    val replacementEnabled: StateFlow<Boolean> = _replacementEnabled.asStateFlow()

    private val _selectedReplacementTrackId = MutableStateFlow<Int?>(1)
    val selectedReplacementTrackId: StateFlow<Int?> = _selectedReplacementTrackId.asStateFlow()

    private val _activeReplacementAsset = MutableStateFlow<ReplacementAsset?>(null)
    val activeReplacementAsset: StateFlow<ReplacementAsset?> = _activeReplacementAsset.asStateFlow()

    private val _replacementUserScale = MutableStateFlow(1.0f)
    val replacementUserScale: StateFlow<Float> = _replacementUserScale.asStateFlow()

    private val _replacementUserOffsetX = MutableStateFlow(0f)
    val replacementUserOffsetX: StateFlow<Float> = _replacementUserOffsetX.asStateFlow()

    private val _replacementUserOffsetY = MutableStateFlow(0f)
    val replacementUserOffsetY: StateFlow<Float> = _replacementUserOffsetY.asStateFlow()

    private val _replacementUserRotationTrim = MutableStateFlow(0f)
    val replacementUserRotationTrim: StateFlow<Float> = _replacementUserRotationTrim.asStateFlow()

    private val _replacementUserOpacity = MutableStateFlow(1.0f)
    val replacementUserOpacity: StateFlow<Float> = _replacementUserOpacity.asStateFlow()

    private val _replacementMaskAware = MutableStateFlow(true)
    val replacementMaskAware: StateFlow<Boolean> = _replacementMaskAware.asStateFlow()

    private val _replacementPreviewMode = MutableStateFlow("COMPOSITED") // "COMPOSITED", "SIDE_BY_SIDE", "LAYER_ONLY", "ORIGINAL"
    val replacementPreviewMode: StateFlow<String> = _replacementPreviewMode.asStateFlow()

    private val _replacementSplitRatio = MutableStateFlow(0.5f)
    val replacementSplitRatio: StateFlow<Float> = _replacementSplitRatio.asStateFlow()

    private val _replacementTimestampMs = MutableStateFlow(0L)
    val replacementTimestampMs: StateFlow<Long> = _replacementTimestampMs.asStateFlow()

    private val _compositedPreviewFrame = MutableStateFlow<CompositedPreviewFrame?>(null)
    val compositedPreviewFrame: StateFlow<CompositedPreviewFrame?> = _compositedPreviewFrame.asStateFlow()

    private val _isCompositing = MutableStateFlow(false)
    val isCompositing: StateFlow<Boolean> = _isCompositing.asStateFlow()

    // Phase 2.7: Native MP4 Export State
    private var exportJob: Job? = null

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(ExportProgress())
    val exportProgress: StateFlow<ExportProgress> = _exportProgress.asStateFlow()

    private val _lastExportResult = MutableStateFlow<VideoExportResult?>(null)
    val lastExportResult: StateFlow<VideoExportResult?> = _lastExportResult.asStateFlow()

    private val _exportConfig = MutableStateFlow(VideoExportConfig())
    val exportConfig: StateFlow<VideoExportConfig> = _exportConfig.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>("")
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _toastIsError = MutableStateFlow(false)
    val toastIsError: StateFlow<Boolean> = _toastIsError.asStateFlow()

    init {
        loadData()
    }

    private fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun showToast(msg: String, isError: Boolean = false) {
        _toastMessage.value = msg
        _toastIsError.value = isError
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun selectProject(project: Project) {
        _activeProject.value = project
        val firstChar = project.characters.firstOrNull()?.characterId ?: "character_01"
        _activeCharacterId.value = firstChar
        _selectedTrackingFrameTimestamp.value = project.analysis.trackingResults.firstOrNull()?.timestampMs
        _selectedSegmentationTimestamp.value = project.analysis.segmentation.frames.firstOrNull()?.timestampMs
        _selectedPersonId.value = null
        _selectedTrackId.value = null

        // Restore Phase 2.6 Replacement Studio State
        val rep = project.replacementStudio
        _replacementEnabled.value = rep.enabled
        _selectedReplacementTrackId.value = rep.targetTrackId ?: project.analysis.personTracks.firstOrNull()?.trackId ?: 1
        _activeReplacementAsset.value = rep.activeAsset
        _replacementUserScale.value = rep.userScaleMultiplier
        _replacementUserOffsetX.value = rep.userOffsetX
        _replacementUserOffsetY.value = rep.userOffsetY
        _replacementUserRotationTrim.value = rep.userRotationTrimDeg
        _replacementUserOpacity.value = rep.userOpacity
        _replacementMaskAware.value = rep.maskAwareBlending
        _replacementPreviewMode.value = rep.previewMode
        _replacementSplitRatio.value = rep.splitSliderRatio
        _replacementTimestampMs.value = rep.selectedTimestampMs
        _compositedPreviewFrame.value = rep.cachedFrames.find { it.timestampMs == rep.selectedTimestampMs } ?: rep.cachedFrames.firstOrNull()
        _exportConfig.value = rep.exportConfig
        _lastExportResult.value = rep.lastExportResult
    }

    fun selectCharacter(charId: String) {
        _activeCharacterId.value = charId
    }

    fun seekToTimestamp(ms: Long) {
        _targetSeekTimestampMs.value = ms
        _selectedTrackingFrameTimestamp.value = ms
        _selectedSegmentationTimestamp.value = ms
        _replacementTimestampMs.value = ms
    }

    fun clearSeekTarget() {
        _targetSeekTimestampMs.value = null
    }

    fun selectTrackingFrame(timestampMs: Long?) {
        _selectedTrackingFrameTimestamp.value = timestampMs
    }

    fun selectSegmentationFrame(timestampMs: Long?) {
        _selectedSegmentationTimestamp.value = timestampMs
        if (timestampMs != null) {
            seekToTimestamp(timestampMs)
        }
    }

    fun setSegmentationDisplayMode(mode: String) {
        _segmentationDisplayMode.value = mode
    }

    fun setMaskConfidenceThreshold(threshold: Float) {
        _maskConfidenceThreshold.value = threshold.coerceIn(0.10f, 0.90f)
    }

    fun setEdgeFeatherRadius(radius: Float) {
        _edgeFeatherRadius.value = radius.coerceIn(0.01f, 0.20f)
    }

    // Phase 2.6: Replacement Studio Operations
    fun importReplacementAsset(uri: Uri, filename: String) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            try {
                showToast("Analyzing character asset transparency…")
                val asset = CharacterReplacementEngine.validateAndImportAsset(
                    context = getApplication(),
                    uri = uri,
                    filename = filename,
                    projectId = current.projectId
                )
                _activeReplacementAsset.value = asset
                val updatedStudio = current.replacementStudio.copy(activeAsset = asset)
                persistProjectUpdate(current.copy(replacementStudio = updatedStudio))
                if (asset.isFullyOpaqueWarning) {
                    showToast(asset.validationMessage, isError = true)
                } else {
                    showToast("Asset '${asset.filename}' validated (${String.format(Locale.US, "%.1f", asset.transparentPixelRatio)}% transparent)")
                }
                renderCompositingPreview()
            } catch (e: Exception) {
                showToast("Failed to import asset: ${e.message}", isError = true)
            }
        }
    }

    fun loadSampleHeroAsset() {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            try {
                showToast("Synthesizing transparent Cyber Operative asset…")
                val sample = CharacterReplacementEngine.createSampleHeroAsset(
                    context = getApplication(),
                    projectId = current.projectId
                )
                _activeReplacementAsset.value = sample
                val updatedStudio = current.replacementStudio.copy(activeAsset = sample)
                persistProjectUpdate(current.copy(replacementStudio = updatedStudio))
                showToast("Ready-to-composite Cyber Operative PNG loaded")
                renderCompositingPreview()
            } catch (e: Exception) {
                showToast("Failed to create sample asset: ${e.message}", isError = true)
            }
        }
    }

    fun setReplacementTrack(trackId: Int) {
        _selectedReplacementTrackId.value = trackId
        _selectedTrackId.value = trackId
        _selectedPersonId.value = trackId
        val current = _activeProject.value ?: return
        val updatedStudio = current.replacementStudio.copy(targetTrackId = trackId)
        persistProjectUpdate(current.copy(replacementStudio = updatedStudio))
        renderCompositingPreview()
    }

    fun setReplacementTimestamp(ts: Long) {
        _replacementTimestampMs.value = ts
        seekToTimestamp(ts)
        renderCompositingPreview()
    }

    fun updateReplacementTransform(
        scale: Float = _replacementUserScale.value,
        offsetX: Float = _replacementUserOffsetX.value,
        offsetY: Float = _replacementUserOffsetY.value,
        rotationTrim: Float = _replacementUserRotationTrim.value,
        opacity: Float = _replacementUserOpacity.value,
        maskAware: Boolean = _replacementMaskAware.value
    ) {
        _replacementUserScale.value = scale
        _replacementUserOffsetX.value = offsetX
        _replacementUserOffsetY.value = offsetY
        _replacementUserRotationTrim.value = rotationTrim
        _replacementUserOpacity.value = opacity
        _replacementMaskAware.value = maskAware

        val current = _activeProject.value ?: return
        val updatedStudio = current.replacementStudio.copy(
            userScaleMultiplier = scale,
            userOffsetX = offsetX,
            userOffsetY = offsetY,
            userRotationTrimDeg = rotationTrim,
            userOpacity = opacity,
            maskAwareBlending = maskAware
        )
        persistProjectUpdate(current.copy(replacementStudio = updatedStudio))
        renderCompositingPreview()
    }

    fun setReplacementPreviewMode(mode: String) {
        _replacementPreviewMode.value = mode
        val current = _activeProject.value ?: return
        persistProjectUpdate(current.copy(replacementStudio = current.replacementStudio.copy(previewMode = mode)))
    }

    fun setReplacementSplitRatio(ratio: Float) {
        _replacementSplitRatio.value = ratio.coerceIn(0f, 1f)
    }

    fun toggleReplacementEnabled(enabled: Boolean) {
        _replacementEnabled.value = enabled
        val current = _activeProject.value ?: return
        persistProjectUpdate(current.copy(replacementStudio = current.replacementStudio.copy(enabled = enabled)))
        renderCompositingPreview()
    }

    fun renderCompositingPreview() {
        val current = _activeProject.value ?: return
        val asset = _activeReplacementAsset.value ?: return
        val trackId = _selectedReplacementTrackId.value ?: 1
        val track = current.analysis.personTracks.find { it.trackId == trackId }
            ?: current.analysis.personTracks.firstOrNull()
            ?: return

        val videoUri = current.sourceVideo?.uriString?.let { Uri.parse(it) }
        val ts = _replacementTimestampMs.value

        viewModelScope.launch {
            _isCompositing.value = true
            try {
                val preview = CharacterReplacementEngine.renderPreviewFrame(
                    context = getApplication(),
                    videoUri = videoUri,
                    track = track,
                    asset = asset,
                    timestampMs = ts,
                    projectId = current.projectId,
                    userScaleMultiplier = _replacementUserScale.value,
                    userOffsetX = _replacementUserOffsetX.value,
                    userOffsetY = _replacementUserOffsetY.value,
                    userRotationTrimDeg = _replacementUserRotationTrim.value,
                    userOpacity = _replacementUserOpacity.value,
                    maskAwareBlending = _replacementMaskAware.value,
                    segmentationSummary = current.analysis.segmentation
                )
                _compositedPreviewFrame.value = preview
                val existing = current.replacementStudio.cachedFrames.filter { it.timestampMs != ts }
                val updatedFrames = existing + preview
                val updatedStudio = current.replacementStudio.copy(
                    cachedFrames = updatedFrames,
                    selectedTimestampMs = ts,
                    targetTrackId = trackId,
                    activeAsset = asset
                )
                persistProjectUpdate(current.copy(replacementStudio = updatedStudio))
            } catch (e: Exception) {
                android.util.Log.w("VideoForgeVM", "Failed to render composited preview: ${e.message}")
            } finally {
                _isCompositing.value = false
            }
        }
    }

    // Phase 2.7: Native MP4 Export Operations
    fun updateExportConfig(config: VideoExportConfig) {
        _exportConfig.value = config
        val current = _activeProject.value ?: return
        val updatedStudio = current.replacementStudio.copy(exportConfig = config)
        persistProjectUpdate(current.copy(replacementStudio = updatedStudio))
    }

    fun startExportMp4() {
        if (_isExporting.value) return
        val current = _activeProject.value ?: run {
            showToast("No active project loaded", isError = true)
            return
        }

        val videoUri = current.sourceVideo?.uriString?.let { Uri.parse(it) }
        val trackId = _selectedReplacementTrackId.value ?: 1
        val track = current.analysis.personTracks.find { it.trackId == trackId }
            ?: current.analysis.personTracks.firstOrNull()

        val asset = _activeReplacementAsset.value

        _isExporting.value = true
        _exportProgress.value = ExportProgress(
            progress = 0.01f,
            statusMessage = "Initializing native MediaCodec video encoder…",
            stage = "PREPARING"
        )
        _lastExportResult.value = null

        exportJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = VideoRenderingEngine.renderAndExportVideo(
                    context = getApplication(),
                    videoUri = videoUri,
                    track = track,
                    asset = asset,
                    studioState = current.replacementStudio,
                    config = _exportConfig.value,
                    segmentationSummary = current.analysis.segmentation
                ) { progressUpdate ->
                    _exportProgress.value = progressUpdate
                }

                _lastExportResult.value = result
                val updatedStudio = current.replacementStudio.copy(lastExportResult = result)
                persistProjectUpdate(current.copy(replacementStudio = updatedStudio))

                if (result.isSuccess) {
                    showToast("MP4 exported successfully: ${String.format(Locale.US, "%.1f", result.fileSizeBytes / 1024f / 1024f)} MB")
                } else if (result.errorMessage != "Export cancelled") {
                    showToast("Export failed: ${result.errorMessage}", isError = true)
                }
            } catch (e: Exception) {
                _lastExportResult.value = VideoExportResult(
                    isSuccess = false,
                    errorMessage = e.message ?: "Export failed"
                )
                showToast("Export error: ${e.message}", isError = true)
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun cancelExportMp4() {
        if (!_isExporting.value) return
        exportJob?.cancel()
        _isExporting.value = false
        _exportProgress.value = ExportProgress(
            progress = 0f,
            statusMessage = "Export cancelled by user",
            stage = "CANCELLED"
        )
        showToast("Export cancelled")
    }

    fun dismissExportResult() {
        _lastExportResult.value = null
    }

    fun saveExportToUri(sourcePath: String, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(sourcePath)
                if (!sourceFile.exists()) {
                    showToast("Exported video file not found", isError = true)
                    return@launch
                }

                getApplication<Application>().contentResolver.openOutputStream(targetUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                showToast("Saved MP4 to chosen location")
            } catch (e: Exception) {
                showToast("Failed to save MP4: ${e.message}", isError = true)
            }
        }
    }

    fun selectPerson(personId: Int?) {
        _selectedPersonId.value = personId
        _selectedTrackId.value = personId
    }

    fun selectTrack(trackId: Int?) {
        _selectedTrackId.value = trackId
        _selectedPersonId.value = trackId
        if (trackId != null) {
            val track = _activeProject.value?.analysis?.personTracks?.find { it.trackId == trackId }
            val firstFrame = track?.frames?.firstOrNull()
            if (firstFrame != null) {
                seekToTimestamp(firstFrame.timestampMs)
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            val list = repository.loadProjects()
            _projects.value = list
            if (_activeProject.value == null && list.isNotEmpty()) {
                selectProject(list[0])
            }
        }
    }

    fun createProject(name: String, description: String = "") {
        viewModelScope.launch {
            val created = repository.createProject(name, description)
            val updated = repository.loadProjects()
            _projects.value = updated
            selectProject(created)
            showToast("Project '${created.name}' created successfully")
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            val updated = repository.loadProjects()
            _projects.value = updated
            if (_activeProject.value?.projectId == projectId) {
                _activeProject.value = updated.firstOrNull()
            }
            showToast("Project deleted")
        }
    }

    /**
     * Ingests a real video content URI and extracts actual duration, resolution, rotation, and fps via MediaMetadataRetriever
     */
    fun setSourceVideoUri(uri: Uri, filename: String, sizeBytes: Long) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            try {
                showToast("Extracting video metadata…")
                val realVideo = VideoMetadataExtractor.extractMetadata(
                    context = getApplication(),
                    uri = uri,
                    filename = filename,
                    sizeBytes = sizeBytes
                )

                val updated = current.copy(
                    sourceVideo = realVideo,
                    analysis = AnalysisState(
                        status = "not_started",
                        resolution = realVideo.resolution,
                        detectedFps = realVideo.fps
                    )
                )
                persistProjectUpdate(updated)
                showToast("Video '${realVideo.filename}' loaded (${realVideo.resolution}, ${(realVideo.durationMs / 1000.0).toInt()}s)")
            } catch (e: Exception) {
                showToast("Failed to inspect video: ${e.message}", isError = true)
            }
        }
    }

    fun setDemoVideo() {
        val current = _activeProject.value ?: return
        val video = SourceVideo(
            filename = "action_stunt_take03_1080p.mp4",
            uriString = null,
            sizeBytes = 28_400_000,
            durationMs = 18_200L,
            durationSeconds = 18.2,
            width = 1920,
            height = 1080,
            rotation = 0,
            fps = 24.0,
            resolution = "1920x1080",
            bitrate = 12_500_000L,
            uploadedAt = nowIso()
        )
        val updated = current.copy(
            sourceVideo = video,
            analysis = AnalysisState(status = "not_started", resolution = "1920x1080", detectedFps = 24.0)
        )
        persistProjectUpdate(updated)
        showToast("Demo video profile loaded")
    }

    fun removeSourceVideo() {
        val current = _activeProject.value ?: return
        val updated = current.copy(
            sourceVideo = null,
            analysis = AnalysisState(status = "not_started")
        )
        persistProjectUpdate(updated)
        showToast("Source video removed")
    }

    fun updatePreservation(audio: Boolean, timing: Boolean, bg: Boolean) {
        val current = _activeProject.value ?: return
        val updated = current.copy(
            preservation = PreservationSettings(
                originalAudio = audio,
                originalTiming = timing,
                originalBackground = bg
            )
        )
        persistProjectUpdate(updated)
    }

    /**
     * Executes real on-device scene detection and ML Kit Face & Pose tracking
     */
    fun startAnalysis() {
        val current = _activeProject.value ?: return
        val video = current.sourceVideo
        if (video == null) {
            showToast("Please upload or select a source video first", isError = true)
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisProgress.value = 0.05f
            _analysisDetail.value = "Starting on-device frame sampler…"

            if (video.uriString != null) {
                try {
                    val videoUri = Uri.parse(video.uriString)

                    // Phase 2.1: Run Scene Cut Detection
                    val sceneResult = withContext(Dispatchers.IO) {
                        SceneDetectionEngine.detectScenes(
                            context = getApplication(),
                            uri = videoUri,
                            projectId = current.projectId,
                            samplingIntervalMs = 500L,
                            cutThreshold = 0.36f
                        ) { progress, detail ->
                            _analysisProgress.value = progress * 0.30f // 0% - 30%
                            _analysisDetail.value = "Scene Analysis: $detail"
                        }
                    }

                    // Phase 2.4: Run Real ML Kit Face & Pose Detection on keyframes & timeline intervals
                    _analysisDetail.value = "Preparing ML Kit face and body tracking models…"
                    val durationMs = video.durationMs.takeIf { it > 0 } ?: (video.durationSeconds * 1000).toLong()

                    val sampledTimes = mutableSetOf<Long>()
                    sceneResult.scenes.forEach { sampledTimes.add(it.timestampMs) }

                    var t = 0L
                    while (t < durationMs && sampledTimes.size < 20) {
                        sampledTimes.add(t)
                        t += 750L
                    }
                    val sortedTimestamps = sampledTimes.sorted()

                    val trackingResult = withContext(Dispatchers.IO) {
                        MotionTrackingEngine.analyzeMotionTracks(
                            context = getApplication(),
                            uri = videoUri,
                            projectId = current.projectId,
                            samplingTimestampsMs = sortedTimestamps
                        ) { progress, detail ->
                            _analysisProgress.value = 0.30f + (progress * 0.35f) // 30% - 65%
                            _analysisDetail.value = "Motion Tracking: $detail"
                        }
                    }

                    // Phase 2.5: Run Real ML Kit Selfie Segmentation & Alpha Mask Extraction
                    _analysisDetail.value = "Extracting ML Kit foreground segmentation & alpha masks…"
                    val segmentationResult = withContext(Dispatchers.IO) {
                        PersonSegmentationEngine.segmentVideo(
                            context = getApplication(),
                            uri = videoUri,
                            projectId = current.projectId,
                            existingTracks = trackingResult.trackingSummary.tracks,
                            samplingIntervalMs = 1000L,
                            threshold = _maskConfidenceThreshold.value,
                            featherRadius = _edgeFeatherRadius.value
                        ) { progress, detail ->
                            _analysisProgress.value = 0.65f + (progress * 0.35f) // 65% - 100%
                            _analysisDetail.value = "Alpha Segmentation: $detail"
                        }
                    }

                    val motionLabel = when {
                        sceneResult.averageMotionDiff >= 0.28f -> "High Dynamic Action"
                        sceneResult.averageMotionDiff >= 0.14f -> "Moderate Movement"
                        else -> "Subtle / Static Camera"
                    }

                    val completedAnalysis = AnalysisState(
                        status = "completed",
                        message = "AI Processing complete: ${sceneResult.scenes.size} scenes, ${trackingResult.uniqueFaceIds.size} faces, ${trackingResult.trackingSummary.totalTracks} motion tracks, and ${segmentationResult.summary.totalFramesSegmented} person alpha masks (${String.format(Locale.US, "%.1f", segmentationResult.summary.averageCoveragePercent)}% avg coverage).",
                        progress = 1.0f,
                        progressDetail = "Done",
                        framesAnalyzed = sceneResult.totalFramesSampled + trackingResult.totalFramesProcessed + segmentationResult.totalFramesProcessed,
                        detectedScenes = sceneResult.scenes,
                        trackingResults = trackingResult.frameResults,
                        trackingSummary = trackingResult.trackingSummary,
                        personTracks = trackingResult.trackingSummary.tracks,
                        segmentation = segmentationResult.summary,
                        totalMasksGenerated = segmentationResult.totalFramesProcessed,
                        uniqueFacesCount = trackingResult.uniqueFaceIds.size,
                        uniquePersonsCount = trackingResult.uniquePersonIds.size,
                        detectedFaces = trackingResult.uniqueFaceIds.size,
                        sceneCuts = sceneResult.sceneCutsCount,
                        detectedFps = video.fps,
                        resolution = video.resolution,
                        motionIntensity = motionLabel,
                        audioTrackDetected = true,
                        keyframesCount = sceneResult.scenes.size,
                        updatedAt = nowIso()
                    )

                    val updated = current.copy(
                        analysis = completedAnalysis,
                        scenes = sceneResult.scenes.map { "${it.description} at ${it.formattedTime}" }
                    )
                    persistProjectUpdate(updated)
                    _selectedTrackingFrameTimestamp.value = trackingResult.frameResults.firstOrNull()?.timestampMs
                    _selectedSegmentationTimestamp.value = segmentationResult.summary.frames.firstOrNull()?.timestampMs
                    _selectedTrackId.value = trackingResult.trackingSummary.tracks.firstOrNull()?.trackId
                    _selectedPersonId.value = _selectedTrackId.value
                    _isAnalyzing.value = false
                    showToast("Pipeline complete: ${trackingResult.trackingSummary.totalTracks} tracks and ${segmentationResult.summary.totalFramesSegmented} alpha masks extracted")
                } catch (e: Exception) {
                    _isAnalyzing.value = false
                    val errorAnalysis = current.analysis.copy(
                        status = "error",
                        message = "Analysis failed: ${e.message}"
                    )
                    persistProjectUpdate(current.copy(analysis = errorAnalysis))
                    showToast("Analysis error: ${e.localizedMessage ?: e.message}", isError = true)
                }
            } else {
                _analysisProgress.value = 0.35f
                _analysisDetail.value = "Demo Take: Compiling benchmark motion tracking sequence…"
                kotlinx.coroutines.delay(300)
                _analysisProgress.value = 0.70f
                _analysisDetail.value = "Synthesizing soft alpha mattes & foreground segmentations…"
                kotlinx.coroutines.delay(300)
                _analysisProgress.value = 1.0f

                val demoTracks = createBenchmarkDemoTracks()
                val demoFrames = createBenchmarkDemoFrames()
                val demoSegmentation = createBenchmarkDemoSegmentation()
                val demoScenes = listOf(
                    DetectedScene(1, 0L, "00:00.0", null, 0f, "Master Opening Anchor"),
                    DetectedScene(2, 2250L, "00:02.2", null, 0.42f, "Fast Camera Pan Transition"),
                    DetectedScene(3, 4500L, "00:04.5", null, 0.38f, "Combat Dolly Track Cut")
                )

                val demoSummary = TrackingSummary(
                    totalTracks = 2,
                    activeTracksCount = 2,
                    averageTrackDurationMs = 3750L,
                    maxTrackDurationMs = 4500L,
                    totalInterruptions = 1,
                    predominantMotion = "Moving Right",
                    tracks = demoTracks
                )

                val completedAnalysis = AnalysisState(
                    status = "completed",
                    message = "Benchmark Sequence: 3 scenes, 2 actor tracks, and 3 high-resolution person alpha masks (${String.format(Locale.US, "%.1f", demoSegmentation.averageCoveragePercent)}% avg coverage). Select a local video file to run live on-device ML Kit inference.",
                    progress = 1.0f,
                    progressDetail = "Done",
                    framesAnalyzed = 4,
                    detectedScenes = demoScenes,
                    trackingResults = demoFrames,
                    trackingSummary = demoSummary,
                    personTracks = demoTracks,
                    segmentation = demoSegmentation,
                    totalMasksGenerated = demoSegmentation.totalFramesSegmented,
                    uniqueFacesCount = 2,
                    uniquePersonsCount = 2,
                    detectedFaces = 2,
                    sceneCuts = 2,
                    detectedFps = 24.0,
                    resolution = "1920x1080",
                    motionIntensity = "High Dynamic Action",
                    audioTrackDetected = true,
                    keyframesCount = 3,
                    updatedAt = nowIso()
                )

                val updated = current.copy(
                    analysis = completedAnalysis,
                    scenes = demoScenes.map { "${it.description} at ${it.formattedTime}" }
                )
                persistProjectUpdate(updated)
                _selectedTrackingFrameTimestamp.value = 0L
                _selectedSegmentationTimestamp.value = 0L
                _selectedTrackId.value = 1
                _selectedPersonId.value = 1
                _isAnalyzing.value = false
                showToast("Demo analysis loaded: 2 tracks with trajectories & 3 person alpha masks")
            }
        }
    }

    fun addCharacter(characterId: String, name: String) {
        val current = _activeProject.value ?: return
        val sanitizedId = characterId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        if (sanitizedId.isBlank()) {
            showToast("Character ID cannot be blank", isError = true)
            return
        }
        if (current.characters.any { it.characterId == sanitizedId }) {
            showToast("Character '$sanitizedId' already exists", isError = true)
            return
        }
        val newChar = CharacterProfile(
            characterId = sanitizedId,
            name = name.ifBlank { sanitizedId },
            images = emptyList()
        )
        val updatedList = current.characters + newChar
        val updated = current.copy(characters = updatedList)
        persistProjectUpdate(updated)
        _activeCharacterId.value = sanitizedId
        showToast("Character '$sanitizedId' created")
    }

    /**
     * Ingests and triggers real ML Kit Face and Pose analysis on uploaded character reference photos
     */
    fun addCharacterImage(characterId: String, filename: String, uriString: String?, sizeBytes: Long, angleTag: String) {
        val current = _activeProject.value ?: return
        val initialImg = CharacterImage(
            imageId = UUID.randomUUID().toString(),
            filename = filename,
            uriString = uriString,
            sizeBytes = sizeBytes,
            angleTag = angleTag,
            uploadedAt = nowIso(),
            processingStatus = "processing"
        )
        val updatedCharacters = current.characters.map { c ->
            if (c.characterId == characterId) {
                c.copy(images = c.images + initialImg)
            } else c
        }
        val intermediate = current.copy(characters = updatedCharacters)
        persistProjectUpdate(intermediate)

        // Run on-device ML Kit Face and Pose processing in background
        viewModelScope.launch {
            try {
                showToast("Analyzing reference photo '$filename' with ML Kit…")
                val processed = CharacterReferenceEngine.processReference(getApplication(), initialImg)

                val cur = _activeProject.value ?: return@launch
                val finalizedChars = cur.characters.map { c ->
                    if (c.characterId == characterId) {
                        c.copy(images = c.images.map { if (it.imageId == initialImg.imageId) processed else it })
                    } else c
                }
                persistProjectUpdate(cur.copy(characters = finalizedChars))

                if (processed.faceDetectionStatus == "detected") {
                    showToast("Processed '$filename': ${processed.detectedOrientation} (Yaw: ${processed.eulerY.toInt()}°)")
                } else {
                    showToast("Processed '$filename': No face detected in photo", isError = true)
                }
            } catch (e: Exception) {
                showToast("Failed to process reference: ${e.message}", isError = true)
            }
        }
    }

    /**
     * Reprocesses an individual reference image using ML Kit
     */
    fun reprocessCharacterImage(characterId: String, imageId: String) {
        val current = _activeProject.value ?: return
        val charProfile = current.characters.find { it.characterId == characterId } ?: return
        val targetImg = charProfile.images.find { it.imageId == imageId } ?: return

        // Set to processing state
        val updatedChars = current.characters.map { c ->
            if (c.characterId == characterId) {
                c.copy(images = c.images.map { if (it.imageId == imageId) it.copy(processingStatus = "processing") else it })
            } else c
        }
        persistProjectUpdate(current.copy(characters = updatedChars))

        viewModelScope.launch {
            showToast("Reprocessing image with ML Kit…")
            val processed = CharacterReferenceEngine.processReference(getApplication(), targetImg)
            val cur = _activeProject.value ?: return@launch
            val finalizedChars = cur.characters.map { c ->
                if (c.characterId == characterId) {
                    c.copy(images = c.images.map { if (it.imageId == imageId) processed else it })
                } else c
            }
            persistProjectUpdate(cur.copy(characters = finalizedChars))
            showToast("Reprocessing complete: ${processed.detectedOrientation}")
        }
    }

    fun addDemoReferencesForCharacter(characterId: String) {
        val current = _activeProject.value ?: return
        val demoImages = listOf(
            CharacterImage(
                imageId = UUID.randomUUID().toString(),
                filename = "front_portrait_reference.png",
                uriString = null,
                sizeBytes = 2_150_000,
                angleTag = "Front View",
                uploadedAt = nowIso(),
                processingStatus = "processed",
                faceDetectionStatus = "detected",
                detectedOrientation = "Front View",
                eulerX = 1f,
                eulerY = -2f,
                eulerZ = 0f,
                faceConfidence = 0.99f,
                detectedFacesCount = 1,
                bodyDetectionStatus = "detected",
                isFullBodyVisible = true,
                bodyConfidence = 0.94f
            ),
            CharacterImage(
                imageId = UUID.randomUUID().toString(),
                filename = "three_quarter_profile.png",
                uriString = null,
                sizeBytes = 1_980_000,
                angleTag = "3/4 Left Angle",
                uploadedAt = nowIso(),
                processingStatus = "processed",
                faceDetectionStatus = "detected",
                detectedOrientation = "Left 3/4 View",
                eulerX = 2f,
                eulerY = -28f,
                eulerZ = -1f,
                faceConfidence = 0.97f,
                detectedFacesCount = 1,
                bodyDetectionStatus = "detected",
                isFullBodyVisible = false,
                bodyConfidence = 0.88f
            ),
            CharacterImage(
                imageId = UUID.randomUUID().toString(),
                filename = "side_profile_expression.png",
                uriString = null,
                sizeBytes = 1_840_000,
                angleTag = "Left Profile",
                uploadedAt = nowIso(),
                processingStatus = "processed",
                faceDetectionStatus = "detected",
                detectedOrientation = "Left Profile",
                eulerX = 0f,
                eulerY = -56f,
                eulerZ = 0f,
                faceConfidence = 0.95f,
                detectedFacesCount = 1,
                bodyDetectionStatus = "no_body",
                isFullBodyVisible = false,
                bodyConfidence = 0f
            )
        )
        val updatedCharacters = current.characters.map { c ->
            if (c.characterId == characterId) {
                c.copy(images = c.images + demoImages)
            } else c
        }
        val updated = current.copy(characters = updatedCharacters)
        persistProjectUpdate(updated)
        showToast("Added 3 calibrated reference perspectives for $characterId")
    }

    fun removeCharacterImage(characterId: String, imageId: String) {
        val current = _activeProject.value ?: return
        val updatedCharacters = current.characters.map { c ->
            if (c.characterId == characterId) {
                c.copy(images = c.images.filter { it.imageId != imageId })
            } else c
        }
        val updated = current.copy(characters = updatedCharacters)
        persistProjectUpdate(updated)
        showToast("Reference image removed")
    }

    fun exportPackageJson(): String {
        val current = _activeProject.value ?: return "{}"
        return repository.exportToJson(current)
    }

    private fun persistProjectUpdate(updated: Project) {
        _activeProject.value = updated
        _projects.value = _projects.value.map {
            if (it.projectId == updated.projectId) updated else it
        }
        viewModelScope.launch {
            repository.updateProject(updated)
        }
    }

    private fun createBenchmarkDemoTracks(): List<PersonTrack> {
        val track1Frames = listOf(
            TrackFrame(
                timestampMs = 0L,
                formattedTime = "00:00.0",
                boxLeft = 140f, boxTop = 60f, boxRight = 320f, boxBottom = 340f,
                speedPxPerSec = 0f,
                instantVelocityX = 0f, instantVelocityY = 0f,
                headEulerY = -4f, headEulerX = 2f,
                confidence = 0.96f
            ),
            TrackFrame(
                timestampMs = 1500L,
                formattedTime = "00:01.5",
                boxLeft = 210f, boxTop = 55f, boxRight = 400f, boxBottom = 342f,
                speedPxPerSec = 52f,
                instantVelocityX = 46f, instantVelocityY = -3f,
                scaleChangeFromPrev = 6.2f,
                headEulerY = -8f, headEulerX = 4f,
                confidence = 0.94f
            ),
            TrackFrame(
                timestampMs = 3000L,
                formattedTime = "00:03.0",
                boxLeft = 285f, boxTop = 50f, boxRight = 485f, boxBottom = 345f,
                speedPxPerSec = 58f,
                instantVelocityX = 50f, instantVelocityY = -4f,
                scaleChangeFromPrev = 5.8f,
                headEulerY = -12f, headEulerX = 3f,
                confidence = 0.92f,
                isInterpolated = true,
                status = "INTERPOLATED_OCCLUDED"
            ),
            TrackFrame(
                timestampMs = 4500L,
                formattedTime = "00:04.5",
                boxLeft = 360f, boxTop = 45f, boxRight = 570f, boxBottom = 350f,
                speedPxPerSec = 55f,
                instantVelocityX = 50f, instantVelocityY = -3f,
                scaleChangeFromPrev = 5.1f,
                headEulerY = -16f, headEulerX = 5f,
                confidence = 0.95f
            )
        )

        val track1Trajectories = listOf(
            LandmarkTrajectory(
                landmarkName = "Nose",
                points = listOf(
                    TrajectoryPoint(0L, "00:00.0", 230f, 95f),
                    TrajectoryPoint(1500L, "00:01.5", 305f, 92f),
                    TrajectoryPoint(3000L, "00:03.0", 385f, 88f),
                    TrajectoryPoint(4500L, "00:04.5", 465f, 85f)
                ),
                totalPathLengthPx = 236f
            ),
            LandmarkTrajectory(
                landmarkName = "Right Wrist",
                points = listOf(
                    TrajectoryPoint(0L, "00:00.0", 290f, 210f),
                    TrajectoryPoint(1500L, "00:01.5", 380f, 195f),
                    TrajectoryPoint(3000L, "00:03.0", 470f, 220f),
                    TrajectoryPoint(4500L, "00:04.5", 550f, 205f)
                ),
                totalPathLengthPx = 278f
            ),
            LandmarkTrajectory(
                landmarkName = "Left Wrist",
                points = listOf(
                    TrajectoryPoint(0L, "00:00.0", 170f, 215f),
                    TrajectoryPoint(1500L, "00:01.5", 240f, 225f),
                    TrajectoryPoint(3000L, "00:03.0", 320f, 200f),
                    TrajectoryPoint(4500L, "00:04.5", 400f, 218f)
                ),
                totalPathLengthPx = 245f
            )
        )

        val track2Frames = listOf(
            TrackFrame(
                timestampMs = 1500L,
                formattedTime = "00:01.5",
                boxLeft = 490f, boxTop = 80f, boxRight = 610f, boxBottom = 330f,
                speedPxPerSec = 0f,
                confidence = 0.91f
            ),
            TrackFrame(
                timestampMs = 3000L,
                formattedTime = "00:03.0",
                boxLeft = 485f, boxTop = 82f, boxRight = 605f, boxBottom = 332f,
                speedPxPerSec = 4f,
                confidence = 0.90f
            ),
            TrackFrame(
                timestampMs = 4500L,
                formattedTime = "00:04.5",
                boxLeft = 480f, boxTop = 85f, boxRight = 600f, boxBottom = 335f,
                speedPxPerSec = 5f,
                confidence = 0.89f
            )
        )

        val track2Trajectories = listOf(
            LandmarkTrajectory(
                landmarkName = "Nose",
                points = listOf(
                    TrajectoryPoint(1500L, "00:01.5", 550f, 110f),
                    TrajectoryPoint(3000L, "00:03.0", 545f, 112f),
                    TrajectoryPoint(4500L, "00:04.5", 540f, 115f)
                ),
                totalPathLengthPx = 12f
            )
        )

        return listOf(
            PersonTrack(
                trackId = 1,
                label = "Track #1 (Spatial/Motion Associated)",
                firstSeenMs = 0L,
                lastSeenMs = 4500L,
                durationMs = 4500L,
                totalFramesTracked = 4,
                averageConfidence = 0.94f,
                motionDirection = "Moving Right (52 px/s)",
                averageSpeedPxPerSec = 52f,
                maxSpeedPxPerSec = 58f,
                netDisplacementX = 235f,
                netDisplacementY = -12f,
                scaleChangePercent = 17.5f,
                interruptionCount = 1,
                frames = track1Frames,
                trajectories = track1Trajectories
            ),
            PersonTrack(
                trackId = 2,
                label = "Track #2 (Spatial Associated)",
                firstSeenMs = 1500L,
                lastSeenMs = 4500L,
                durationMs = 3000L,
                totalFramesTracked = 3,
                averageConfidence = 0.90f,
                motionDirection = "Stationary / In-Place (5 px/s)",
                averageSpeedPxPerSec = 4.5f,
                maxSpeedPxPerSec = 5f,
                netDisplacementX = -10f,
                netDisplacementY = 5f,
                scaleChangePercent = 1.2f,
                interruptionCount = 0,
                frames = track2Frames,
                trajectories = track2Trajectories
            )
        )
    }

    private fun createBenchmarkDemoFrames(): List<FrameTrackingResult> {
        val landmarks1 = listOf(
            PoseLandmarkItem("Nose", PointF2D(230f, 95f), 0.98f),
            PoseLandmarkItem("Left Shoulder", PointF2D(200f, 130f), 0.96f),
            PoseLandmarkItem("Right Shoulder", PointF2D(260f, 130f), 0.96f),
            PoseLandmarkItem("Left Elbow", PointF2D(180f, 175f), 0.92f),
            PoseLandmarkItem("Right Elbow", PointF2D(280f, 175f), 0.92f),
            PoseLandmarkItem("Left Wrist", PointF2D(170f, 215f), 0.90f),
            PoseLandmarkItem("Right Wrist", PointF2D(290f, 210f), 0.91f),
            PoseLandmarkItem("Left Hip", PointF2D(210f, 220f), 0.94f),
            PoseLandmarkItem("Right Hip", PointF2D(250f, 220f), 0.94f),
            PoseLandmarkItem("Left Knee", PointF2D(205f, 280f), 0.91f),
            PoseLandmarkItem("Right Knee", PointF2D(255f, 280f), 0.91f),
            PoseLandmarkItem("Left Ankle", PointF2D(200f, 335f), 0.88f),
            PoseLandmarkItem("Right Ankle", PointF2D(260f, 335f), 0.89f)
        )

        return listOf(
            FrameTrackingResult(
                timestampMs = 0L,
                formattedTime = "00:00.0",
                frameWidth = 640,
                frameHeight = 360,
                thumbnailPath = null,
                detectedFaces = listOf(
                    DetectedFaceTrack(
                        trackingId = 101,
                        boxLeft = 205f, boxTop = 68f, boxRight = 255f, boxBottom = 122f,
                        headEulerAngleX = 2f, headEulerAngleY = -4f, headEulerAngleZ = 0f,
                        confidence = 0.96f
                    )
                ),
                detectedPersons = listOf(
                    DetectedPersonTrack(
                        personId = 1,
                        boxLeft = 140f, boxTop = 60f, boxRight = 320f, boxBottom = 340f,
                        landmarks = landmarks1,
                        confidence = 0.96f,
                        status = "DETECTED",
                        speedPxPerSec = 0f
                    )
                )
            ),
            FrameTrackingResult(
                timestampMs = 1500L,
                formattedTime = "00:01.5",
                frameWidth = 640,
                frameHeight = 360,
                thumbnailPath = null,
                detectedFaces = listOf(
                    DetectedFaceTrack(
                        trackingId = 101,
                        boxLeft = 280f, boxTop = 65f, boxRight = 330f, boxBottom = 120f,
                        headEulerAngleX = 4f, headEulerAngleY = -8f, headEulerAngleZ = 0f,
                        confidence = 0.94f
                    )
                ),
                detectedPersons = listOf(
                    DetectedPersonTrack(
                        personId = 1,
                        boxLeft = 210f, boxTop = 55f, boxRight = 400f, boxBottom = 342f,
                        landmarks = landmarks1.map { it.copy(position = PointF2D(it.position.x + 75f, it.position.y)) },
                        confidence = 0.94f,
                        status = "DETECTED",
                        speedPxPerSec = 52f,
                        instantVelocityX = 46f,
                        instantVelocityY = -3f
                    ),
                    DetectedPersonTrack(
                        personId = 2,
                        boxLeft = 490f, boxTop = 80f, boxRight = 610f, boxBottom = 330f,
                        landmarks = emptyList(),
                        confidence = 0.91f,
                        status = "DETECTED",
                        speedPxPerSec = 0f
                    )
                )
            ),
            FrameTrackingResult(
                timestampMs = 3000L,
                formattedTime = "00:03.0",
                frameWidth = 640,
                frameHeight = 360,
                thumbnailPath = null,
                detectedFaces = emptyList(),
                detectedPersons = listOf(
                    DetectedPersonTrack(
                        personId = 1,
                        boxLeft = 285f, boxTop = 50f, boxRight = 485f, boxBottom = 345f,
                        landmarks = landmarks1.map { it.copy(position = PointF2D(it.position.x + 155f, it.position.y)) },
                        confidence = 0.92f,
                        status = "INTERPOLATED_OCCLUDED",
                        speedPxPerSec = 58f,
                        instantVelocityX = 50f,
                        instantVelocityY = -4f
                    ),
                    DetectedPersonTrack(
                        personId = 2,
                        boxLeft = 485f, boxTop = 82f, boxRight = 605f, boxBottom = 332f,
                        landmarks = emptyList(),
                        confidence = 0.90f,
                        status = "DETECTED",
                        speedPxPerSec = 4f
                    )
                )
            ),
            FrameTrackingResult(
                timestampMs = 4500L,
                formattedTime = "00:04.5",
                frameWidth = 640,
                frameHeight = 360,
                thumbnailPath = null,
                detectedFaces = listOf(
                    DetectedFaceTrack(
                        trackingId = 101,
                        boxLeft = 440f, boxTop = 58f, boxRight = 495f, boxBottom = 115f,
                        headEulerAngleX = 5f, headEulerAngleY = -16f, headEulerAngleZ = 0f,
                        confidence = 0.95f
                    )
                ),
                detectedPersons = listOf(
                    DetectedPersonTrack(
                        personId = 1,
                        boxLeft = 360f, boxTop = 45f, boxRight = 570f, boxBottom = 350f,
                        landmarks = landmarks1.map { it.copy(position = PointF2D(it.position.x + 235f, it.position.y)) },
                        confidence = 0.95f,
                        status = "DETECTED",
                        speedPxPerSec = 55f,
                        instantVelocityX = 50f,
                        instantVelocityY = -3f
                    ),
                    DetectedPersonTrack(
                        personId = 2,
                        boxLeft = 480f, boxTop = 85f, boxRight = 600f, boxBottom = 335f,
                        landmarks = emptyList(),
                        confidence = 0.89f,
                        status = "DETECTED",
                        speedPxPerSec = 5f
                    )
                )
            )
        )
    }

    private fun createBenchmarkDemoSegmentation(): SegmentationSummary {
        val frames = listOf(
            SegmentationFrame(
                timestampMs = 0L,
                formattedTime = "00:00.0",
                sourceFramePath = null,
                maskPath = null,
                foregroundCutoutPath = null,
                confidenceThreshold = 0.50f,
                featherRadius = 0.06f,
                metrics = MaskMetrics(
                    maskWidth = 640,
                    maskHeight = 360,
                    coveragePercent = 19.4f,
                    foregroundPixelCount = 44700,
                    boundingBoxLeft = 0.18f,
                    boundingBoxTop = 0.11f,
                    boundingBoxRight = 0.52f,
                    boundingBoxBottom = 0.94f,
                    averageConfidence = 0.94f
                ),
                associatedTrackIds = listOf(1)
            ),
            SegmentationFrame(
                timestampMs = 2250L,
                formattedTime = "00:02.2",
                sourceFramePath = null,
                maskPath = null,
                foregroundCutoutPath = null,
                confidenceThreshold = 0.50f,
                featherRadius = 0.06f,
                metrics = MaskMetrics(
                    maskWidth = 640,
                    maskHeight = 360,
                    coveragePercent = 28.2f,
                    foregroundPixelCount = 64972,
                    boundingBoxLeft = 0.22f,
                    boundingBoxTop = 0.10f,
                    boundingBoxRight = 0.88f,
                    boundingBoxBottom = 0.95f,
                    averageConfidence = 0.92f
                ),
                associatedTrackIds = listOf(1, 2)
            ),
            SegmentationFrame(
                timestampMs = 4500L,
                formattedTime = "00:04.5",
                sourceFramePath = null,
                maskPath = null,
                foregroundCutoutPath = null,
                confidenceThreshold = 0.50f,
                featherRadius = 0.06f,
                metrics = MaskMetrics(
                    maskWidth = 640,
                    maskHeight = 360,
                    coveragePercent = 21.6f,
                    foregroundPixelCount = 49766,
                    boundingBoxLeft = 0.52f,
                    boundingBoxTop = 0.12f,
                    boundingBoxRight = 0.92f,
                    boundingBoxBottom = 0.96f,
                    averageConfidence = 0.95f
                ),
                associatedTrackIds = listOf(1, 2)
            )
        )
        return SegmentationSummary(
            totalFramesSegmented = frames.size,
            averageCoveragePercent = 23.1f,
            modelType = "Google ML Kit Selfie Segmentation (Unified Foreground)",
            segmentationNotice = "ML Kit produces a unified foreground probability mask for all persons in frame. Model does not perform multi-instance person separation.",
            frames = frames
        )
    }
}
