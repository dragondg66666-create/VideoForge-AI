package com.videoforge.ai.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.videoforge.ai.model.SourceVideo
import com.videoforge.ai.ui.theme.DarkBorder
import com.videoforge.ai.ui.theme.DarkSurface
import com.videoforge.ai.ui.theme.NeonCyan
import com.videoforge.ai.ui.theme.NeonViolet
import com.videoforge.ai.ui.theme.TextPrimary
import com.videoforge.ai.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerView(
    sourceVideo: SourceVideo,
    targetSeekTimestampMs: Long? = null,
    onSeekHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember {
        mutableLongStateOf(
            if (sourceVideo.durationMs > 0) sourceVideo.durationMs
            else (sourceVideo.durationSeconds * 1000).toLong().coerceAtLeast(1000L)
        )
    }

    val isRealVideo = sourceVideo.uriString != null

    // Handle external seek requests (e.g. tapping on a detected scene keyframe)
    LaunchedEffect(targetSeekTimestampMs) {
        if (targetSeekTimestampMs != null) {
            val clamped = targetSeekTimestampMs.coerceIn(0L, durationMs)
            currentPositionMs = clamped
            if (isRealVideo && videoViewRef != null) {
                videoViewRef?.seekTo(clamped.toInt())
            }
            onSeekHandled()
        }
    }

    // Progress synchronization loop
    LaunchedEffect(isPlaying, isRealVideo) {
        while (isPlaying) {
            delay(100)
            if (isRealVideo && videoViewRef != null) {
                val realPos = videoViewRef?.currentPosition?.toLong() ?: 0L
                val realDur = videoViewRef?.duration?.toLong() ?: durationMs
                if (realDur > 0) durationMs = realDur
                currentPositionMs = realPos
                if (!videoViewRef!!.isPlaying && realPos >= durationMs - 200) {
                    isPlaying = false
                }
            } else if (!isRealVideo) {
                // Synthetic simulation for demo take
                currentPositionMs += 100L
                if (currentPositionMs >= durationMs) {
                    currentPositionMs = 0L
                    isPlaying = false
                }
            }
        }
    }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val progressFraction = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    val currentSeconds = currentPositionMs / 1000
    val totalSeconds = safeDuration / 1000
    val timeLabel = String.format(
        "%02d:%02d.%01d / %02d:%02d.%01d",
        (currentSeconds / 60) % 60, currentSeconds % 60, (currentPositionMs % 1000) / 100,
        (totalSeconds / 60) % 60, totalSeconds % 60, (safeDuration % 1000) / 100
    )
    val fps = if (sourceVideo.fps > 0) sourceVideo.fps else 24.0
    val currentFrame = (currentPositionMs * fps / 1000.0).toInt()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        color = DarkSurface
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0F172A), Color(0xFF070B14))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isRealVideo) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val uri = Uri.parse(sourceVideo.uriString)
                                setVideoURI(uri)
                                setOnPreparedListener { mp ->
                                    val actualDuration = mp.duration.toLong()
                                    if (actualDuration > 0) {
                                        durationMs = actualDuration
                                    }
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    seekTo(0)
                                    currentPositionMs = 0L
                                }
                                videoViewRef = this
                            }
                        },
                        update = { view ->
                            videoViewRef = view
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Studio synthetic preview canvas for sample footage
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF202A44), CircleShape)
                                .border(1.dp, NeonViolet, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = NeonCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = sourceVideo.filename,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${sourceVideo.resolution} · ${fps} FPS · Frame #$currentFrame",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }
                }

                // Overlay tag
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isRealVideo) "HARDWARE VIDEO DECODER" else "SAMPLE PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRealVideo) Color(0xFF35D69A) else Color(0xFF82C5FF),
                        fontSize = 9.sp
                    )
                }
            }

            // Controls Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131D2F))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Slider(
                    value = progressFraction,
                    onValueChange = { fraction ->
                        val targetMs = (fraction * safeDuration).toLong()
                        currentPositionMs = targetMs
                        if (isRealVideo && videoViewRef != null) {
                            videoViewRef?.seekTo(targetMs.toInt())
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = NeonViolet,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = Color(0xFF29354C)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .testTag("video_scrub_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (isRealVideo && videoViewRef != null) {
                                    if (videoViewRef!!.isPlaying) {
                                        videoViewRef!!.pause()
                                        isPlaying = false
                                    } else {
                                        videoViewRef!!.start()
                                        isPlaying = true
                                    }
                                } else {
                                    isPlaying = !isPlaying
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("play_pause_video_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Frame $currentFrame",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            color = NeonViolet
                        )
                    }
                }
            }
        }
    }
}
