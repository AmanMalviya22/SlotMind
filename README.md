# SlotMind

SlotMind is a Spring Boot + React front-desk application for AI-assisted appointment re-scheduling. It automatically creates human-reviewable recommendations when a provider goes offline or a slot is cancelled.

## Run on Windows PowerShell

Prerequisites: Java 21, installed Maven 3.9+, and Node.js 20+ with npm. This repository does not currently contain a Maven Wrapper, so use the installed `mvn` command. Backend and frontend are intentionally independent processes.

Terminal 1:

```powershell
cd C:\Users\zycus\.cline\data\workspaces\chat\slotmind\backend
mvn spring-boot:run
```

Terminal 2:

```powershell
cd C:\Users\zycus\.cline\data\workspaces\chat\slotmind\frontend
npm ci
npm run dev
```

The frontend reads `VITE_API_BASE_URL` from `frontend/.env`; copy `frontend/.env.example` to `frontend/.env` only when the default needs changing. Open `http://localhost:5173`. The backend uses an in-memory H2 database and seeds the demo records at startup. Set `PRV-001` to `OFFLINE`; after a few seconds, suggestions for John Martinez and Emily Watson appear. Accept one to atomically book the replacement slot.

## API

`GET /api/providers`, `GET /api/appointments`, `GET /api/slots`, `GET /api/suggestions`; `POST /api/appointments`; `PATCH /api/providers/{id}/status`; `PATCH /api/slots/{id}/status`; `POST /api/appointments/{id}/suggest`; `PATCH /api/suggestions/{id}`.

The primary scheduling strategy attempts the company LiteLLM-compatible gateway when `SLOTMIND_LLM_API_KEY` is present, validates the returned candidate independently, and falls back to deterministic rule-based scheduling on missing credentials, gateway errors, timeouts, or invalid output. The rule-based fallback excludes cancelled/offline destinations and prefers matching specialties. The API key is never sent to the frontend.

### Optional company LLM gateway configuration

Request a rotated token from the hackathon organizer or HR before use. The token pasted into chat must be treated as exposed. In the same PowerShell session used to start the backend, configure only environment variables:

```powershell
$env:SLOTMIND_LLM_API_KEY = "PASTE_ROTATED_TOKEN_HERE"
$env:SLOTMIND_LLM_PRODUCT = "PC1"
$env:SLOTMIND_LLM_BASE_URL = "https://litellm-qc.zycus.net/v1"
$env:SLOTMIND_LLM_MODEL = "qwen-cursor"
# Only if the gateway owner confirms it is required:
# $env:SLOTMIND_LLM_COOKIE = "ROTATED_COOKIE_VALUE"
cd C:\Users\zycus\.cline\data\workspaces\chat\slotmind\backend
mvn spring-boot:run
```

The gateway uses `POST /chat/completions` with `Authorization: Bearer ...`, `product: PC1`, and JSON content. Do not put credentials in Java, application properties, frontend variables, Git, or logs. A real gateway request remains unverified until a rotated token is configured and network access is available.

## Verification

```powershell
cd C:\Users\zycus\.cline\data\workspaces\chat\slotmind\backend
mvn test

cd C:\Users\zycus\.cline\data\workspaces\chat\slotmind\frontend
npm ci
npm run build
```

Known limitation: no real gateway request is made during tests; mocked/fake gateway tests cover validation and fallback. SSE streaming is not used.