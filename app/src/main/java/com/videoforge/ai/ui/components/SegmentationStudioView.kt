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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import com.videoforge.ai.model.SegmentationFrame
import com.videoforge.ai.model.SegmentationSummary
import com.videoforge.ai.ui.theme.DarkBorder
import com.videoforge.ai.ui.theme.DarkSurface
import com.videoforge.ai.ui.theme.DarkSurfaceElevated
import com.videoforge.ai.ui.theme.DarkSurfaceVariant
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
 * Phase 2.5: Interactive Person Segmentation & Alpha Studio Composable
 *
 * Provides a production-grade inspection station for on-device alpha masks:
 * - 4 Display Modes: Composite Overlay, Monochrome Alpha Matte, Isolated Cutout, Source Frame.
 * - Dynamic checkerboard transparency viewport.
 * - Live confidence threshold & edge feathering sliders.
 * - Mask metrics: coverage %, bounding box, pixel count, and track intersection.
 * - Explicit model limitation notice regarding unified foreground vs instance separation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentationStudioView(
    summary: SegmentationSummary,
    selectedFrame: SegmentationFrame?,
    displayMode: String,
    confidenceThreshold: Float,
    edgeFeatherRadius: Float,
    onSelectFrame: (Long) -> Unit,
    onSetDisplayMode: (String) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onFeatherChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (summary.frames.isEmpty()) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
            color = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No Person Alpha Masks Generated Yet",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Execute vision inference to extract ML Kit on-device foreground masks and soft alpha mattes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    val activeFrame = selectedFrame ?: summary.frames.first()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
        color = DarkSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header with Model Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SectionKicker("PHASE 2.5 · PERSON SEGMENTATION")
                    Text(
                        text = "Alpha Matte & Cutout Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1B2E24), RoundedCornerShape(6.dp))
                        .border(1.dp, MintSuccess.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ML KIT SELFIE SEGMENTATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MintSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Honest Architectural Transparency Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131F33), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF263F6A), RoundedCornerShape(8.dp))
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
                        text = "Unified Foreground Probability Mask: ML Kit processes all humans in the frame into a single combined probability mask. It is not an individual instance segmenter; per-person masks are never synthesized.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = Color(0xFFBDD4F5),
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Timeline Keyframe Chips Row
            Text(
                text = "Sampled Video Frame Masks",
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
                summary.frames.forEach { frame ->
                    val isSelected = frame.timestampMs == activeFrame.timestampMs
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFrame(frame.timestampMs) },
                        label = {
                            Text(
                                text = "${frame.formattedTime} (${String.format(Locale.US, "%.1f", frame.metrics.coveragePercent)}% cov)",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF183B38),
                            selectedLabelColor = MintSuccess,
                            containerColor = DarkSurfaceVariant,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) MintSuccess else DarkBorder
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("seg_frame_chip_${frame.timestampMs}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Display Mode Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "OVERLAY" to "Composite Overlay",
                    "MASK" to "Binary Alpha Mask",
                    "CUTOUT" to "Foreground Cutout",
                    "SOURCE" to "Source Frame"
                ).forEach { (modeKey, label) ->
                    val isSelected = displayMode == modeKey
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
                            .clickable { onSetDisplayMode(modeKey) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .testTag("seg_mode_${modeKey.lowercase()}"),
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

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Alpha Matte Viewport with Checkerboard Background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF2C3955), RoundedCornerShape(10.dp))
                    .testTag("segmentation_viewport"),
                contentAlignment = Alignment.Center
            ) {
                // Checkerboard Canvas for transparency
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val squareSize = 16.dp.toPx()
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

                // Render image artifact or fallback canvas synthesis
                when (displayMode) {
                    "CUTOUT" -> {
                        if (activeFrame.foregroundCutoutPath != null && File(activeFrame.foregroundCutoutPath).exists()) {
                            AsyncImage(
                                model = File(activeFrame.foregroundCutoutPath),
                                contentDescription = "Foreground Cutout",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            FallbackCutoutCanvas(activeFrame, confidenceThreshold)
                        }
                    }
                    "MASK" -> {
                        if (activeFrame.maskPath != null && File(activeFrame.maskPath).exists()) {
                            AsyncImage(
                                model = File(activeFrame.maskPath),
                                contentDescription = "Monochrome Mask",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            FallbackMaskCanvas(activeFrame, confidenceThreshold)
                        }
                    }
                    "OVERLAY" -> {
                        if (activeFrame.sourceFramePath != null && File(activeFrame.sourceFramePath).exists()) {
                            AsyncImage(
                                model = File(activeFrame.sourceFramePath),
                                contentDescription = "Source Frame",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (activeFrame.maskPath != null && File(activeFrame.maskPath).exists()) {
                                AsyncImage(
                                    model = File(activeFrame.maskPath),
                                    contentDescription = "Mask Overlay",
                                    contentScale = ContentScale.Fit,
                                    alpha = 0.55f,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            FallbackOverlayCanvas(activeFrame, confidenceThreshold)
                        }
                    }
                    "SOURCE" -> {
                        if (activeFrame.sourceFramePath != null && File(activeFrame.sourceFramePath).exists()) {
                            AsyncImage(
                                model = File(activeFrame.sourceFramePath),
                                contentDescription = "Source Frame",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            FallbackSourceCanvas(activeFrame)
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
                        text = "${displayMode.replace('_', ' ')} · ${activeFrame.formattedTime}",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .background(Color(0xCC0B111E), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Coverage: ${String.format(Locale.US, "%.1f", activeFrame.metrics.coveragePercent)}%",
                        color = MintSuccess,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Configurable Threshold and Feather Controls
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF26334F), RoundedCornerShape(10.dp)),
                color = DarkSurfaceElevated,
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
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Foreground Probability Threshold",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2f", confidenceThreshold),
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Slider(
                        value = confidenceThreshold,
                        onValueChange = onThresholdChange,
                        valueRange = 0.10f..0.90f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = Color(0xFF1F2F4E)
                        ),
                        modifier = Modifier.testTag("threshold_slider")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Soft Edge Feather Smoothing",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "±%.2f", edgeFeatherRadius),
                            color = NeonViolet,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Slider(
                        value = edgeFeatherRadius,
                        onValueChange = onFeatherChange,
                        valueRange = 0.01f..0.15f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonViolet,
                            activeTrackColor = NeonViolet,
                            inactiveTrackColor = Color(0xFF1F2F4E)
                        ),
                        modifier = Modifier.testTag("feather_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Segmentation Telemetry Grid
            Text(
                text = "Segmentation Telemetry & Spatial Bounds",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatCard(
                    title = "Foreground Area",
                    value = "${String.format(Locale.US, "%.1f", activeFrame.metrics.coveragePercent)}%",
                    detail = "${activeFrame.metrics.foregroundPixelCount} pixels",
                    accentColor = MintSuccess
                )
                TelemetryStatCard(
                    title = "Mask Dimensions",
                    value = "${activeFrame.metrics.maskWidth} × ${activeFrame.metrics.maskHeight}",
                    detail = "Raw float buffer",
                    accentColor = NeonCyan
                )
                TelemetryStatCard(
                    title = "Mean Confidence",
                    value = "${String.format(Locale.US, "%.1f", activeFrame.metrics.averageConfidence * 100)}%",
                    detail = "Foreground pixels",
                    accentColor = NeonViolet
                )
                TelemetryStatCard(
                    title = "Bounding Box",
                    value = "[${String.format(Locale.US, "%.2f", activeFrame.metrics.boundingBoxLeft)}, ${String.format(Locale.US, "%.2f", activeFrame.metrics.boundingBoxTop)}]",
                    detail = "Norm: L/T to R/B",
                    accentColor = TextHighlight
                )
            }

            // Associated Motion Tracks Section
            if (activeFrame.associatedTrackIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Intersecting Motion Tracks:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    activeFrame.associatedTrackIds.forEach { trackId ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF261D45), RoundedCornerShape(4.dp))
                                .border(1.dp, NeonViolet.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Track #$trackId",
                                color = NeonViolet,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
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

// Fallback Canvas Renderers for Demo and Test Environments
@Composable
private fun FallbackCutoutCanvas(frame: SegmentationFrame, threshold: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val m = frame.metrics

        val l = m.boundingBoxLeft.coerceIn(0.1f, 0.9f) * w
        val t = m.boundingBoxTop.coerceIn(0.1f, 0.9f) * h
        val r = m.boundingBoxRight.coerceIn(0.1f, 0.9f) * w
        val b = m.boundingBoxBottom.coerceIn(0.1f, 0.9f) * h

        val actorWidth = r - l
        val actorHeight = b - t

        // Draw isolated actor silhouette with glowing cyan edges
        val path = Path().apply {
            moveTo(l + actorWidth * 0.5f, t)
            cubicTo(
                l + actorWidth * 0.7f, t + actorHeight * 0.15f,
                l + actorWidth * 0.85f, t + actorHeight * 0.4f,
                l + actorWidth * 0.75f, t + actorHeight * 0.6f
            )
            lineTo(l + actorWidth * 0.85f, b)
            lineTo(l + actorWidth * 0.55f, b)
            lineTo(l + actorWidth * 0.5f, t + actorHeight * 0.65f)
            lineTo(l + actorWidth * 0.45f, b)
            lineTo(l + actorWidth * 0.15f, b)
            lineTo(l + actorWidth * 0.25f, t + actorHeight * 0.6f)
            cubicTo(
                l + actorWidth * 0.15f, t + actorHeight * 0.4f,
                l + actorWidth * 0.3f, t + actorHeight * 0.15f,
                l + actorWidth * 0.5f, t
            )
            close()
        }

        drawPath(path, color = Color(0xFF1E3A5F), style = Fill)
        drawPath(path, color = NeonCyan.copy(alpha = 0.85f), style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun FallbackMaskCanvas(frame: SegmentationFrame, threshold: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Black background
        drawRect(Color.Black, size = size)

        val w = size.width
        val h = size.height
        val m = frame.metrics

        val l = m.boundingBoxLeft.coerceIn(0.1f, 0.9f) * w
        val t = m.boundingBoxTop.coerceIn(0.1f, 0.9f) * h
        val r = m.boundingBoxRight.coerceIn(0.1f, 0.9f) * w
        val b = m.boundingBoxBottom.coerceIn(0.1f, 0.9f) * h

        val actorWidth = r - l
        val actorHeight = b - t

        val path = Path().apply {
            moveTo(l + actorWidth * 0.5f, t)
            cubicTo(
                l + actorWidth * 0.7f, t + actorHeight * 0.15f,
                l + actorWidth * 0.85f, t + actorHeight * 0.4f,
                l + actorWidth * 0.75f, t + actorHeight * 0.6f
            )
            lineTo(l + actorWidth * 0.85f, b)
            lineTo(l + actorWidth * 0.55f, b)
            lineTo(l + actorWidth * 0.5f, t + actorHeight * 0.65f)
            lineTo(l + actorWidth * 0.45f, b)
            lineTo(l + actorWidth * 0.15f, b)
            lineTo(l + actorWidth * 0.25f, t + actorHeight * 0.6f)
            cubicTo(
                l + actorWidth * 0.15f, t + actorHeight * 0.4f,
                l + actorWidth * 0.3f, t + actorHeight * 0.15f,
                l + actorWidth * 0.5f, t
            )
            close()
        }

        // Pure white mask foreground
        drawPath(path, color = Color.White, style = Fill)
    }
}

@Composable
private fun FallbackOverlayCanvas(frame: SegmentationFrame, threshold: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Base background (dark cinematic room)
        drawRect(Color(0xFF090D16), size = size)

        val w = size.width
        val h = size.height
        val m = frame.metrics

        val l = m.boundingBoxLeft.coerceIn(0.1f, 0.9f) * w
        val t = m.boundingBoxTop.coerceIn(0.1f, 0.9f) * h
        val r = m.boundingBoxRight.coerceIn(0.1f, 0.9f) * w
        val b = m.boundingBoxBottom.coerceIn(0.1f, 0.9f) * h

        val actorWidth = r - l
        val actorHeight = b - t

        val path = Path().apply {
            moveTo(l + actorWidth * 0.5f, t)
            cubicTo(
                l + actorWidth * 0.7f, t + actorHeight * 0.15f,
                l + actorWidth * 0.85f, t + actorHeight * 0.4f,
                l + actorWidth * 0.75f, t + actorHeight * 0.6f
            )
            lineTo(l + actorWidth * 0.85f, b)
            lineTo(l + actorWidth * 0.55f, b)
            lineTo(l + actorWidth * 0.5f, t + actorHeight * 0.65f)
            lineTo(l + actorWidth * 0.45f, b)
            lineTo(l + actorWidth * 0.15f, b)
            lineTo(l + actorWidth * 0.25f, t + actorHeight * 0.6f)
            cubicTo(
                l + actorWidth * 0.15f, t + actorHeight * 0.4f,
                l + actorWidth * 0.3f, t + actorHeight * 0.15f,
                l + actorWidth * 0.5f, t
            )
            close()
        }

        // Tinted alpha overlay in Neon Cyan
        drawPath(path, color = NeonCyan.copy(alpha = 0.40f), style = Fill)
        drawPath(path, color = NeonCyan, style = Stroke(width = 2.dp.toPx()))

        // Bounding box overlay in MintSuccess
        drawRect(
            color = MintSuccess.copy(alpha = 0.6f),
            topLeft = Offset(l, t),
            size = Size(actorWidth, actorHeight),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
private fun FallbackSourceCanvas(frame: SegmentationFrame) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color(0xFF0F1726), size = size)
        // Draw subtle stage lighting circles
        drawCircle(
            color = Color(0xFF19253C),
            radius = size.width * 0.35f,
            center = Offset(size.width * 0.5f, size.height * 0.45f)
        )
    }
}
