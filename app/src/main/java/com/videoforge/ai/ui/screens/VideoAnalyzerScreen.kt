package com.videoforge.ai.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoforge.ai.model.DetectedScene
import com.videoforge.ai.model.FrameTrackingResult
import com.videoforge.ai.model.LandmarkTrajectory
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.model.Project
import com.videoforge.ai.model.TrackFrame
import com.videoforge.ai.model.TrackingSummary
import com.videoforge.ai.ui.AppTab
import com.videoforge.ai.ui.VideoForgeViewModel
import com.videoforge.ai.ui.components.NoticeBox
import com.videoforge.ai.ui.components.SectionKicker
import com.videoforge.ai.ui.components.SegmentationStudioView
import com.videoforge.ai.ui.components.StatusBadge
import com.videoforge.ai.ui.components.TrackingOverlayView
import com.videoforge.ai.ui.components.VideoPlayerView
import com.videoforge.ai.ui.theme.DarkBorder
import com.videoforge.ai.ui.theme.DarkSurface
import com.videoforge.ai.ui.theme.DarkSurfaceElevated
import com.videoforge.ai.ui.theme.DarkSurfaceVariant
import com.videoforge.ai.ui.theme.ErrorRed
import com.videoforge.ai.ui.theme.MintSuccess
import com.videoforge.ai.ui.theme.NeonCyan
import com.videoforge.ai.ui.theme.NeonViolet
import com.videoforge.ai.ui.theme.TextHighlight
import com.videoforge.ai.ui.theme.TextMuted
import com.videoforge.ai.ui.theme.TextPrimary
import com.videoforge.ai.ui.theme.TextSecondary
import java.io.File

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val tenths = (ms % 1000) / 100
    return String.format("%02d:%02d.%01d", min, sec, tenths)
}

