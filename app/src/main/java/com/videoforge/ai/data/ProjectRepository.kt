package com.videoforge.ai.data

import android.content.Context
import com.videoforge.ai.model.AnalysisState
import com.videoforge.ai.model.CharacterImage
import com.videoforge.ai.model.CharacterProfile
import com.videoforge.ai.model.PreservationSettings
import com.videoforge.ai.model.Project
import com.videoforge.ai.model.ProjectPackageExport
import com.videoforge.ai.model.SourceVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val storageFile: File
        get() = File(context.filesDir, "projects.json")

    private fun getCurrentIsoTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    suspend fun loadProjects(): List<Project> = withContext(Dispatchers.IO) {
        if (!storageFile.exists()) {
            val initial = createInitialProjects()
            saveAllProjects(initial)
            return@withContext initial
        }
        try {
            val text = storageFile.readText()
            val list = json.decodeFromString<List<Project>>(text)
            if (list.isEmpty()) {
                val initial = createInitialProjects()
                saveAllProjects(initial)
                initial
            } else {
                list
            }
        } catch (e: Exception) {
            val initial = createInitialProjects()
            saveAllProjects(initial)
            initial
        }
    }

    suspend fun saveAllProjects(projects: List<Project>) = withContext(Dispatchers.IO) {
        try {
            val text = json.encodeToString(projects)
            storageFile.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createProject(name: String, description: String = ""): Project {
        val now = getCurrentIsoTime()
        val newProj = Project(
            projectId = UUID.randomUUID().toString(),
            name = name.ifBlank { "Untitled project" },
            description = description,
            createdAt = now,
            updatedAt = now,
            sourceVideo = null,
            characters = listOf(
                CharacterProfile(
                    characterId = "character_01",
                    name = "Protagonist",
                    images = emptyList()
                )
            ),
            scenes = emptyList(),
            analysis = AnalysisState(status = "not_started"),
            preservation = PreservationSettings()
        )
        val current = loadProjects().toMutableList()
        current.add(0, newProj)
        saveAllProjects(current)
        return newProj
    }

    suspend fun updateProject(project: Project) {
        val updated = project.copy(updatedAt = getCurrentIsoTime())
        val current = loadProjects().map {
            if (it.projectId == updated.projectId) updated else it
        }
        saveAllProjects(current)
    }

    suspend fun deleteProject(projectId: String) {
        val current = loadProjects().filter { it.projectId != projectId }
        saveAllProjects(current)
    }

    fun exportToJson(project: Project): String {
        val export = ProjectPackageExport(
            schemaVersion = "1.0",
            projectId = project.projectId,
            name = project.name,
            description = project.description,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            sourceVideo = project.sourceVideo,
            characters = project.characters,
            scenes = project.scenes,
            analysis = project.analysis,
            preservation = project.preservation
        )
        return json.encodeToString(export)
    }

    private fun createInitialProjects(): List<Project> {
        val now = getCurrentIsoTime()
        return listOf(
            Project(
                projectId = UUID.randomUUID().toString(),
                name = "Cyberpunk Alley Action Sequence",
                description = "Original stunt double performance to be mapped with synthetic avatar actor.",
                createdAt = now,
                updatedAt = now,
                sourceVideo = SourceVideo(
                    filename = "alley_run_scene_1080p.mp4",
                    uriString = null,
                    sizeBytes = 24_680_000,
                    contentType = "video/mp4",
                    durationMs = 14_500L,
                    durationSeconds = 14.5,
                    width = 1920,
                    height = 1080,
                    rotation = 0,
                    fps = 24.0,
                    resolution = "1920x1080",
                    bitrate = 14_000_000L,
                    uploadedAt = now
                ),
                characters = listOf(
                    CharacterProfile(
                        characterId = "character_01",
                        name = "Neo Pilot",
                        images = listOf(
                            CharacterImage(
                                imageId = "img_01",
                                filename = "ref_front_bust.png",
                                uriString = null,
                                sizeBytes = 1_840_000,
                                angleTag = "Front View",
                                uploadedAt = now
                            ),
                            CharacterImage(
                                imageId = "img_02",
                                filename = "ref_3quarter_left.png",
                                uriString = null,
                                sizeBytes = 1_920_000,
                                angleTag = "3/4 Left Angle",
                                uploadedAt = now
                            )
                        )
                    )
                ),
                scenes = listOf("Scene 01: Initial master anchor (00:00.0)"),
                analysis = AnalysisState(
                    status = "not_started",
                    message = null,
                    progress = 0f,
                    framesAnalyzed = 0,
                    detectedFaces = 0,
                    sceneCuts = 0,
                    detectedFps = 24.0,
                    resolution = "1920x1080",
                    motionIntensity = "Unprocessed",
                    audioTrackDetected = true,
                    keyframesCount = 0,
                    updatedAt = now
                ),
                preservation = PreservationSettings(
                    originalAudio = true,
                    originalTiming = true,
                    originalBackground = true
                )
            ),
            Project(
                projectId = UUID.randomUUID().toString(),
                name = "Dialogue Chamber Close-up",
                description = "Intimate dramatic conversation requiring subtle facial muscle fidelity.",
                createdAt = now,
                updatedAt = now,
                sourceVideo = null,
                characters = listOf(
                    CharacterProfile(
                        characterId = "character_02",
                        name = "Commander Vane",
                        images = emptyList()
                    )
                ),
                scenes = emptyList(),
                analysis = AnalysisState(status = "not_started"),
                preservation = PreservationSettings(
                    originalAudio = true,
                    originalTiming = true,
                    originalBackground = true
                )
            )
        )
    }
}
