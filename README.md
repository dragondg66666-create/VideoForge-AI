# VideoForge AI — Android Studio Application (Phase 1 Foundation)

A native Android application built with **Kotlin** and **Jetpack Compose**, implementing the **VideoForge AI** video analysis and character reconstruction workflow.

---

## Ported Features & Architecture

### 1. Video Analyzer (Step 01)
- **Source Video Ingestion**: Native device file and gallery picker for MP4, MOV, MKV, WebM, and AVI files via zero-permission Android contracts.
- **Sample Footage Preset**: Instant demo action stunt sequence loader for rapid on-device testing.
- **Video Player**: Video preview with live seek bar, play/pause controls, elapsed/total timestamps, and frame number calculations at 24.0 FPS.
- **Original-First Preservation Matrix**: Toggle switches to preserve original audio stems, frame timing, and background scenery.
- **AI Scene Decomposition**: Simulation and execution of scene cuts detection, keyframe indexing, facial tracking, and motion intensity metrics.

### 2. Character Swap Studio (Step 02)
- **Multi-Character Management**: Create, view, and switch between character profiles (`character_01`, `character_02`, custom aliases).
- **Multi-Angle Reference Ingestion**: Ingest multi-angle photos with automatic tag assignment (`Front View`, `3/4 Angle`, `Side Profile`, `Expression View`).
- **Sample Reference Sets**: Quick-loader for demo portrait and multi-angle reference photos.
- **Readiness Badging**: Real-time coverage metrics assessing reference angle completeness for neural rigging.

### 3. Project Workspace & JSON Package Exporter
- **Project CRUD**: Create new video projects with names and descriptions, switch active projects, and delete old projects.
- **Offline Persistence**: Fast, persistent storage utilizing `projects.json` in the app's internal storage.
- **Schema v1.0 Package Exporter**: Generates a valid JSON package matching the `project.schema.json` v1.0 specification with copy-to-clipboard and Android system share sheet actions.

---

## Tech Stack & Runtime
- **Language**: Kotlin 2.2.10
- **UI Toolkit**: Jetpack Compose (Material Design 3)
- **Architecture**: MVVM (Model-View-ViewModel) with StateFlow
- **Serialization**: `kotlinx.serialization` (JSON v1.0 schema)
- **Image Loading**: Coil Compose 2.7.0
- **Target SDK**: Android 36 (minSdk 26)
- **Build System**: Gradle 9.3.1 with Android Gradle Plugin (AGP) 9.1.1
