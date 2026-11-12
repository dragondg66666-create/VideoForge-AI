package com.videoforge.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoforge.ai.ui.theme.DarkBorder
import com.videoforge.ai.ui.theme.DarkBorderSubtle
import com.videoforge.ai.ui.theme.DarkSurface
import com.videoforge.ai.ui.theme.DarkSurfaceElevated
import com.videoforge.ai.ui.theme.DarkSurfaceVariant
import com.videoforge.ai.ui.theme.MintSuccess
import com.videoforge.ai.ui.theme.MintSuccessBg
import com.videoforge.ai.ui.theme.NeonCyan
import com.videoforge.ai.ui.theme.NeonViolet
import com.videoforge.ai.ui.theme.TextHighlight
import com.videoforge.ai.ui.theme.TextMuted
import com.videoforge.ai.ui.theme.TextPrimary
import com.videoforge.ai.ui.theme.TextSecondary

@Composable
fun SectionKicker(
    kicker: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = kicker.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextHighlight,
        modifier = modifier
    )
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        color = DarkSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconBgColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    isSuccess: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bg = if (isSuccess) MintSuccessBg else Color(0xFF282448)
    val border = if (isSuccess) Color(0xFF2A5C4F) else Color(0xFF454071)
    val textColor = if (isSuccess) MintSuccess else TextHighlight

    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontSize = 9.sp
        )
    }
}

@Composable
fun NoticeBox(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF2A3B59), RoundedCornerShape(8.dp)),
        color = Color(0xFF142035),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NeonViolet,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun ToastBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isError) Color(0xFF48262B) else Color(0xFF143B33)
    val border = if (isError) Color(0xFF82434B) else Color(0xFF2A725E)
    val textCol = if (isError) Color(0xFFFFD0D3) else Color(0xFFA4F2D4)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(10.dp)),
        color = bg,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textCol,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("dismiss_toast_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = textCol,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
