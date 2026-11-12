package com.videoforge.ai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoforge.ai.model.Project
import com.videoforge.ai.ui.VideoForgeViewModel
import com.videoforge.ai.ui.components.NoticeBox
import com.videoforge.ai.ui.components.SectionKicker
import com.videoforge.ai.ui.components.StatCard
import com.videoforge.ai.ui.components.StatusBadge
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

@Composable
fun ProjectOverviewScreen(
    viewModel: VideoForgeViewModel,
    project: Project?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsState()

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectDesc by remember { mutableStateOf("") }

    var showJsonDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Project Banner
        if (project != null) {
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
                        SectionKicker("PROJECT OVERVIEW")
                        StatusBadge("ACTIVE PROJECT", isSuccess = true)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (project.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Phase 1 Progress Checklist
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChecklistRow(
                            stepNumber = "1",
                            title = "Source video",
                            subtitle = if (project.sourceVideo != null) "Uploaded: ${project.sourceVideo.filename}" else "Waiting for upload",
                            isDone = project.sourceVideo != null
                        )
                        ChecklistRow(
                            stepNumber = "2",
                            title = "Character references",
                            subtitle = if (project.characters.any { it.images.isNotEmpty() }) "References added for ${project.characters.size} character(s)" else "Optional at this step",
                            isDone = project.characters.any { it.images.isNotEmpty() }
                        )
                        ChecklistRow(
                            stepNumber = "3",
                            title = "AI analysis",
                            subtitle = when (project.analysis.status) {
                                "completed" -> "Completed · ${project.analysis.keyframesCount} keyframes"
                                "analyzing" -> "Processing frames…"
                                else -> "Not started"
                            },
                            isDone = project.analysis.status == "completed"
                        )
                        ChecklistRow(
                            stepNumber = "4",
                            title = "Reconstruction & Neural Rigging",
                            subtitle = "Coming in Phase 2",
                            isDone = false
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Package Exporter Action
                    Button(
                        onClick = {
                            exportedJsonText = viewModel.exportPackageJson()
                            showJsonDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25334C)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_project_package_button")
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = TextHighlight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Project Package (JSON v1.0)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }
        }

        // Project Switcher & Workspace Library
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
                        SectionKicker("WORKSPACE LIBRARY")
                        Text(
                            text = "Projects (${projects.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Button(
                        onClick = {
                            newProjectName = ""
                            newProjectDesc = ""
                            showNewProjectDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("create_new_project_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Project", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    projects.forEach { p ->
                        val isCurrent = p.projectId == project?.projectId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isCurrent) NeonViolet else DarkBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.selectProject(p) }
                                .testTag("project_item_${p.projectId}"),
                            color = if (isCurrent) Color(0xFF1E1E3A) else DarkSurfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(if (isCurrent) NeonViolet else Color(0xFF1C273C), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isCurrent) Color.White else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = p.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${p.characters.size} character(s) · ${if (p.sourceVideo != null) "Video Ready" else "No Video"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                if (projects.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.deleteProject(p.projectId) },
                                        modifier = Modifier.testTag("delete_project_${p.projectId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Project",
                                            tint = Color(0xFFFF6B6B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Create New Project Dialog
    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("Create New Project", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Start a project workspace for your source video and replacement characters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonViolet,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("project_name_input")
                    )
                    OutlinedTextField(
                        value = newProjectDesc,
                        onValueChange = { newProjectDesc = it },
                        label = { Text("Description (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonViolet,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("project_desc_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            viewModel.createProject(newProjectName, newProjectDesc)
                            showNewProjectDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    enabled = newProjectName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_create_project_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNewProjectDialog = false },
                    modifier = Modifier.testTag("cancel_create_project_button")
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // JSON Package Viewer Dialog
    if (showJsonDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Package JSON v1.0", color = TextPrimary)
                    Row {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("VideoForge Package", exportedJsonText)
                                clipboard.setPrimaryClip(clip)
                                viewModel.showToast("JSON copied to clipboard")
                            },
                            modifier = Modifier.testTag("copy_json_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = NeonCyan)
                        }
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TEXT, exportedJsonText)
                                    putExtra(Intent.EXTRA_TITLE, "${project?.name ?: "VideoForge"}-package.json")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share VideoForge Package"))
                            },
                            modifier = Modifier.testTag("share_json_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = NeonViolet)
                        }
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .background(Color(0xFF090D18), RoundedCornerShape(8.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = exportedJsonText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFC7D3F0),
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showJsonDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    modifier = Modifier.testTag("close_json_dialog_button")
                ) {
                    Text("Close")
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}

@Composable
private fun ChecklistRow(
    stepNumber: String,
    title: String,
    subtitle: String,
    isDone: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(
                    if (isDone) MintSuccess else Color.Transparent,
                    CircleShape
                )
                .border(
                    1.dp,
                    if (isDone) MintSuccess else Color(0xFF53617A),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF090D18),
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = stepNumber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA2AEC5)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 11.sp,
                color = if (isDone) MintSuccess else TextMuted
            )
        }
    }
}
