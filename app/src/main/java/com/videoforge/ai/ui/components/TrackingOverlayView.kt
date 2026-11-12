package com.videoforge.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videoforge.ai.model.FrameTrackingResult
import com.videoforge.ai.model.PersonTrack
import com.videoforge.ai.ui.theme.DarkBorder
import com.videoforge.ai.ui.theme.MintSuccess
import com.videoforge.ai.ui.theme.NeonCyan
import com.videoforge.ai.ui.theme.NeonViolet
import com.videoforge.ai.ui.theme.TextHighlight
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun TrackingOverlayView(
    trackingResult: FrameTrackingResult,
    selectedPersonId: Int? = null,
    selectedTrack: PersonTrack? = null,
    modifier: Modifier = Modifier
) {
    val fw = trackingResult.frameWidth.coerceAtLeast(1).toFloat()
    val fh = trackingResult.frameHeight.coerceAtLeast(1).toFloat()
    val aspectRatio = fw / fh

    SurfaceContainer(
        aspectRatio = aspectRatio,
        modifier = modifier
    ) {
        // 1. Base keyframe image
        if (trackingResult.thumbnailPath != null && File(trackingResult.thumbnailPath).exists()) {
            AsyncImage(
                model = File(trackingResult.thumbnailPath),
                contentDescription = "Tracking frame at ${trackingResult.formattedTime}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = TextHighlight
                )
            }
        }

        // 2. ML Kit Detection & Motion Trajectory Overlay Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val scaleX = canvasW / fw
            val scaleY = canvasH / fh

            // Draw Historical Motion Trajectory Path for the Selected Track
            if (selectedTrack != null && selectedTrack.frames.size > 1) {
                val trajectoryPath = Path()
                var first = true

                selectedTrack.frames.forEach { frame ->
                    val px = frame.centerX * scaleX
                    val py = frame.centerY * scaleY

                    if (first) {
                        trajectoryPath.moveTo(px, py)
                        first = false
                    } else {
                        trajectoryPath.lineTo(px, py)
                    }

                    // Keyframe point along path
                    val isCurrentFrame = frame.timestampMs == trackingResult.timestampMs
                    drawCircle(
                        color = if (isCurrentFrame) NeonCyan else NeonViolet.copy(alpha = 0.65f),
                        radius = if (isCurrentFrame) 6.dp.toPx() else 3.5.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                // Render luminous motion path
                drawPath(
                    path = trajectoryPath,
                    color = NeonViolet.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )

                // Render Wrist Trajectories if available
                val leftWristPoints = selectedTrack.trajectories.find { it.landmarkName.contains("Left Wrist") }?.points
                if (!leftWristPoints.isNullOrEmpty() && leftWristPoints.size > 1) {
                    val wristPath = Path()
                    leftWristPoints.forEachIndexed { i, pt ->
                        val wx = pt.x * scaleX
                        val wy = pt.y * scaleY
                        if (i == 0) wristPath.moveTo(wx, wy) else wristPath.lineTo(wx, wy)
                    }
                    drawPath(
                        path = wristPath,
                        color = MintSuccess.copy(alpha = 0.5f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                val rightWristPoints = selectedTrack.trajectories.find { it.landmarkName.contains("Right Wrist") }?.points
                if (!rightWristPoints.isNullOrEmpty() && rightWristPoints.size > 1) {
                    val wristPath = Path()
                    rightWristPoints.forEachIndexed { i, pt ->
                        val wx = pt.x * scaleX
                        val wy = pt.y * scaleY
                        if (i == 0) wristPath.moveTo(wx, wy) else wristPath.lineTo(wx, wy)
                    }
                    drawPath(
                        path = wristPath,
                        color = Color(0xFFFFB074).copy(alpha = 0.5f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // Draw Face Tracks
            trackingResult.detectedFaces.forEach { face ->
                val left = face.boxLeft * scaleX
                val top = face.boxTop * scaleY
                val right = face.boxRight * scaleX
                val bottom = face.boxBottom * scaleY
                val boxWidth = right - left
                val boxHeight = bottom - top

                // Face bounding box
                drawRect(
                    color = NeonCyan.copy(alpha = 0.85f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawRect(
                    color = NeonCyan.copy(alpha = 0.08f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight)
                )

                // Face landmarks
                face.landmarks?.let { lm ->
                    lm.leftEye?.let {
                        drawCircle(color = MintSuccess, radius = 3.5.dp.toPx(), center = Offset(it.x * scaleX, it.y * scaleY))
                    }
                    lm.rightEye?.let {
                        drawCircle(color = MintSuccess, radius = 3.5.dp.toPx(), center = Offset(it.x * scaleX, it.y * scaleY))
                    }
                    lm.noseBase?.let {
                        drawCircle(color = Color(0xFFFFB074), radius = 3.5.dp.toPx(), center = Offset(it.x * scaleX, it.y * scaleY))
                    }
                    lm.mouthLeft?.let {
                        drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(it.x * scaleX, it.y * scaleY))
                    }
                    lm.mouthRight?.let {
                        drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(it.x * scaleX, it.y * scaleY))
                    }
                }
            }

            // Draw Person Poses & Skeletons
            trackingResult.detectedPersons.forEach { person ->
                val isSelected = selectedPersonId == null || selectedPersonId == person.personId
                val strokeColor = if (person.personId == selectedPersonId) NeonViolet else MintSuccess
                val boneColor = if (person.personId == selectedPersonId) Color(0xFFD4BFFF) else Color(0xFF7DE6C2)

                val lmMap = person.landmarks.associateBy { it.landmarkType }

                fun pt(name: String): Offset? {
                    return lmMap[name]?.let { Offset(it.position.x * scaleX, it.position.y * scaleY) }
                }

                fun drawBone(name1: String, name2: String) {
                    val p1 = pt(name1)
                    val p2 = pt(name2)
                    if (p1 != null && p2 != null) {
                        drawLine(
                            color = boneColor,
                            start = p1,
                            end = p2,
                            strokeWidth = 2.5.dp.toPx()
                        )
                    }
                }

                if (isSelected) {
                    // Upper Body Bones
                    drawBone("Left Shoulder", "Right Shoulder")
                    drawBone("Left Shoulder", "Left Elbow")
                    drawBone("Left Elbow", "Left Wrist")
                    drawBone("Right Shoulder", "Right Elbow")
                    drawBone("Right Elbow", "Right Wrist")

                    // Torso Bones
                    drawBone("Left Shoulder", "Left Hip")
                    drawBone("Right Shoulder", "Right Hip")
                    drawBone("Left Hip", "Right Hip")

                    // Lower Body Bones
                    drawBone("Left Hip", "Left Knee")
                    drawBone("Left Knee", "Left Ankle")
                    drawBone("Right Hip", "Right Knee")
                    drawBone("Right Knee", "Right Ankle")

                    // Draw Landmark Joints
                    person.landmarks.forEach { lm ->
                        val center = Offset(lm.position.x * scaleX, lm.position.y * scaleY)
                        drawCircle(
                            color = strokeColor,
                            radius = 4.5.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = center
                        )
                    }

                    // Person Bounding Box
                    val pLeft = person.boxLeft * scaleX
                    val pTop = person.boxTop * scaleY
                    val pRight = person.boxRight * scaleX
                    val pBottom = person.boxBottom * scaleY
                    val boxW = pRight - pLeft
                    val boxH = pBottom - pTop

                    val isInterpolated = person.status == "INTERPOLATED_OCCLUDED"
                    val boxBorderColor = if (isInterpolated) Color(0xFFFFB300) else strokeColor

                    drawRect(
                        color = boxBorderColor.copy(alpha = if (person.personId == selectedPersonId) 0.85f else 0.55f),
                        topLeft = Offset(pLeft, pTop),
                        size = Size(boxW, boxH),
                        style = Stroke(
                            width = if (person.personId == selectedPersonId) 2.5.dp.toPx() else 1.5.dp.toPx(),
                            pathEffect = if (isInterpolated) PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) else null
                        )
                    )

                    // Draw Velocity Vector Arrow if moving
                    if (person.speedPxPerSec > 15f) {
                        val centerX = (pLeft + pRight) / 2f
                        val centerY = (pTop + pBottom) / 2f
                        val vx = person.instantVelocityX * scaleX * 0.18f
                        val vy = person.instantVelocityY * scaleY * 0.18f
                        val arrowEnd = Offset(centerX + vx, centerY + vy)

                        drawLine(
                            color = Color(0xFFFFE57F),
                            start = Offset(centerX, centerY),
                            end = arrowEnd,
                            strokeWidth = 3.dp.toPx()
                        )

                        // Arrowhead
                        val angle = atan2(vy.toDouble(), vx.toDouble())
                        val arrowLen = 14f
                        val a1 = Offset(
                            (arrowEnd.x - arrowLen * cos(angle - Math.PI / 6)).toFloat(),
                            (arrowEnd.y - arrowLen * sin(angle - Math.PI / 6)).toFloat()
                        )
                        val a2 = Offset(
                            (arrowEnd.x - arrowLen * cos(angle + Math.PI / 6)).toFloat(),
                            (arrowEnd.y - arrowLen * sin(angle + Math.PI / 6)).toFloat()
                        )
                        drawLine(color = Color(0xFFFFE57F), start = arrowEnd, end = a1, strokeWidth = 2.5.dp.toPx())
                        drawLine(color = Color(0xFFFFE57F), start = arrowEnd, end = a2, strokeWidth = 2.5.dp.toPx())
                    }
                }
            }
        }

        // Overlay status pill at top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color(0xCC090D18), RoundedCornerShape(6.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (selectedPersonId != null) "TRACK #$selectedPersonId ACTIVE" else "ALL ACTORS",
                color = if (selectedPersonId != null) NeonCyan else MintSuccess,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SurfaceContainer(
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.5f, 2.5f))
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
    ) {
        content()
    }
}