private fun queryFileInfo(context: Context, uri: Uri): Pair<String, Long> {
    var name = "source_video.mp4"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return Pair(name, size)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoAnalyzerScreen(
    viewModel: VideoForgeViewModel,
    project: Project,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisProgress by viewModel.analysisProgress.collectAsState()
    val analysisDetail by viewModel.analysisDetail.collectAsState()
    val targetSeekMs by viewModel.targetSeekTimestampMs.collectAsState()
    val selectedTrackingTimestamp by viewModel.selectedTrackingFrameTimestamp.collectAsState()
    val selectedPersonId by viewModel.selectedPersonId.collectAsState()
    val selectedTrackId by viewModel.selectedTrackId.collectAsState()
    val selectedSegmentationTimestamp by viewModel.selectedSegmentationTimestamp.collectAsState()
    val segmentationDisplayMode by viewModel.segmentationDisplayMode.collectAsState()
    val maskConfidenceThreshold by viewModel.maskConfidenceThreshold.collectAsState()
    val edgeFeatherRadius by viewModel.edgeFeatherRadius.collectAsState()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, size) = queryFileInfo(context, uri)
            viewModel.setSourceVideoUri(
                uri = uri,
                filename = name,
                sizeBytes = if (size > 0) size else 15_000_000L
            )
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome / Section Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
            color = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionKicker("STEP 01 · SOURCE INGESTION & VISION")
                    StatusBadge(
                        text = if (project.analysis.status == "completed") "AI TRACKING READY"
                        else if (project.sourceVideo != null) "METADATA EXTRACTED"
                        else "AWAITING SOURCE",
                        isSuccess = project.analysis.status == "completed"
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Understand every frame.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "On-device frame histogram scene detection paired with Google ML Kit facial landmarks and body pose skeletal tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Source Video Display or Dropzone
        if (project.sourceVideo == null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp)),
                color = DarkSurfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFF2B2455), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload",
                            tint = NeonViolet,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Select a source video to analyze",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "MP4, MOV, MKV, WEBM or AVI · processed entirely on-device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("browse_video_button")
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Video File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.setDemoVideo() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            modifier = Modifier.testTag("use_demo_video_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use Sample Take", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // Video Player and Real Metadata Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                color = DarkSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    VideoPlayerView(
                        sourceVideo = project.sourceVideo,
                        targetSeekTimestampMs = targetSeekMs,
                        onSeekHandled = { viewModel.clearSeekTarget() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.sourceVideo.filename,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val durText = if (project.sourceVideo.durationMs > 0) {
                                formatMs(project.sourceVideo.durationMs)
                            } else {
                                String.format("%.1fs", project.sourceVideo.durationSeconds)
                            }
                            Text(
                                text = "${formatBytes(project.sourceVideo.sizeBytes)} · $durText · ${project.sourceVideo.resolution} · ${project.sourceVideo.fps} FPS",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.removeSourceVideo() },
                            modifier = Modifier.testTag("remove_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Video",
                                tint = Color(0xFFFF6B6B)
                            )
                        }
                    }
                }
            }
        }

        // Real AI Scene & Tracking Runner Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
            color = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionKicker("VISION INFERENCE PIPELINE")
                    StatusBadge(
                        text = when (project.analysis.status) {
                            "completed" -> "${project.analysis.uniquePersonsCount} PERFORMERS TRACKED"
                            "analyzing" -> "RUNNING INFERENCE"
                            "error" -> "ANALYSIS FAILED"
                            else -> "UNPROCESSED"
                        },
                        isSuccess = project.analysis.status == "completed"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "On-Device Scene Detection & ML Kit Tracking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Combines 48-bin color histogram cut detection with ML Kit Face (eyes, nose, mouth, Euler orientation) and Pose (13 skeletal joint tracks) processing.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // Ongoing Analysis Progress UI
                if (isAnalyzing) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { analysisProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .testTag("analysis_progress_bar"),
                        color = NeonCyan,
                        trackColor = Color(0xFF1F2B44)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (analysisDetail.isNotBlank()) analysisDetail else "Processing video...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            color = NeonCyan,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(analysisProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }
                }

                // Error State Display
                if (project.analysis.status == "error") {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFF381A1D),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6B2A30)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = project.analysis.message ?: "Failed to process video frames.",
                                color = Color(0xFFFFD0D3),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Completed Tracking & Scene Metrics
                if (project.analysis.status == "completed") {
                    Spacer(modifier = Modifier.height(14.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricChip("Performers: ${project.analysis.uniquePersonsCount}", MintSuccess)
                        MetricChip("Faces: ${project.analysis.uniqueFacesCount}", NeonCyan)
                        MetricChip("Scenes: ${project.analysis.detectedScenes.size}", TextHighlight)
                        MetricChip("Cuts: ${project.analysis.sceneCuts}", Color(0xFFFFB074))
                        MetricChip("Analyzed Frames: ${project.analysis.framesAnalyzed}", TextSecondary)
                        MetricChip("Motion: ${project.analysis.motionIntensity}", TextHighlight)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.startAnalysis() },
                    enabled = project.sourceVideo != null && !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_analysis_button")
                ) {
                    Icon(
                        imageVector = if (isAnalyzing) Icons.Default.Sync else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAnalyzing) "Processing ML Kit Inference…"
                        else if (project.analysis.status == "completed") "Re-run Scene & Performer Tracking"
                        else "Detect Scenes & Track Performers",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Phase 2.4: Temporal Motion Tracking & Trajectory Studio
        if (project.analysis.status == "completed" && project.analysis.trackingResults.isNotEmpty()) {
            val trackingResults = project.analysis.trackingResults
            val activeFrameResult = trackingResults.find { it.timestampMs == selectedTrackingTimestamp }
                ?: trackingResults.first()

            val personTracks = project.analysis.personTracks.ifEmpty {
                // Backward-compatibility: derive from detected persons if summary is empty
                val ids = activeFrameResult.detectedPersons.map { it.personId }.distinct()
                ids.map { id ->
                    PersonTrack(
                        trackId = id,
                        label = "Track #$id (Spatial Associated)",
                        firstSeenMs = activeFrameResult.timestampMs,
                        lastSeenMs = activeFrameResult.timestampMs,
                        durationMs = 0L,
                        totalFramesTracked = 1,
                        averageConfidence = 0.9f,
                        motionDirection = "Stationary",
                        averageSpeedPxPerSec = 0f
                    )
                }
            }

            val activeTrack = personTracks.find { it.trackId == (selectedTrackId ?: selectedPersonId) }
                ?: personTracks.firstOrNull()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                color = DarkSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            SectionKicker("PHASE 2.4 · TEMPORAL MOTION TRACKING")
                            Text(
                                text = "Motion Trajectory & Skeletal Rig",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF132B3E), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = activeFrameResult.formattedTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Timeline Selector Chips for Sampled Tracking Frames
                    Text(
                        text = "Sampled Timeline Keyframes",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val timelineScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(timelineScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        trackingResults.forEach { fr ->
                            val isSelected = fr.timestampMs == activeFrameResult.timestampMs
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectTrackingFrame(fr.timestampMs)
                                    viewModel.seekToTimestamp(fr.timestampMs)
                                },
                                label = {
                                    Text(
                                        text = "${fr.formattedTime} (${fr.detectedPersons.size}P/${fr.detectedFaces.size}F)",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF28224E),
                                    selectedLabelColor = TextHighlight,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) NeonViolet else DarkBorder
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("frame_chip_${fr.timestampMs}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Interactive Overlay View with Motion Trajectories & Velocity Vector
                    TrackingOverlayView(
                        trackingResult = activeFrameResult,
                        selectedPersonId = selectedTrackId ?: selectedPersonId,
                        selectedTrack = activeTrack,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Phase 2.4: Motion Tracking Summary & Track Selector
                    Text(
                        text = "Temporal Identity Association & Track Registry",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Local track association via bounding box IoU, spatial velocity prediction, and skeletal landmark similarity. Distinguishes local trajectory continuity from verified global identity.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Track selector chips
                    val trackScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(trackScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTrackId == null,
                            onClick = { viewModel.selectTrack(null) },
                            label = { Text("All Tracks (${personTracks.size})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonViolet,
                                selectedLabelColor = Color.White
                            )
                        )
                        personTracks.forEach { track ->
                            val isSel = track.trackId == (selectedTrackId ?: selectedPersonId)
                            val durSec = String.format("%.1fs", track.durationMs / 1000f)
                            FilterChip(
                                selected = isSel,
                                onClick = { viewModel.selectTrack(track.trackId) },
                                label = {
                                    Text(
                                        text = "Track #${track.trackId} ($durSec · ${track.motionDirection})",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isSel) NeonCyan else TextSecondary
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1E3547),
                                    selectedLabelColor = NeonCyan,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSel,
                                    borderColor = if (isSel) NeonCyan else DarkBorder
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("track_chip_${track.trackId}")
                            )
                        }
                    }

                    // Detailed Track Inspector
                    if (activeTrack != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF233B54), RoundedCornerShape(10.dp)),
                            color = Color(0xFF112236),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Track Title & Confidence
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Timeline,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Track #${activeTrack.trackId} Motion Profile",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                    StatusBadge(
                                        text = "${(activeTrack.averageConfidence * 100).toInt()}% CONFIDENCE",
                                        isSuccess = activeTrack.averageConfidence >= 0.8f
                                    )
                                }

                                // Key Motion Metrics Row
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MetricChip("Direction: ${activeTrack.motionDirection}", NeonCyan)
                                    MetricChip("Avg Speed: ${String.format("%.1f", activeTrack.averageSpeedPxPerSec)} px/s", MintSuccess)
                                    MetricChip("Peak Speed: ${String.format("%.1f", activeTrack.maxSpeedPxPerSec)} px/s", Color(0xFFFFE57F))
                                    MetricChip("Displacement: ΔX=${String.format("%+.0f", activeTrack.netDisplacementX)}px, ΔY=${String.format("%+.0f", activeTrack.netDisplacementY)}px", TextHighlight)
                                    val scaleLabel = when {
                                        activeTrack.scaleChangePercent > 12f -> "${String.format("%+.1f%%", activeTrack.scaleChangePercent)} (Approaching)"
                                        activeTrack.scaleChangePercent < -12f -> "${String.format("%+.1f%%", activeTrack.scaleChangePercent)} (Receding)"
                                        else -> "Constant Distance"
                                    }
                                    MetricChip("Scale: $scaleLabel", Color(0xFFFFA726))
                                }

                                // Temporal Span & Occlusion Recovery
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val firstTime = activeTrack.frames.firstOrNull()?.formattedTime ?: "00:00.0"
                                    val lastTime = activeTrack.frames.lastOrNull()?.formattedTime ?: "00:00.0"
                                    Text(
                                        text = "Span: $firstTime → $lastTime (${String.format("%.1f", activeTrack.durationMs / 1000f)}s, ${activeTrack.totalFramesTracked} frames)",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    if (activeTrack.interruptionCount > 0) {
                                        Text(
                                            text = "${activeTrack.interruptionCount} occlusion(s) recovered",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFFB300),
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = "Continuous track",
                                            fontSize = 11.sp,
                                            color = MintSuccess
                                        )
                                    }
                                }

                                // Key Landmark Trajectories Breakdown
                                if (activeTrack.trajectories.isNotEmpty()) {
                                    Text(
                                        text = "Joint Trajectories (Skeletal Motion Paths)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextHighlight
                                    )
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        activeTrack.trajectories.take(6).forEach { traj ->
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF1E2D44), RoundedCornerShape(6.dp))
                                                    .border(1.dp, Color(0xFF2C4368), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "${traj.landmarkName}: ${String.format("%.0f", traj.totalPathLengthPx)} px (${traj.points.size} pts)",
                                                    fontSize = 10.sp,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Track Keyframe Jump Strip
                                Text(
                                    text = "Tracked Keyframe Cadence (Tap to seek in video player):",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                                val stripScrollState = rememberScrollState()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(stripScrollState),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    activeTrack.frames.forEach { tf ->
                                        val isCurrent = tf.timestampMs == activeFrameResult.timestampMs
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isCurrent) NeonCyan
                                                    else if (tf.isInterpolated) Color(0xFF5A4010)
                                                    else Color(0xFF1A334B),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isCurrent) Color.White else if (tf.isInterpolated) Color(0xFFFFB300) else Color(0xFF2E537A),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable {
                                                    viewModel.seekToTimestamp(tf.timestampMs)
                                                    viewModel.selectTrackingFrame(tf.timestampMs)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = if (tf.isInterpolated) "${tf.formattedTime}*" else tf.formattedTime,
                                                fontSize = 9.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrent) Color(0xFF090D18) else TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Extracted Scene Cuts Section
        if (project.analysis.status == "completed" && project.analysis.detectedScenes.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                color = DarkSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            SectionKicker("SCENE TRANSITIONS")
                            Text(
                                text = "Detected Scene Cuts (${project.analysis.detectedScenes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Tap to jump in player",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            color = NeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        project.analysis.detectedScenes.forEach { scene ->
                            SceneKeyframeCard(
                                scene = scene,
                                onClick = {
                                    viewModel.seekToTimestamp(scene.timestampMs)
                                    viewModel.showToast("Jumped to ${scene.formattedTime} in video player")
                                }
                            )
                        }
                    }
                }
            }

            // Phase 2.5: Person Segmentation & Alpha Studio Card
            if (project.analysis.segmentation.frames.isNotEmpty()) {
                val activeSegFrame = project.analysis.segmentation.frames.find {
                    it.timestampMs == selectedSegmentationTimestamp
                } ?: project.analysis.segmentation.frames.first()

                SegmentationStudioView(
                    summary = project.analysis.segmentation,
                    selectedFrame = activeSegFrame,
                    displayMode = segmentationDisplayMode,
                    confidenceThreshold = maskConfidenceThreshold,
                    edgeFeatherRadius = edgeFeatherRadius,
                    onSelectFrame = { ts -> viewModel.selectSegmentationFrame(ts) },
                    onSetDisplayMode = { mode -> viewModel.setSegmentationDisplayMode(mode) },
                    onThresholdChange = { th -> viewModel.setMaskConfidenceThreshold(th) },
                    onFeatherChange = { fe -> viewModel.setEdgeFeatherRadius(fe) }
                )
            }
        }

        // Phase 2.6: Character Replacement & 2D Compositing Bridge Card
        if (project.analysis.status == "completed" && project.analysis.personTracks.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                color = DarkSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            SectionKicker("PHASE 2.6 · 2D CHARACTER COMPOSITING")
                            Text(
                                text = "Performer Replacement Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        StatusBadge(
                            text = if (project.replacementStudio.activeAsset != null) "READY TO COMPOSITE" else "AWAITING ASSET",
                            isSuccess = project.replacementStudio.activeAsset != null
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Genuine 2D alpha compositing using motion trajectories, temporal transform interpolation, transparent PNG validation, and mask-aware bounding protection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setTab(AppTab.STUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("launch_replacement_studio_button")
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Replacement Studio", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.loadSampleHeroAsset()
                                viewModel.setTab(AppTab.STUDIO)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_sample_hero_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load Sample Cutout", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Original-First Preservation Settings Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
            color = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                SectionKicker("PRESERVATION MATRIX")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Original-First Workflow Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Ensures original timing, audio stems, and background scenery remain strictly untouched.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Original Audio Stem", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Text("Preserve original dialogue, foley & music", style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp, color = TextMuted)
                    }
                    Switch(
                        checked = project.preservation.originalAudio,
                        onCheckedChange = { viewModel.updatePreservation(it, project.preservation.originalTiming, project.preservation.originalBackground) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = Color(0xFF1C3A5A)),
                        modifier = Modifier.testTag("toggle_original_audio")
                    )
                }

                HorizontalDivider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Original Frame Timing", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Text("Keep native keyframe durations and cadence", style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp, color = TextMuted)
                    }
                    Switch(
                        checked = project.preservation.originalTiming,
                        onCheckedChange = { viewModel.updatePreservation(project.preservation.originalAudio, it, project.preservation.originalBackground) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = Color(0xFF1C3A5A)),
                        modifier = Modifier.testTag("toggle_original_timing")
                    )
                }

                HorizontalDivider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Original Background Scenery", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Text("Mask out only character replacement bounding", style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp, color = TextMuted)
                    }
                    Switch(
                        checked = project.preservation.originalBackground,
                        onCheckedChange = { viewModel.updatePreservation(project.preservation.originalAudio, project.preservation.originalTiming, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = Color(0xFF1C3A5A)),
                        modifier = Modifier.testTag("toggle_original_background")
                    )
                }
            }
        }

        // Notice Banner
        NoticeBox(
            icon = Icons.Default.Security,
            text = "Phase 2.6 implements genuine 2D alpha compositing using motion trajectories, temporal transform interpolation, transparent PNG validation, and mask-aware bounding protection."
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun MetricChip(label: String, color: Color) {
    Surface(
        color = Color(0xFF15223A),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF283A5D))
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SceneKeyframeCard(
    scene: DetectedScene,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("scene_card_${scene.sceneIndex}"),
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Keyframe Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF263554), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (scene.thumbnailPath != null && File(scene.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(scene.thumbnailPath),
                        contentDescription = "Keyframe ${scene.sceneIndex}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = TextHighlight,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Scene ${String.format("%02d", scene.sceneIndex)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF143046), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = scene.formattedTime,
                            fontSize = 9.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (scene.diffScore > 0f) "Color Histogram Shift: ${(scene.diffScore * 100).toInt()}%" else "Initial Reference Keyframe",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = onClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Jump to scene",
                    tint = NeonViolet,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
