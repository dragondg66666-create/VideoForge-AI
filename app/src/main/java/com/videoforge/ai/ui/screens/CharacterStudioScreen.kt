package com.videoforge.ai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import com.videoforge.ai.model.CharacterImage
import com.videoforge.ai.model.CharacterProfile
import com.videoforge.ai.model.Project
import com.videoforge.ai.model.ReferenceCoverage
import com.videoforge.ai.ui.VideoForgeViewModel
import com.videoforge.ai.ui.components.CharacterReplacementStudioView
import com.videoforge.ai.ui.components.NoticeBox
import com.videoforge.ai.ui.components.SectionKicker
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
import java.io.File
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterStudioScreen(
    viewModel: VideoForgeViewModel,
    project: Project,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var studioSection by remember { mutableStateOf("REPLACEMENT") } // "REPLACEMENT" vs "REFERENCE_RIG"

    val activeCharacterId by viewModel.activeCharacterId.collectAsState()
    val currentCharacter = project.characters.find { it.characterId == activeCharacterId }
        ?: project.characters.firstOrNull()

    val selectedTrackId by viewModel.selectedReplacementTrackId.collectAsState()
    val activeAsset by viewModel.activeReplacementAsset.collectAsState()
    val previewFrame by viewModel.compositedPreviewFrame.collectAsState()
    val replacementEnabled by viewModel.replacementEnabled.collectAsState()
    val userScale by viewModel.replacementUserScale.collectAsState()
    val userOffsetX by viewModel.replacementUserOffsetX.collectAsState()
    val userOffsetY by viewModel.replacementUserOffsetY.collectAsState()
    val userRotationTrim by viewModel.replacementUserRotationTrim.collectAsState()
    val userOpacity by viewModel.replacementUserOpacity.collectAsState()
    val maskAwareBlending by viewModel.replacementMaskAware.collectAsState()
    val previewMode by viewModel.replacementPreviewMode.collectAsState()
    val splitRatio by viewModel.replacementSplitRatio.collectAsState()
    val currentTimestampMs by viewModel.replacementTimestampMs.collectAsState()
    val isCompositing by viewModel.isCompositing.collectAsState()

    // Phase 2.7 Export States
    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val lastExportResult by viewModel.lastExportResult.collectAsState()
    val exportConfig by viewModel.exportConfig.collectAsState()

    var pendingExportSourcePath by remember { mutableStateOf<String?>(null) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri: Uri? ->
        if (uri != null && pendingExportSourcePath != null) {
            viewModel.saveExportToUri(pendingExportSourcePath!!, uri)
            pendingExportSourcePath = null
        }
    }

    val onShareExport: (String) -> Unit = { filePath ->
        try {
            val file = File(filePath)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share VideoForge MP4"))
        } catch (e: Exception) {
            viewModel.showToast("Failed to share video: ${e.message}", isError = true)
        }
    }

    val assetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val name = cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else "character_asset.png"
                } else "character_asset.png"
            } ?: "character_asset.png"
            viewModel.importReplacementAsset(uri, name)
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var newCharIdInput by remember { mutableStateOf("") }
    var newCharNameInput by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty() && currentCharacter != null) {
            val defaultAngleOptions = listOf("Front View", "3/4 Left Angle", "Side Profile", "Expression View", "Full Body")
            uris.forEachIndexed { index, uri ->
                val tag = defaultAngleOptions[index % defaultAngleOptions.size]
                viewModel.addCharacterImage(
                    characterId = currentCharacter.characterId,
                    filename = "ref_${System.currentTimeMillis()}_${index + 1}.png",
                    uriString = uri.toString(),
                    sizeBytes = 2_100_000,
                    angleTag = tag
                )
            }
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
        // Welcome Header
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
                    SectionKicker("STEP 02 · CHARACTER STUDIO & REPLACEMENT")
                    val coverage = currentCharacter?.computeCoverage()
                    StatusBadge(
                        text = if (activeAsset != null) "ASSET LINKED" else if (coverage?.isRigReady == true) "RIG READY" else "${coverage?.coveredCount ?: 0}/4 VIEWS",
                        isSuccess = activeAsset != null || coverage?.isRigReady == true
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Character Studio & 2D Compositing",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Genuine 2D alpha compositing using video motion tracks, segmentation masks, and transparent PNG character assets alongside multi-angle reference rig analysis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Sub-Tab Section Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (studioSection == "REPLACEMENT") Color(0xFF232D48) else DarkSurfaceVariant)
                    .border(
                        1.dp,
                        if (studioSection == "REPLACEMENT") NeonCyan else DarkBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { studioSection = "REPLACEMENT" }
                    .padding(vertical = 10.dp, horizontal = 8.dp)
                    .testTag("tab_replacement_studio"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = if (studioSection == "REPLACEMENT") NeonCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "2D Character Replacement",
                        fontSize = 11.sp,
                        fontWeight = if (studioSection == "REPLACEMENT") FontWeight.Bold else FontWeight.Medium,
                        color = if (studioSection == "REPLACEMENT") TextHighlight else TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (studioSection == "REFERENCE_RIG") Color(0xFF232D48) else DarkSurfaceVariant)
                    .border(
                        1.dp,
                        if (studioSection == "REFERENCE_RIG") NeonViolet else DarkBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { studioSection = "REFERENCE_RIG" }
                    .padding(vertical = 10.dp, horizontal = 8.dp)
                    .testTag("tab_reference_rig"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = if (studioSection == "REFERENCE_RIG") NeonViolet else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reference Angle Rig",
                        fontSize = 11.sp,
                        fontWeight = if (studioSection == "REFERENCE_RIG") FontWeight.Bold else FontWeight.Medium,
                        color = if (studioSection == "REFERENCE_RIG") TextHighlight else TextSecondary
                    )
                }
            }
        }

        if (studioSection == "REPLACEMENT") {
            CharacterReplacementStudioView(
                tracks = project.analysis.personTracks,
                selectedTrackId = selectedTrackId,
                activeAsset = activeAsset,
                previewFrame = previewFrame,
                replacementEnabled = replacementEnabled,
                userScale = userScale,
                userOffsetX = userOffsetX,
                userOffsetY = userOffsetY,
                userRotationTrim = userRotationTrim,
                userOpacity = userOpacity,
                maskAwareBlending = maskAwareBlending,
                previewMode = previewMode,
                splitRatio = splitRatio,
                currentTimestampMs = currentTimestampMs,
                isCompositing = isCompositing,
                onSelectTrack = { trackId -> viewModel.setReplacementTrack(trackId) },
                onImportAssetClick = { assetPickerLauncher.launch("image/*") },
                onLoadSampleAsset = { viewModel.loadSampleHeroAsset() },
                onToggleEnabled = { enabled -> viewModel.toggleReplacementEnabled(enabled) },
                onUpdateTransform = { scale, offX, offY, rotTrim, opacity, maskAware ->
                    viewModel.updateReplacementTransform(scale, offX, offY, rotTrim, opacity, maskAware)
                },
                onSetPreviewMode = { mode -> viewModel.setReplacementPreviewMode(mode) },
                onSetSplitRatio = { ratio -> viewModel.setReplacementSplitRatio(ratio) },
                onSelectTimestamp = { ts -> viewModel.setReplacementTimestamp(ts) },
                isExporting = isExporting,
                exportProgress = exportProgress,
                lastExportResult = lastExportResult,
                exportConfig = exportConfig,
                onStartExport = { viewModel.startExportMp4() },
                onCancelExport = { viewModel.cancelExportMp4() },
                onUpdateExportConfig = { cfg -> viewModel.updateExportConfig(cfg) },
                onDismissExportResult = { viewModel.dismissExportResult() },
                onSaveExportClick = { path ->
                    pendingExportSourcePath = path
                    createDocumentLauncher.launch("videoforge_composite.mp4")
                },
                onShareExport = onShareExport
            )
        } else {
            // Character Selector Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
            color = DarkSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Character Profiles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    OutlinedButton(
                        onClick = {
                            newCharIdInput = "character_0${project.characters.size + 1}"
                            newCharNameInput = ""
                            showAddDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        modifier = Modifier.testTag("add_character_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val rowScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rowScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    project.characters.forEach { charProfile ->
                        val isSelected = charProfile.characterId == activeCharacterId
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectCharacter(charProfile.characterId) },
                            label = {
                                Text(
                                    text = "${charProfile.name.ifBlank { charProfile.characterId }} (${charProfile.images.size})",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) NeonViolet else TextSecondary
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
                            modifier = Modifier.testTag("character_chip_${charProfile.characterId}")
                        )
                    }
                }
            }
        }

        // Active Character Detail, Coverage Analysis & Reference Ingestion
        if (currentCharacter != null) {
            val coverage = currentCharacter.computeCoverage()

            // Reference Coverage Card
            ReferenceCoverageCard(coverage = coverage)

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
                            Text(
                                text = currentCharacter.name.ifBlank { currentCharacter.characterId },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "ID: ${currentCharacter.characterId} · ${currentCharacter.images.size} reference image(s)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        StatusBadge(
                            text = if (coverage.isRigReady) "RIG READY (4/4)" else "${coverage.coveredCount}/4 VIEWS",
                            isSuccess = coverage.isRigReady
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Ingest buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("upload_character_images_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Photos", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.addDemoReferencesForCharacter(currentCharacter.characterId) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_sample_references_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sample Angles", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // References Gallery
                    if (currentCharacter.images.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF26324A), RoundedCornerShape(10.dp)),
                            color = Color(0xFF131D30),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ViewInAr,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No reference angles uploaded yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Upload Front, Left, Right and 3/4 views for high character fidelity.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Processed Reference Angles (${currentCharacter.images.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            currentCharacter.images.forEach { img ->
                                CharacterImageCard(
                                    img = img,
                                    onReprocess = {
                                        viewModel.reprocessCharacterImage(currentCharacter.characterId, img.imageId)
                                    },
                                    onDelete = {
                                        viewModel.removeCharacterImage(currentCharacter.characterId, img.imageId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        // Technical Notice Box
        NoticeBox(
            icon = Icons.Default.Security,
            text = "Phase 2.6 implements genuine 2D alpha compositing using motion trajectories, temporal transform interpolation, transparent PNG validation, and mask-aware bounding protection."
        )

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Add Character Profile Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text("New Character Profile", color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Define a replacement actor or 3D synthetic character ID.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = newCharIdInput,
                        onValueChange = { newCharIdInput = it },
                        label = { Text("Character ID (e.g. character_02)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonViolet,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_character_id_input")
                    )
                    OutlinedTextField(
                        value = newCharNameInput,
                        onValueChange = { newCharNameInput = it },
                        label = { Text("Display Name (optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonViolet,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_character_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCharacter(newCharIdInput, newCharNameInput)
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    modifier = Modifier.testTag("confirm_create_character_button")
                ) {
                    Text("Create Profile")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    modifier = Modifier.testTag("cancel_create_character_button")
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferenceCoverageCard(coverage: ReferenceCoverage) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
        color = DarkSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reference Coverage Analysis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                StatusBadge(
                    text = if (coverage.isRigReady) "READY FOR RIGGING" else "${coverage.coveredCount}/4 VIEWS",
                    isSuccess = coverage.isRigReady
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            val progress = (coverage.coveredCount.toFloat() / coverage.totalRequired).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (coverage.isRigReady) MintSuccess else NeonViolet,
                trackColor = Color(0xFF1E2838)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Angle requirements grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CoverageStatusPill(
                    label = "Front View",
                    isCovered = coverage.hasFrontView,
                    modifier = Modifier.weight(1f)
                )
                CoverageStatusPill(
                    label = "3/4 Angles",
                    isCovered = coverage.hasThreeQuarterView,
                    modifier = Modifier.weight(1f)
                )
                CoverageStatusPill(
                    label = "Profile",
                    isCovered = coverage.hasProfileView,
                    modifier = Modifier.weight(1f)
                )
                CoverageStatusPill(
                    label = "Full Body",
                    isCovered = coverage.hasFullBody,
                    modifier = Modifier.weight(1f)
                )
            }

            if (coverage.missingAngles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Missing recommendations: ",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        coverage.missingAngles.forEach { missing ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF281E2A), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFF5A3048), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "+ $missing",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFF9EA6),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverageStatusPill(
    label: String,
    isCovered: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (isCovered) Color(0xFF1B4D3E) else DarkBorder,
                shape = RoundedCornerShape(8.dp)
            ),
        color = if (isCovered) Color(0xFF112A22) else DarkSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isCovered) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isCovered) MintSuccess else TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isCovered) FontWeight.Bold else FontWeight.Normal,
                color = if (isCovered) MintSuccess else TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterImageCard(
    img: CharacterImage,
    onReprocess: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .testTag("character_image_${img.imageId}"),
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Thumbnails + Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source Image Thumbnail
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF231D45)),
                    contentAlignment = Alignment.Center
                ) {
                    if (img.uriString != null) {
                        AsyncImage(
                            model = img.uriString,
                            contentDescription = img.filename,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = TextHighlight,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Cropped Face Preview (if detected)
                if (img.croppedFacePath != null && File(img.croppedFacePath).exists()) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, NeonViolet, RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1430)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = File(img.croppedFacePath),
                            contentDescription = "Normalized Face Crop",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color(0xCC000000))
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = "CROP",
                                fontSize = 8.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // Info column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = img.angleTag,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        // Processing Status Badge
                        when (img.processingStatus.lowercase(Locale.ROOT)) {
                            "processing" -> {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF3B2E15), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(8.dp),
                                            strokeWidth = 1.5.dp,
                                            color = Color(0xFFFFB300)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ANALYZING...", fontSize = 8.sp, color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            "processed" -> {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF16382F), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("PROCESSED", fontSize = 8.sp, color = MintSuccess, fontWeight = FontWeight.Bold)
                                }
                            }
                            "failed", "error" -> {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF451919), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("FAILED", fontSize = 8.sp, color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF26324A), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("QUEUED", fontSize = 8.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = img.filename,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )

                    if (img.imageWidth > 0 && img.imageHeight > 0) {
                        Text(
                            text = "${img.imageWidth}×${img.imageHeight} px",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                // Actions: Reprocess & Delete
                IconButton(
                    onClick = onReprocess,
                    modifier = Modifier.testTag("reprocess_image_${img.imageId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reprocess",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_image_${img.imageId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Reference",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Detection Metrics & Telemetry
            if (img.processingStatus.equals("processed", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF26324A), RoundedCornerShape(8.dp)),
                    color = Color(0xFF131D30),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Face telemetry
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Detected Orientation: ",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = img.detectedOrientation,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextHighlight
                            )

                            if (img.faceDetectionStatus.equals("detected", ignoreCase = true)) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format(Locale.US, "(Yaw: %.1f°, Pitch: %.1f°)", img.eulerY, img.eulerX),
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        // Landmarks & Face Confidence
                        if (img.faceDetectionStatus.equals("detected", ignoreCase = true)) {
                            val landmarksCount = listOfNotNull(
                                img.facialLandmarks?.leftEye,
                                img.facialLandmarks?.rightEye,
                                img.facialLandmarks?.noseBase,
                                img.facialLandmarks?.mouthLeft,
                                img.facialLandmarks?.mouthRight,
                                img.facialLandmarks?.mouthBottom
                            ).size

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Landmarks: $landmarksCount points (eyes, nose, mouth) · Face Conf: ${(img.faceConfidence * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                                if (img.detectedFacesCount > 1) {
                                    Text(
                                        text = "${img.detectedFacesCount} faces found (largest selected)",
                                        fontSize = 10.sp,
                                        color = Color(0xFFFFB300)
                                    )
                                }
                            }
                        } else if (img.faceDetectionStatus.equals("no_face", ignoreCase = true)) {
                            Text(
                                text = "No facial features detected in image",
                                fontSize = 10.sp,
                                color = Color(0xFFFF9EA6)
                            )
                        }

                        // Body pose telemetry
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (img.bodyDetectionStatus.equals("detected", ignoreCase = true)) {
                                Text(
                                    text = if (img.isFullBodyVisible) "Full-Body Pose Detected" else "Upper-Body / Partial Pose",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (img.isFullBodyVisible) MintSuccess else TextHighlight
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${img.bodyLandmarks.size} joints · ${(img.bodyConfidence * 100).toInt()}% conf)",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            } else {
                                Text(
                                    text = "No body pose detected (portrait / closeup crop)",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            } else if (img.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = img.errorMessage,
                        fontSize = 10.sp,
                        color = Color(0xFFFF6B6B)
                    )
                }
            }
        }
    }
}
