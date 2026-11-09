from __future__ import annotations
import json, os, re, shutil, uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parents[2]
STORAGE_DIR = Path(os.getenv("STORAGE_DIR", str(BASE_DIR / "storage")))
UPLOAD_DIR = STORAGE_DIR / "uploads"
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
DB_FILE = STORAGE_DIR / "projects.json"
MAX_VIDEO_BYTES = 2 * 1024 * 1024 * 1024
MAX_IMAGE_BYTES = 25 * 1024 * 1024
VIDEO_EXTS = {".mp4", ".mov", ".mkv", ".webm", ".avi"}
IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp"}

def now() -> str:
    return datetime.now(timezone.utc).isoformat()

def load_db() -> dict[str, Any]:
    if DB_FILE.exists():
        try:
            return json.loads(DB_FILE.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}

def save_db(data: dict[str, Any]) -> None:
    STORAGE_DIR.mkdir(parents=True, exist_ok=True)
    DB_FILE.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

db = load_db()

def safe_name(name: str) -> str:
    suffix = Path(name or "upload").suffix.lower()
    stem = re.sub(r"[^a-zA-Z0-9_-]+", "-", Path(name or "upload").stem).strip("-")[:60] or "upload"
    return f"{stem}{suffix}"

class ProjectCreate(BaseModel):
    name: str = Field(default="Untitled project", min_length=1, max_length=100)
    description: str = Field(default="", max_length=1000)

app = FastAPI(title="VideoForge AI API", version="0.1.0", description="Phase 1 project and upload API")
origins = os.getenv("CORS_ORIGINS", "http://localhost:5173").split(",")
app.add_middleware(CORSMiddleware, allow_origins=[x.strip() for x in origins], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])

@app.get("/")
def root():
    return {"name": "VideoForge AI API", "version": "0.1.0", "docs": "/docs"}

@app.get("/api/health")
def health():
    return {"status": "ok", "service": "videoforge-api", "time": now()}

@app.get("/api/projects")
def list_projects():
    return sorted(db.values(), key=lambda p: p.get("updated_at", ""), reverse=True)

@app.post("/api/projects")
def create_project(payload: ProjectCreate):
    project_id = str(uuid.uuid4())
    project = {
        "project_id": project_id, "name": payload.name, "description": payload.description,
        "created_at": now(), "updated_at": now(), "source_video": None,
        "characters": [], "scenes": [], "analysis": {"status": "not_started"},
        "preservation": {"original_audio": True, "original_timing": True, "original_background": True}
    }
    db[project_id] = project
    save_db(db)
    (UPLOAD_DIR / project_id / "characters").mkdir(parents=True, exist_ok=True)
    return project

def get_project(project_id: str):
    project = db.get(project_id)
    if not project:
        raise HTTPException(status_code=404, detail="Project not found")
    return project

async def save_upload(upload: UploadFile, destination: Path, allowed: set[str], max_bytes: int):
    suffix = Path(upload.filename or "").suffix.lower()
    if suffix not in allowed:
        raise HTTPException(status_code=400, detail=f"Unsupported file type: {suffix or 'no extension'}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    size = 0
    try:
        with destination.open("wb") as out:
            while chunk := await upload.read(1024 * 1024):
                size += len(chunk)
                if size > max_bytes:
                    out.close()
                    destination.unlink(missing_ok=True)
                    raise HTTPException(status_code=413, detail="File exceeds upload size limit")
                out.write(chunk)
    finally:
        await upload.close()
    return size

@app.post("/api/projects/{project_id}/video")
async def upload_video(project_id: str, file: UploadFile = File(...)):
    project = get_project(project_id)
    filename = f"source_{uuid.uuid4().hex}{Path(file.filename or '').suffix.lower()}"
    path = UPLOAD_DIR / project_id / filename
    size = await save_upload(file, path, VIDEO_EXTS, MAX_VIDEO_BYTES)
    project["source_video"] = {
        "filename": file.filename, "stored_as": filename, "size_bytes": size,
        "content_type": file.content_type, "uploaded_at": now()
    }
    project["updated_at"] = now()
    save_db(db)
    return {"message": "Video uploaded", "source_video": project["source_video"]}

@app.post("/api/projects/{project_id}/characters/{character_id}/images")
async def upload_character_images(project_id: str, character_id: str, files: list[UploadFile] = File(...)):
    project = get_project(project_id)
    if not re.fullmatch(r"[a-zA-Z0-9_-]{1,60}", character_id):
        raise HTTPException(status_code=400, detail="Invalid character_id")
    saved = []
    folder = UPLOAD_DIR / project_id / "characters" / character_id
    for file in files:
        filename = f"{uuid.uuid4().hex}_{safe_name(file.filename or 'reference.png')}"
        path = folder / filename
        size = await save_upload(file, path, IMAGE_EXTS, MAX_IMAGE_BYTES)
        saved.append({"filename": file.filename, "stored_as": filename, "size_bytes": size, "uploaded_at": now()})
    existing = next((c for c in project["characters"] if c["character_id"] == character_id), None)
    if existing:
        existing["images"].extend(saved)
    else:
        project["characters"].append({"character_id": character_id, "name": character_id, "images": saved})
    project["updated_at"] = now()
    save_db(db)
    return {"character_id": character_id, "uploaded": saved}

@app.post("/api/projects/{project_id}/analysis")
def start_analysis(project_id: str):
    project = get_project(project_id)
    if not project.get("source_video"):
        raise HTTPException(status_code=400, detail="Upload a source video first")
    project["analysis"] = {
        "status": "queued_not_configured", "created_at": now(),
        "message": "Foundation endpoint only. Connect scene detection and Gemini workers in a later phase."
    }
    project["updated_at"] = now()
    save_db(db)
    return project["analysis"]

@app.get("/api/projects/{project_id}/package")
def get_package(project_id: str):
    project = get_project(project_id)
    return JSONResponse(content={"schema_version": "1.0", **project})

@app.get("/api/projects/{project_id}")
def project_detail(project_id: str):
    return get_project(project_id)
