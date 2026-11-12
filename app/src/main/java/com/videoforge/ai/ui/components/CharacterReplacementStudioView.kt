package com.videoforge.ai.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoforge.ai.model.CompositedPreviewFrame
import com.videoforge.ai.model.ExportProgress
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.model.ReplacementAsset
import com.videoforge.ai.model.VideoExportConfig
import com.videoforge.ai.model.VideoExportResult
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
import java.util.Locale

/**
 * Phase 2.6 & 2.7: Interactive Character Replacement & MP4 Export Studio Composable
 *
 * Provides a genuine 2D alpha compositing workstation and native MP4 export engine:
 * - Target performer track selector.
 * - Transparent PNG character asset importer & validation inspector.
 * - 4 Preview Modes: Composited, Side-by-Side Split Wipe, Isolated Layer, Original Frame.
 * - Interactive fine-tuning sliders (scale, position offset, rotation trim, opacity).
 * - Multi-timestamp timeline scrubbing and transform telemetry.
 * - Mask-aware blending controls with unified segmentation warning disclosure.
 * - Native Android MP4 video rendering with MediaCodec, MediaMuxer, and audio passthrough.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterReplacementStudioView(
    tracks: List<PersonTrack>,
    selectedTrackId: Int?,
    activeAsset: ReplacementAsset?,
    previewFrame: CompositedPreviewFrame?,
    replacementEnabled: Boolean,
    userScale: Float,
    userOffsetX: Float,
    userOffsetY: Float,
    userRotationTrim: Float,
    userOpacity: Float,
    maskAwareBlending: Boolean,
    previewMode: String,
    splitRatio: Float,
    currentTimestampMs: Long,
    isCompositing: Boolean,
    onSelectTrack: (Int) -> Unit,
    onImportAssetClick: () -> Unit,
    onLoadSampleAsset: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateTransform: (scale: Float, offsetX: Float, offsetY: Float, rotTrim: Float, opacity: Float, maskAware: Boolean) -> Unit,
    onSetPreviewMode: (String) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onSelectTimestamp: (Long) -> Unit,
    // Phase 2.7 Export parameters
    isExporting: Boolean = false,
    exportProgress: ExportProgress = ExportProgress(),
    lastExportResult: VideoExportResult? = null,
    exportConfig: VideoExportConfig = VideoExportConfig(),
    onStartExport: () -> Unit = {},
    onCancelExport: () -> Unit = {},
    onUpdateExportConfig: (VideoExportConfig) -> Unit = {},
    onDismissExportResult: () -> Unit = {},
    onSaveExportClick: (String) -> Unit = {},
    onShareExport: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
        color = DarkSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header with Enabled Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SectionKicker("PHASE 2.6 · CHARACTER REPLACEMENT STUDIO")
                    Text(
                        text = "2D Alpha Compositing Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (replacementEnabled) "COMPOSITING ACTIVE" else "LAYER MUTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (replacementEnabled) MintSuccess else TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = replacementEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = Color(0xFF16344E)
                        ),
                        modifier = Modifier.testTag("toggle_replacement_layer")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Architectural Reality & Capability Notice
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131D31), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF263C66), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "2D Matrix Compositing Engine: Fits transparent character assets to target performer motion tracks using position, height-matching scale, and roll rotation. Does not perform 3D skeletal deformation or neural character generation.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = Color(0xFFBCD5F5),
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 1: Target Performer Track Selector
            Text(
                text = "1. Select Target Performer Track",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (tracks.isEmpty()) {
                Text(
                    text = "No motion tracks detected yet. Run Vision Inference on source video first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            } else {
                val trackScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(trackScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tracks.forEach { track ->
                        val isSelected = track.trackId == (selectedTrackId ?: tracks.first().trackId)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectTrack(track.trackId) },
                            label = {
                                Text(
                                    text = "Track #${track.trackId} (${track.frames.size} frames · ${track.motionDirection})",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF261D45),
                                selectedLabelColor = NeonViolet,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) NeonViolet else DarkBorder
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("replacement_track_chip_${track.trackId}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Replacement Character Asset Input & Validation
            Text(
                text = "2. Replacement Character Asset (Transparent PNG)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF26334F), RoundedCornerShape(10.dp)),
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (activeAsset != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Asset Thumbnail on Checkerboard
                            Box(
                                modifier = Modifier
                                    .size(60.dp, 80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF2C3955), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CheckerboardBackground()
                                if (activeAsset.localCachedPath != null && File(activeAsset.localCachedPath).exists()) {
                                    AsyncImage(
                                        model = File(activeAsset.localCachedPath),
                                        contentDescription = "Replacement Asset",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeAsset.filename,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${activeAsset.width} × ${activeAsset.height} px · ARGB_8888",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                // Validation Badge
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (activeAsset.isFullyOpaqueWarning) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB074),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Opaque Image Warning: 0% transparent pixels",
                                            color = Color(0xFFFFB074),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MintSuccess,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Valid Cutout (${String.format(Locale.US, "%.1f", activeAsset.transparentPixelRatio)}% alpha background)",
                                            color = MintSuccess,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No transparent character asset loaded yet. Select a transparent PNG or load the built-in sample asset.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onImportAssetClick,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("browse_character_png_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Transparent PNG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onLoadSampleAsset,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("load_sample_hero_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use Sample Hero Asset", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 3: Compositing Preview Viewport
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3. Composited Viewport Preview",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (isCompositing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Transforming…", fontSize = 10.sp, color = NeonCyan)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Mode switcher buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "COMPOSITED" to "Full Composite",
                    "SIDE_BY_SIDE" to "Split Wipe",
                    "LAYER_ONLY" to "Layer Cutout",
                    "ORIGINAL" to "Original Source"
                ).forEach { (modeKey, label) ->
                    val isSelected = previewMode == modeKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF232D48) else DarkSurfaceVariant)
                            .border(
                                1.dp,
                                if (isSelected) NeonCyan else DarkBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSetPreviewMode(modeKey) }
                            .padding(vertical = 7.dp, horizontal = 4.dp)
                            .testTag("preview_mode_${modeKey.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextHighlight else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Preview Canvas Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF2C3955), RoundedCornerShape(10.dp))
                    .testTag("replacement_viewport"),
                contentAlignment = Alignment.Center
            ) {
                // If showing layer only, show checkerboard
                if (previewMode == "LAYER_ONLY") {
                    CheckerboardBackground()
                }

                // Render Preview Image
                val frame = previewFrame
                when (previewMode) {
                    "COMPOSITED" -> {
                        if (frame?.compositedFramePath != null && File(frame.compositedFramePath).exists() && replacementEnabled) {
                            AsyncImage(
                                model = File(frame.compositedFramePath),
                                contentDescription = "Composited Frame",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (frame?.sourceFramePath != null && File(frame.sourceFramePath).exists()) {
                            AsyncImage(
                                model = File(frame.sourceFramePath),
                                contentDescription = "Source Frame",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            FallbackCompositeCanvas(frame, activeAsset, replacementEnabled)
                        }
                    }
                    "SIDE_BY_SIDE" -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Left: Original
                            if (frame?.sourceFramePath != null && File(frame.sourceFramePath).exists()) {
                                AsyncImage(
                                    model = File(frame.sourceFramePath),
                                    contentDescription = "Original Side",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                FallbackCompositeCanvas(frame, null, false)
                            }

                            // Right: Composited overlay with split wipe line
                            if (frame?.compositedFramePath != null && File(frame.compositedFramePath).exists() && replacementEnabled) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = (splitRatio * 320).dp)
                                        .clip(RoundedCornerShape(0.dp))
                                ) {
                                    AsyncImage(
                                        model = File(frame.compositedFramePath),
                                        contentDescription = "Composited Side",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // Vertical Divider Line at splitRatio
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val splitX = size.width * splitRatio
                                drawLine(
                                    color = NeonCyan,
                                    start = Offset(splitX, 0f),
                                    end = Offset(splitX, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                    }
                    "LAYER_ONLY" -> {
                        if (frame?.layerOnlyPath != null && File(frame.layerOnlyPath).exists()) {
                            AsyncImage(
                                model = File(frame.layerOnlyPath),
                                contentDescription = "Replacement Layer Only",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            FallbackLayerCanvas(frame, activeAsset)
                        }
                    }
                    "ORIGINAL" -> {
                        if (frame?.sourceFramePath != null && File(frame.sourceFramePath).exists()) {
                            AsyncImage(
                                model = File(frame.sourceFramePath),
                                contentDescription = "Original Source Frame",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            FallbackCompositeCanvas(frame, null, false)
                        }
                    }
                }

                // Floating Corner Badges
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(Color(0xCC0B111E), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${previewMode.replace('_', ' ')} · ${previewFrame?.formattedTime ?: "00:00.0"}",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (previewFrame?.transform?.isOccluded == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color(0xCC3E2413), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFFFB074), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Track Interpolated (Occlusion)",
                            color = Color(0xFFFFB074),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Split Wipe Slider (Only in SIDE_BY_SIDE mode)
            if (previewMode == "SIDE_BY_SIDE") {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Original (Left)", fontSize = 10.sp, color = TextMuted)
                    Text("Split Wipe: ${(splitRatio * 100).toInt()}%", fontSize = 10.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                    Text("Composited (Right)", fontSize = 10.sp, color = TextMuted)
                }
                Slider(
                    value = splitRatio,
                    onValueChange = onSetSplitRatio,
                    valueRange = 0.05f..0.95f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = Color(0xFF1F2F4E)
                    ),
                    modifier = Modifier.testTag("split_wipe_slider")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step 4: Sampled Timeline Keyframe Selector
            Text(
                text = "4. Temporal Keyframe Timeline",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            val timelineTimestamps = listOf(0L, 1000L, 2000L, 2250L, 3000L, 4500L)
            val timelineScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(timelineScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timelineTimestamps.forEach { ts ->
                    val isSelected = ts == currentTimestampMs
                    val totalSec = ts / 1000
                    val label = String.format(Locale.US, "%02d:%02d.%01d", totalSec / 60, totalSec % 60, (ts % 1000) / 100)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectTimestamp(ts) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF16324D),
                            selectedLabelColor = NeonCyan,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) NeonCyan else DarkBorder
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("replacement_time_chip_$ts")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 5: Interactive Transform Fine-Tuning Controls
            Text(
                text = "5. Transform Matrix & Blending Calibration",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF26334F), RoundedCornerShape(10.dp)),
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Scale Multiplier
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Scale Multiplier", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("${String.format(Locale.US, "%.2f", userScale)}x", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = userScale,
                        onValueChange = { onUpdateTransform(it, userOffsetX, userOffsetY, userRotationTrim, userOpacity, maskAwareBlending) },
                        valueRange = 0.50f..2.00f,
                        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan, inactiveTrackColor = Color(0xFF1F2F4E)),
                        modifier = Modifier.testTag("scale_slider")
                    )

                    // Horizontal Offset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Horizontal Position Offset (ΔX)", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("${userOffsetX.toInt()} px", fontSize = 11.sp, color = NeonViolet, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = userOffsetX,
                        onValueChange = { onUpdateTransform(userScale, it, userOffsetY, userRotationTrim, userOpacity, maskAwareBlending) },
                        valueRange = -100f..100f,
                        colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet, inactiveTrackColor = Color(0xFF1F2F4E)),
                        modifier = Modifier.testTag("offset_x_slider")
                    )

                    // Vertical Offset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Vertical Position Offset (ΔY)", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("${userOffsetY.toInt()} px", fontSize = 11.sp, color = NeonViolet, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = userOffsetY,
                        onValueChange = { onUpdateTransform(userScale, userOffsetX, it, userRotationTrim, userOpacity, maskAwareBlending) },
                        valueRange = -100f..100f,
                        colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet, inactiveTrackColor = Color(0xFF1F2F4E)),
                        modifier = Modifier.testTag("offset_y_slider")
                    )

                    // Rotation Trim
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rotation Trim (ΔRoll)", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("${userRotationTrim.toInt()}°", fontSize = 11.sp, color = TextHighlight, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = userRotationTrim,
                        onValueChange = { onUpdateTransform(userScale, userOffsetX, userOffsetY, it, userOpacity, maskAwareBlending) },
                        valueRange = -30f..30f,
                        colors = SliderDefaults.colors(thumbColor = TextHighlight, activeTrackColor = TextHighlight, inactiveTrackColor = Color(0xFF1F2F4E)),
                        modifier = Modifier.testTag("rotation_trim_slider")
                    )

                    // Opacity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Asset Alpha Opacity", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("${(userOpacity * 100).toInt()}%", fontSize = 11.sp, color = MintSuccess, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Slider(
                        value = userOpacity,
                        onValueChange = { onUpdateTransform(userScale, userOffsetX, userOffsetY, userRotationTrim, it, maskAwareBlending) },
                        valueRange = 0.10f..1.00f,
                        colors = SliderDefaults.colors(thumbColor = MintSuccess, activeTrackColor = MintSuccess, inactiveTrackColor = Color(0xFF1F2F4E)),
                        modifier = Modifier.testTag("opacity_slider")
                    )

                    // Mask-Aware Clipping Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mask-Aware Bounding Clipping", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Constrain compositing to track bounds to protect adjacent performers", fontSize = 10.sp, color = TextMuted)
                        }
                        Switch(
                            checked = maskAwareBlending,
                            onCheckedChange = { onUpdateTransform(userScale, userOffsetX, userOffsetY, userRotationTrim, userOpacity, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = Color(0xFF16344E)),
                            modifier = Modifier.testTag("toggle_mask_aware")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step 6: Transform Telemetry & Mathematical Diagnostics
            Text(
                text = "6. Spatial Transform Telemetry",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val tf = previewFrame?.transform
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatCard(
                    title = "Target Anchor",
                    value = "(${tf?.anchorX?.toInt() ?: 0}, ${tf?.anchorY?.toInt() ?: 0})",
                    detail = "Feet bottom-center px",
                    accentColor = NeonCyan
                )
                TelemetryStatCard(
                    title = "Target Size",
                    value = "${tf?.targetWidth?.toInt() ?: 0} × ${tf?.targetHeight?.toInt() ?: 0}",
                    detail = "Track bounding px",
                    accentColor = NeonViolet
                )
                TelemetryStatCard(
                    title = "Computed Rotation",
                    value = "${String.format(Locale.US, "%.1f", tf?.rotationDeg ?: 0f)}°",
                    detail = "Roll tilt + trim",
                    accentColor = TextHighlight
                )
                TelemetryStatCard(
                    title = "Track Status",
                    value = tf?.trackStatus ?: "ACTIVE",
                    detail = if (tf?.isOccluded == true) "Interpolated" else "High confidence",
                    accentColor = if (tf?.isOccluded == true) Color(0xFFFFB074) else MintSuccess
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 7. Phase 2.7: Native MP4 Rendering & Export Studio
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF26334F), RoundedCornerShape(12.dp)),
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            SectionKicker("PHASE 2.7 · NATIVE VIDEO EXPORT")
                            Text(
                                text = "Render & Export MP4 Video",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1B283E)
                        ) {
                            Text(
                                text = "H.264 / AAC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quality & Resolution Presets
                    Text(
                        text = "Output Quality & Resolution",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            Triple("HIGH", "1080p · 8 Mbps", Pair(1920, 1080)),
                            Triple("BALANCED", "720p · 4 Mbps", Pair(1280, 720)),
                            Triple("DRAFT", "540p · 2 Mbps", Pair(960, 540))
                        )
                        presets.forEach { (presetKey, label, dims) ->
                            val isSelected = exportConfig.qualityPreset == presetKey
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val bitrate = when (presetKey) {
                                        "HIGH" -> 8_000_000
                                        "BALANCED" -> 4_000_000
                                        else -> 2_000_000
                                    }
                                    onUpdateExportConfig(
                                        exportConfig.copy(
                                            qualityPreset = presetKey,
                                            outputWidth = dims.first,
                                            outputHeight = dims.second,
                                            bitrate = bitrate
                                        )
                                    )
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1A3554),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Framerate & Audio Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(24, 30, 60).forEach { fps ->
                                val isSelected = exportConfig.targetFps == fps
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onUpdateExportConfig(exportConfig.copy(targetFps = fps)) },
                                    label = { Text("${fps}fps", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF1A3554),
                                        selectedLabelColor = NeonCyan
                                    )
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = if (exportConfig.preserveAudio) NeonCyan else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Audio Track",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = exportConfig.preserveAudio,
                                onCheckedChange = { onUpdateExportConfig(exportConfig.copy(preserveAudio = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonCyan,
                                    checkedTrackColor = Color(0xFF1B3D5E)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Export Progress State
                    if (isExporting) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                            color = Color(0xFF0F1E33),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = NeonCyan,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "EXPORTING MP4",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan
                                        )
                                    }
                                    Text(
                                        text = "${(exportProgress.progress * 100f).toInt()}%",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextHighlight
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { exportProgress.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = NeonCyan,
                                    trackColor = Color(0xFF182A44)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = exportProgress.statusMessage.ifBlank { "Rendering frames…" },
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (exportProgress.estimatedRemainingSeconds != null && exportProgress.estimatedRemainingSeconds > 0) {
                                        Text(
                                            text = "~${exportProgress.estimatedRemainingSeconds}s remaining",
                                            fontSize = 11.sp,
                                            color = NeonViolet,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = onCancelExport,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("button_cancel_export"),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel Video Export", color = ErrorRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Completed Export Result Card
                    if (lastExportResult != null && !isExporting) {
                        val res = lastExportResult
                        if (res.isSuccess) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MintSuccess.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                                color = Color(0xFF0C241E),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MintSuccess,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "MP4 Video Ready",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MintSuccess
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = TextMuted,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { onDismissExportResult() }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${res.width}×${res.height} · ${res.renderedFramesCount} frames · ${String.format(Locale.US, "%.1f", res.fileSizeBytes / 1024f / 1024f)} MB",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    if (res.audioNotice != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = res.audioNotice,
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { res.outputPath?.let { onSaveExportClick(it) } },
                                            colors = ButtonDefaults.buttonColors(containerColor = MintSuccess, contentColor = Color(0xFF061510)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("button_save_export_saf")
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Save to Device", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = { res.outputPath?.let { onShareExport(it) } },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("button_share_export"),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MintSuccess)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, tint = MintSuccess, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Share Video", color = MintSuccess, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ErrorRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                                color = Color(0xFF291016),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Export Error", fontWeight = FontWeight.Bold, color = ErrorRed, fontSize = 12.sp)
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = TextMuted,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onDismissExportResult() }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = res.errorMessage ?: "Unknown rendering failure",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Main Export Button (when idle)
                    if (!isExporting) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onStartExport,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color(0xFF09121F)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("button_export_mp4")
                        ) {
                            Icon(imageVector = Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export MP4 Video (${exportConfig.qualityPreset})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Renders chronological frames at source timestamps with feet-anchored character replacement overlays, preserving original video timing and audio stream.",
                            fontSize = 10.sp,
                            color = TextMuted,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckerboardBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val squareSize = 14.dp.toPx()
        val cols = (size.width / squareSize).toInt() + 1
        val rows = (size.height / squareSize).toInt() + 1

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val isEven = (r + c) % 2 == 0
                val color = if (isEven) Color(0xFF0F1726) else Color(0xFF141F33)
                drawRect(
                    color = color,
                    topLeft = Offset(c * squareSize, r * squareSize),
                    size = Size(squareSize, squareSize)
                )
            }
        }
    }
}

@Composable
private fun TelemetryStatCard(
    title: String,
    value: String,
    detail: String,
    accentColor: Color
) {
    Surface(
        modifier = Modifier
            .width(155.dp)
            .border(1.dp, Color(0xFF26334F), RoundedCornerShape(8.dp)),
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

// Fallback Canvas Renderers for test and benchmark previews
@Composable
private fun FallbackCompositeCanvas(
    frame: CompositedPreviewFrame?,
    asset: ReplacementAsset?,
    enabled: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Stage background
        drawRect(Color(0xFF0B111D), size = size)
        drawRect(
            color = Color(0xFF141F32),
            topLeft = Offset(0f, size.height * 0.75f),
            size = Size(size.width, size.height * 0.25f)
        )
        drawCircle(
            color = Color(0xFF1B283E),
            radius = size.width * 0.32f,
            center = Offset(size.width * 0.5f, size.height * 0.45f)
        )

        // If replacement is enabled, draw stylized replacement figure
        if (enabled && asset != null) {
            val tf = frame?.transform
            val anchorX = tf?.anchorX?.coerceIn(0.1f, 0.9f * size.width) ?: (size.width * 0.45f)
            val anchorY = tf?.anchorY?.coerceIn(0.2f, 0.95f * size.height) ?: (size.height * 0.85f)
            val charW = (tf?.targetWidth ?: 120f) * (tf?.userScaleMultiplier ?: 1f)
            val charH = (tf?.targetHeight ?: 220f) * (tf?.userScaleMultiplier ?: 1f)

            val left = anchorX - (charW * 0.5f)
            val top = anchorY - charH

            val path = Path().apply {
                moveTo(anchorX, top)
                lineTo(anchorX + charW * 0.35f, top + charH * 0.35f)
                lineTo(anchorX + charW * 0.25f, anchorY)
                lineTo(anchorX - charW * 0.25f, anchorY)
                lineTo(anchorX - charW * 0.35f, top + charH * 0.35f)
                close()
            }

            drawPath(path, color = Color(0xFF1B2B44), style = Fill)
            drawPath(path, color = NeonCyan, style = Stroke(width = 3.dp.toPx()))

            // Draw visor glow
            drawCircle(
                color = NeonCyan,
                radius = charW * 0.08f,
                center = Offset(anchorX, top + charH * 0.12f)
            )
        }
    }
}

@Composable
private fun FallbackLayerCanvas(
    frame: CompositedPreviewFrame?,
    asset: ReplacementAsset?
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val tf = frame?.transform
        val anchorX = tf?.anchorX?.coerceIn(0.1f, 0.9f * size.width) ?: (size.width * 0.5f)
        val anchorY = tf?.anchorY?.coerceIn(0.2f, 0.95f * size.height) ?: (size.height * 0.85f)
        val charW = (tf?.targetWidth ?: 120f) * (tf?.userScaleMultiplier ?: 1f)
        val charH = (tf?.targetHeight ?: 220f) * (tf?.userScaleMultiplier ?: 1f)

        val left = anchorX - (charW * 0.5f)
        val top = anchorY - charH

        val path = Path().apply {
            moveTo(anchorX, top)
            lineTo(anchorX + charW * 0.35f, top + charH * 0.35f)
            lineTo(anchorX + charW * 0.25f, anchorY)
            lineTo(anchorX - charW * 0.25f, anchorY)
            lineTo(anchorX - charW * 0.35f, top + charH * 0.35f)
            close()
        }

        drawPath(path, color = Color(0xFF1B2B44), style = Fill)
        drawPath(path, color = NeonCyan, style = Stroke(width = 3.dp.toPx()))
    }
}
