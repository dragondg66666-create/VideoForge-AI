# VideoForge AI — Phase 1 Foundation

منصة أولية تجمع **Video Analyzer** و **Character Swap Studio** في مستودع واحد (Monorepo).
هذه المرحلة توفر واجهة React/TypeScript وخلفية FastAPI ونقاط API لرفع الفيديوهات وصور الشخصيات وإنشاء المشاريع.

> مهم: هذه نسخة تأسيسية قابلة للتشغيل، وليست محرك تحليل أو استبدال شخصيات بالذكاء الاصطناعي بعد. التحليل المتقدم، segmentation، motion tracking، retargeting، وrendering مراحل لاحقة تحتاج نماذج وأجهزة GPU.

## المتطلبات
- Node.js 20 أو أحدث
- Python 3.11 أو أحدث
- (اختياري) Docker + Docker Compose

## تشغيل سريع على الكمبيوتر

### 1) الخلفية
```bash
cd backend
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```
واجهة API: http://localhost:8000/docs

### 2) الواجهة — افتح Terminal آخر
```bash
cd frontend
npm install
npm run dev -- --host 0.0.0.0
```
افتح الرابط الذي يظهره Vite، غالباً http://localhost:5173

## التشغيل باستخدام Docker
من مجلد المشروع الرئيسي:
```bash
docker compose up --build
```
- Frontend: http://localhost:5173
- API: http://localhost:8000
- API docs: http://localhost:8000/docs

## التشغيل من الهاتف (Android / Termux)
1. ثبّت Termux من مصدر موثوق وثبّت Node.js وPython:
   ```bash
   pkg update && pkg upgrade
   pkg install nodejs python git
   ```
2. فك ضغط المشروع داخل مجلد يمكن الوصول إليه.
3. شغّل الخلفية:
   ```bash
   cd VideoForge-AI/backend
   pip install -r requirements.txt
   uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```
4. في جلسة Termux ثانية شغّل الواجهة:
   ```bash
   cd VideoForge-AI/frontend
   npm install
   npm run dev -- --host 0.0.0.0
   ```
5. افتح عنوان Vite الظاهر في المتصفح. إذا لم تعمل الواجهة بسبب إعدادات الهاتف أو الذاكرة، استخدم GitHub Codespaces أو جهاز كمبيوتر.

### ربط الواجهة بعنوان API
افتراضياً الواجهة تستخدم `http://localhost:8000`. لتغيير العنوان أنشئ `frontend/.env`:
```env
VITE_API_URL=http://YOUR_SERVER_IP:8000
```
لا تضع مفاتيح API سرية داخل ملفات الواجهة أو GitHub.

## رفع ZIP إلى GitHub من الهاتف
1. أنشئ مستودعاً جديداً على GitHub باسم `VideoForge-AI`.
2. ارفع ملف ZIP إلى جهازك ثم فك ضغطه باستخدام مدير الملفات.
3. افتح المستودع في GitHub من المتصفح.
4. اختر **Add file → Upload files**.
5. ارفع محتويات مجلد `VideoForge-AI` بعد فك الضغط (وليس ملف ZIP نفسه إذا أردت أن تظهر الملفات كمجلدات قابلة للتصفح).
6. اضغط **Commit changes**.
7. تأكد أن `README.md` ظاهر في الصفحة الرئيسية للمستودع.

إذا كان GitHub من الهاتف لا يسمح برفع مجلدات كثيرة دفعة واحدة، استخدم GitHub Codespaces أو تطبيق Git يدعم رفع المجلد، أو ارفع ZIP ثم فك ضغطه محلياً قبل الرفع. لا ترفع مجلدات `node_modules` أو `.venv`.

## هيكل المستودع
```text
VideoForge-AI/
├── frontend/                 # React + TypeScript + Vite
├── backend/                  # FastAPI
├── shared/schemas/            # عقد بيانات المشروع
├── engines/                   # أماكن محركات المعالجة القادمة
├── storage/                   # ملفات الرفع المحلية (مستثناة من Git)
├── docker/
├── docker-compose.yml
└── README.md
```

## API الحالية
- `GET /api/health` — فحص حالة الخادم
- `POST /api/projects` — إنشاء مشروع
- `GET /api/projects` — قائمة المشاريع
- `POST /api/projects/{project_id}/video` — رفع الفيديو الأصلي
- `POST /api/projects/{project_id}/characters/{character_id}/images` — رفع صور مرجعية للشخصية
- `GET /api/projects/{project_id}/package` — تنزيل حزمة JSON للمشروع
- `POST /api/projects/{project_id}/analysis` — إنشاء سجل مهمة تحليل تأسيسي (حالياً لا يشغّل نموذج AI)

## ملاحظات الأمان والتشغيل
- استخدم ملفات تملكها أو لديك إذن بتعديلها.
- الملفات تحفظ محلياً في `storage/uploads` في هذه النسخة.
- لا تضع أسرار أو مفاتيح Gemini في GitHub. أضفها لاحقاً كمتغيرات بيئة في الخادم.
- قبل نشره للعامة نحتاج مصادقة، حدود رفع، فحص ملفات، تخزين سحابي، قاعدة بيانات دائمة، وطابور مهام.
