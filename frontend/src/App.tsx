import { useEffect, useMemo, useState } from 'react'
import { Activity, ArrowRight, AudioLines, CheckCircle2, Clapperboard, CloudUpload, FileVideo, FolderOpen, Gauge, ImagePlus, Layers3, Play, ScanSearch, Settings2, ShieldCheck, Sparkles, Users, WandSparkles } from 'lucide-react'

const API = import.meta.env.VITE_API_URL || 'http://localhost:8000'
type Project = { project_id: string; name: string; source_video?: { filename: string; size_bytes: number } | null; characters: { character_id: string; images: { filename: string }[] }[]; analysis?: { status: string; message?: string } }
type Tab = 'analyzer' | 'studio'
const fmt = (n: number) => n < 1024 ? `${n} B` : n < 1048576 ? `${(n/1024).toFixed(1)} KB` : `${(n/1048576).toFixed(1)} MB`

export default function App() {
  const [tab, setTab] = useState<Tab>('analyzer')
  const [projects, setProjects] = useState<Project[]>([])
  const [active, setActive] = useState<Project | null>(null)
  const [name, setName] = useState('My first video project')
  const [video, setVideo] = useState<File | null>(null)
  const [images, setImages] = useState<File[]>([])
  const [characterId, setCharacterId] = useState('character_01')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [apiOnline, setApiOnline] = useState(false)

  async function request(path: string, options?: RequestInit) {
    const res = await fetch(`${API}${path}`, options)
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      throw new Error(body.detail || `Request failed (${res.status})`)
    }
    return res.json()
  }
  async function refresh() {
    try {
      const [health, list] = await Promise.all([request('/api/health'), request('/api/projects')])
      setApiOnline(health.status === 'ok'); setProjects(list)
      if (active) {
        const updated = await request(`/api/projects/${active.project_id}`)
        setActive(updated)
      }
    } catch { setApiOnline(false) }
  }
  useEffect(() => { refresh() }, [])
  const current = useMemo(() => active || projects[0] || null, [active, projects])

  async function createProject() {
    setBusy(true); setMessage('')
    try {
      const p = await request('/api/projects', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({name}) })
      setActive(p); setProjects(prev => [p, ...prev]); setMessage('Project created successfully.')
    } catch (e) { setMessage((e as Error).message) } finally { setBusy(false) }
  }
  async function uploadVideo() {
    if (!current || !video) return
    setBusy(true); setMessage('')
    try {
      const data = new FormData(); data.append('file', video)
      await request(`/api/projects/${current.project_id}/video`, {method:'POST', body:data})
      setVideo(null); await refresh(); setMessage('Original video uploaded.')
    } catch (e) { setMessage((e as Error).message) } finally { setBusy(false) }
  }
  async function uploadImages() {
    if (!current || images.length === 0) return
    setBusy(true); setMessage('')
    try {
      const data = new FormData(); images.forEach(f => data.append('files', f))
      await request(`/api/projects/${current.project_id}/characters/${encodeURIComponent(characterId)}/images`, {method:'POST', body:data})
      setImages([]); await refresh(); setMessage('Character reference images uploaded.')
    } catch (e) { setMessage((e as Error).message) } finally { setBusy(false) }
  }
  async function startAnalysis() {
    if (!current) return
    setBusy(true); setMessage('')
    try {
      const result = await request(`/api/projects/${current.project_id}/analysis`, {method:'POST'})
      await refresh(); setMessage(result.message || `Analysis status: ${result.status}`)
    } catch (e) { setMessage((e as Error).message) } finally { setBusy(false) }
  }
  async function exportPackage() {
    if (!current) return
    try {
      const data = await request(`/api/projects/${current.project_id}/package`)
      const blob = new Blob([JSON.stringify(data, null, 2)], {type:'application/json'})
      const url = URL.createObjectURL(blob); const a = document.createElement('a')
      a.href = url; a.download = `${current.name.replace(/[^a-z0-9-_]/gi,'_')}-package.json`; a.click()
      URL.revokeObjectURL(url)
    } catch (e) { setMessage((e as Error).message) }
  }

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><Clapperboard size={21}/></div><div><b>VideoForge</b><span>AI STUDIO</span></div></div>
      <div className="workspace-label">WORKSPACE</div>
      <button className={`nav-item ${tab==='analyzer'?'active':''}`} onClick={()=>setTab('analyzer')}><ScanSearch size={18}/> Video Analyzer</button>
      <button className={`nav-item ${tab==='studio'?'active':''}`} onClick={()=>setTab('studio')}><Users size={18}/> Character Swap Studio</button>
      <div className="side-divider"/>
      <div className="workspace-label">PROJECTS <span>{projects.length}</span></div>
      <div className="project-list">{projects.slice(0,6).map(p=><button key={p.project_id} className={`project-link ${current?.project_id===p.project_id?'selected':''}`} onClick={()=>setActive(p)}><FolderOpen size={15}/><span>{p.name}</span></button>)}</div>
      <div className="sidebar-bottom"><div className="status-card"><span className={`status-dot ${apiOnline?'online':''}`}/><div><b>{apiOnline?'API Connected':'API Offline'}</b><small>{apiOnline?'Backend responding':'Start FastAPI server'}</small></div></div><div className="version">VIDEOF​​ORGE AI · PHASE 01</div></div>
    </aside>

    <main className="main">
      <header className="topbar"><div className="breadcrumbs">Workspace <span>/</span> {tab==='analyzer'?'Video Analyzer':'Character Swap Studio'}</div><div className="top-actions"><span className="beta-pill">FOUNDATION BUILD</span><button className="icon-btn" title="Settings"><Settings2 size={18}/></button><div className="avatar">VF</div></div></header>
      <div className="content">
        <section className="welcome-row"><div><div className="eyebrow"><Sparkles size={14}/> AI VIDEO PRODUCTION WORKSPACE</div><h1>{tab==='analyzer'?'Understand every frame.':'Rebuild the cast. Keep the performance.'}</h1><p>{tab==='analyzer'?'Upload a source video and prepare its structured analysis package.':'Map original characters to multi-angle references and prepare a reconstruction project.'}</p></div><div className="api-chip"><span className={`status-dot ${apiOnline?'online':''}`}/>{apiOnline?'System operational':'Waiting for API'}</div></section>

        <div className="stats-grid">
          <div className="stat-card"><div className="stat-icon purple"><FolderOpen size={18}/></div><div><strong>{projects.length}</strong><span>Projects</span></div></div>
          <div className="stat-card"><div className="stat-icon blue"><FileVideo size={18}/></div><div><strong>{current?.source_video?'1':'0'}</strong><span>Source videos</span></div></div>
          <div className="stat-card"><div className="stat-icon green"><Users size={18}/></div><div><strong>{current?.characters?.length || 0}</strong><span>Character profiles</span></div></div>
          <div className="stat-card"><div className="stat-icon orange"><Activity size={18}/></div><div><strong>{current?.analysis?.status==='completed'?'Ready':'—'}</strong><span>Analysis status</span></div></div>
        </div>

        {!current && <section className="panel create-panel"><div className="panel-icon"><Layers3/></div><div className="panel-copy"><h2>Create your first project</h2><p>Start with a project workspace for your original video and replacement characters.</p><div className="inline-form"><input value={name} onChange={e=>setName(e.target.value)} placeholder="Project name"/><button className="primary-btn" onClick={createProject} disabled={busy || !name.trim()}>{busy?'Creating…':'Create project'} <ArrowRight size={16}/></button></div></div></section>}

        {current && <div className="work-grid">
          <section className="panel main-panel">
            <div className="panel-heading"><div><div className="section-kicker">{tab==='analyzer'?'STEP 01 · SOURCE INGESTION':'STEP 02 · CHARACTER ASSETS'}</div><h2>{tab==='analyzer'?'Original video':'Replacement character'}</h2></div><span className="step-number">{tab==='analyzer'?'01':'02'}</span></div>
            {tab==='analyzer' ? <>
              <label className="dropzone"><input type="file" accept="video/mp4,video/quicktime,video/webm,video/x-matroska,.avi" onChange={e=>setVideo(e.target.files?.[0]||null)}/><div className="upload-icon"><CloudUpload size={25}/></div><b>{video?video.name:'Drop your video here'}</b><span>{video?fmt(video.size):'MP4, MOV, MKV, WEBM or AVI · up to 2 GB'}</span><em>Browse files</em></label>
              {current.source_video && <div className="file-row"><div className="file-icon"><FileVideo/></div><div className="file-info"><b>{current.source_video.filename}</b><small>{fmt(current.source_video.size_bytes)} · Uploaded source</small></div><CheckCircle2 className="success-icon"/></div>}
              <div className="action-row"><button className="primary-btn" disabled={!video||busy} onClick={uploadVideo}>{busy?'Uploading…':'Upload original video'} <ArrowRight size={16}/></button><button className="secondary-btn" disabled={!current.source_video||busy} onClick={startAnalysis}><WandSparkles size={16}/> Prepare analysis</button></div>
              <div className="notice"><ShieldCheck size={17}/><span>Phase 1 stores the source safely and creates a project record. AI scene analysis will be connected in Phase 2.</span></div>
            </> : <>
              <div className="field"><label>Character ID</label><input value={characterId} onChange={e=>setCharacterId(e.target.value)} placeholder="character_01"/></div>
              <label className="dropzone compact"><input type="file" accept="image/png,image/jpeg,image/webp" multiple onChange={e=>setImages(Array.from(e.target.files||[]))}/><div className="upload-icon"><ImagePlus size={25}/></div><b>{images.length?`${images.length} reference image(s) selected`:'Upload multi-angle references'}</b><span>Front, back, left, right, 3/4 and expression views · PNG/JPG/WEBP</span><em>Choose images</em></label>
              {images.length>0 && <div className="selected-files">{images.map((f,i)=><span key={i}>{f.name}</span>)}</div>}
              <div className="action-row"><button className="primary-btn" disabled={!images.length||busy||!characterId.trim()} onClick={uploadImages}>{busy?'Uploading…':'Upload character references'} <ArrowRight size={16}/></button></div>
              <div className="notice"><ShieldCheck size={17}/><span>Upload clear, well-lit views of the same character. 3D model reconstruction and rigging are not part of this foundation build.</span></div>
              {current.characters?.map(c=><div className="character-row" key={c.character_id}><div className="character-avatar"><Users/></div><div><b>{c.character_id}</b><small>{c.images.length} reference images</small></div><span className="mini-tag">IMPORTED</span></div>)}
            </>}
          </section>
          <aside className="panel side-panel">
            <div className="panel-heading"><div><div className="section-kicker">PROJECT OVERVIEW</div><h2>{current.name}</h2></div><span className="project-badge">ACTIVE</span></div>
            <div className="check-list">
              <div className="check-item"><span className={current.source_video?'check done':'check'}>{current.source_video?<CheckCircle2 size={15}/>:1}</span><div><b>Source video</b><small>{current.source_video?'Uploaded':'Waiting for upload'}</small></div></div>
              <div className="check-item"><span className={current.characters?.length?'check done':'check'}>{current.characters?.length?<CheckCircle2 size={15}/>:2}</span><div><b>Character references</b><small>{current.characters?.length?'References added':'Optional at this step'}</small></div></div>
              <div className="check-item"><span className="check">3</span><div><b>AI analysis</b><small>{current.analysis?.status||'Not started'}</small></div></div>
              <div className="check-item"><span className="check">4</span><div><b>Reconstruction</b><small>Coming in later phase</small></div></div>
            </div>
            <button className="export-btn" onClick={exportPackage}><Layers3 size={16}/> Export project package</button>
            <div className="side-foot"><Gauge size={15}/> Project package uses shared JSON schema v1.0</div>
          </aside>
        </div>}

        {message && <div className={`toast ${message.toLowerCase().includes('error')||message.toLowerCase().includes('failed')?'error':''}`}>{message}<button onClick={()=>setMessage('')}>×</button></div>}
        <section className="bottom-info"><div className="info-icon"><AudioLines size={18}/></div><div><b>Original-first workflow</b><p>VideoForge keeps the uploaded source as the reference master. Future processing stages will target only selected character regions wherever technically possible.</p></div><div className="phase-label">PHASE 01</div></section>
        <footer>© 2026 VideoForge AI <span>Built for controlled video reconstruction</span></footer>
      </div>
    </main>
  </div>
}
