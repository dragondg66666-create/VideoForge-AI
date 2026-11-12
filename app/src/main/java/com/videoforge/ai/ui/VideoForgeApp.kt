package com.videoforge.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoforge.ai.ui.components.StatCard
import com.videoforge.ai.ui.components.StatusBadge
import com.videoforge.ai.ui.components.ToastBanner
import com.videoforge.ai.ui.screens.CharacterStudioScreen
import com.videoforge.ai.ui.screens.ProjectOverviewScreen
import com.videoforge.ai.ui.screens.VideoAnalyzerScreen
import com.videoforge.ai.ui.theme.DarkBackground
import com.videoforge.ai.ui.theme.DarkBorder
import com.videoforge.ai.ui.theme.DarkSurface
import com.videoforge.ai.ui.theme.DarkSurfaceVariant
import com.videoforge.ai.ui.theme.MintSuccess
import com.videoforge.ai.ui.theme.NeonCyan
import com.videoforge.ai.ui.theme.NeonViolet
import com.videoforge.ai.ui.theme.TextHighlight
import com.videoforge.ai.ui.theme.TextMuted
import com.videoforge.ai.ui.theme.TextPrimary
import com.videoforge.ai.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoForgeApp(
    viewModel: VideoForgeViewModel,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.projects.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val toastIsError by viewModel.toastIsError.collectAsState()

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        val isWide = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWide) {
                // Adaptive Navigation Rail for Tablets / Wide Windows
                NavigationRail(
                    containerColor = DarkSurface,
                    contentColor = TextSecondary,
                    modifier = Modifier.border(1.dp, DarkBorder)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                Brush.linearGradient(listOf(NeonViolet, NeonCyan)),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Logo",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    NavigationRailItem(
                        selected = currentTab == AppTab.ANALYZER,
                        onClick = { viewModel.setTab(AppTab.ANALYZER) },
                        icon = { Icon(Icons.Default.Videocam, contentDescription = "Analyzer") },
                        label = { Text("Analyzer") },
                        colors = railColors(),
                        modifier = Modifier.testTag("nav_rail_analyzer")
                    )
                    NavigationRailItem(
                        selected = currentTab == AppTab.STUDIO,
                        onClick = { viewModel.setTab(AppTab.STUDIO) },
                        icon = { Icon(Icons.Default.Group, contentDescription = "Studio") },
                        label = { Text("Studio") },
                        colors = railColors(),
                        modifier = Modifier.testTag("nav_rail_studio")
                    )
                    NavigationRailItem(
                        selected = currentTab == AppTab.OVERVIEW,
                        onClick = { viewModel.setTab(AppTab.OVERVIEW) },
                        icon = { Icon(Icons.Default.Layers, contentDescription = "Overview") },
                        label = { Text("Overview") },
                        colors = railColors(),
                        modifier = Modifier.testTag("nav_rail_overview")
                    )
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isWide) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                Brush.linearGradient(listOf(NeonViolet, NeonCyan)),
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "VideoForge",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "AI",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonCyan,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Text(
                                        text = activeProject?.name ?: "Workspace",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        },
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                StatusBadge("PHASE 01")
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFF131D2D), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFF24334B), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(MintSuccess, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "On-Device Core",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9AA7BF)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DarkSurface
                        ),
                        modifier = Modifier.border(0.dp, DarkBorder)
                    )
                },
                bottomBar = {
                    if (!isWide) {
                        NavigationBar(
                            containerColor = DarkSurface,
                            contentColor = TextSecondary,
                            modifier = Modifier.border(1.dp, DarkBorder)
                        ) {
                            NavigationBarItem(
                                selected = currentTab == AppTab.ANALYZER,
                                onClick = { viewModel.setTab(AppTab.ANALYZER) },
                                icon = { Icon(Icons.Default.Videocam, contentDescription = "Video Analyzer") },
                                label = { Text("Analyzer", fontSize = 11.sp) },
                                colors = navColors(),
                                modifier = Modifier.testTag("tab_video_analyzer")
                            )
                            NavigationBarItem(
                                selected = currentTab == AppTab.STUDIO,
                                onClick = { viewModel.setTab(AppTab.STUDIO) },
                                icon = { Icon(Icons.Default.Group, contentDescription = "Character Studio") },
                                label = { Text("Cast Studio", fontSize = 11.sp) },
                                colors = navColors(),
                                modifier = Modifier.testTag("tab_character_studio")
                            )
                            NavigationBarItem(
                                selected = currentTab == AppTab.OVERVIEW,
                                onClick = { viewModel.setTab(AppTab.OVERVIEW) },
                                icon = { Icon(Icons.Default.Layers, contentDescription = "Project Overview") },
                                label = { Text("Overview", fontSize = 11.sp) },
                                colors = navColors(),
                                modifier = Modifier.testTag("tab_project_overview")
                            )
                        }
                    }
                },
                containerColor = DarkBackground
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Global Quick Stats Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C1322))
                            .border(1.dp, DarkBorder)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatChip("Projects", projects.size.toString(), NeonViolet)
                        VerticalDivider(modifier = Modifier.height(18.dp).width(1.dp), color = DarkBorder)
                        StatChip("Source", if (activeProject?.sourceVideo != null) "1" else "0", NeonCyan)
                        VerticalDivider(modifier = Modifier.height(18.dp).width(1.dp), color = DarkBorder)
                        StatChip("Characters", (activeProject?.characters?.size ?: 0).toString(), MintSuccess)
                        VerticalDivider(modifier = Modifier.height(18.dp).width(1.dp), color = DarkBorder)
                        StatChip("Analysis", if (activeProject?.analysis?.status == "completed") "Ready" else "—", Color(0xFFFFB074))
                    }

                    // Toast message banner
                    AnimatedVisibility(
                        visible = !toastMessage.isNullOrBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        toastMessage?.let { msg ->
                            ToastBanner(
                                message = msg,
                                isError = toastIsError,
                                onDismiss = { viewModel.clearToast() },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Main Active Screen View
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentTab) {
                            AppTab.ANALYZER -> {
                                activeProject?.let { proj ->
                                    VideoAnalyzerScreen(viewModel = viewModel, project = proj)
                                } ?: ProjectOverviewScreen(viewModel = viewModel, project = null)
                            }
                            AppTab.STUDIO -> {
                                activeProject?.let { proj ->
                                    CharacterStudioScreen(viewModel = viewModel, project = proj)
                                } ?: ProjectOverviewScreen(viewModel = viewModel, project = null)
                            }
                            AppTab.OVERVIEW -> {
                                ProjectOverviewScreen(viewModel = viewModel, project = activeProject)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(accentColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 10.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = NeonViolet,
    selectedTextColor = TextPrimary,
    indicatorColor = Color(0xFF231E44),
    unselectedIconColor = TextMuted,
    unselectedTextColor = TextMuted
)

@Composable
private fun railColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = NeonViolet,
    selectedTextColor = TextPrimary,
    indicatorColor = Color(0xFF231E44),
    unselectedIconColor = TextMuted,
    unselectedTextColor = TextMuted
)
