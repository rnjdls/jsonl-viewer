# Pause/Resume Ingestion Toggle

## Summary
- Add a UI toggle that pauses and resumes **background ingestion**.
- Works for both ingest modes:
  - **File mode**: pauses the scheduled poll/tail loop.
  - **Kafka mode**: pauses by stopping the Kafka listener container; resumes by starting it.
- Paused state is **in-memory only** (resets to “running” on backend restart).
- While paused, existing admin actions (**Reload File**, **Delete All**) remain enabled and do **not** auto-resume ingestion.

## UX Details
- Location: `TopBar` action group beside the existing **Reload File** / **Delete All** buttons.
- Button behavior:
  - When running: label `Pause`
  - When paused: label `Resume`
  - Disabled when no active source (`stats.filePath` is null) or while request is in-flight.
- Status display:
  - Add a chip in `TopBar` meta area: `ingest paused` / `ingest running`.
- Error handling:
  - Reuse the existing app-level error banner when pause/resume fails.

## API Changes
- Extend `GET /api/stats` response:
  - Add `ingestPaused: boolean`
  - Existing fields unchanged.
- Add admin endpoints:
  - `POST /api/admin/pause` → `{ "status": "ok" }`
  - `POST /api/admin/resume` → `{ "status": "ok" }`
- Update `README.md` “API Endpoints” section:
  - Document `ingestPaused` in `/api/stats`
  - Add `/api/admin/pause` and `/api/admin/resume`

## Backend Implementation (Spring Boot)
### 1) In-memory pause state
- Add a singleton component `IngestPauseState` (e.g., `AtomicBoolean paused`):
  - `boolean isPaused()`
  - `void pause()` (idempotent)
  - `void resume()` (idempotent)

### 2) Admin service contract
- Extend `IngestAdminService` with:
  - `void pause()`
  - `void resume()`
- Update `ModeAwareIngestAdminService`:
  - In all modes: set `IngestPauseState` paused/unpaused.
  - In **Kafka mode**:
    - `pause()`: stop the Kafka listener container for `KafkaIngestService.LISTENER_ID`.
    - `resume()`: start the container if present and not running.
  - Implementation note: prefer encapsulating container start/stop in `KafkaIngestService` (e.g., `pauseListener()` / `resumeListener()`) so the stop-latch logic stays in one place; `ModeAwareIngestAdminService` calls into it via its existing `ObjectProvider<KafkaIngestService>`.

### 3) Wire new endpoints
- In `JsonlController`:
  - Inject `IngestPauseState`.
  - Add handlers for `POST /admin/pause` and `POST /admin/resume` calling `IngestAdminService`.
  - Add `ingestPaused` to the `StatsResponse` construction.

### 4) Respect pause during ingestion
- File mode (`JsonlIngestService`):
  - In `pollFile()`: if `pauseState.isPaused()` return early.
  - Optional but recommended: make pause take effect quickly during a long ingest pass by checking `pauseState.isPaused()` periodically in the ingest read loop (e.g., at batch boundaries). When detected:
    - Persist any in-flight batch already accumulated.
    - Stop reading further bytes and save `IngestState` at the last complete-line offset.
- Kafka mode (`KafkaIngestService`):
  - Pause/resume is handled by stopping/starting the listener container.
  - Ensure `resetToEnd()` and `reloadFromBeginning()` do not restart the container when paused:
    - When the reset/reload completes, only restart if it was previously running **and** `!pauseState.isPaused()`.

## Frontend Implementation (React)
- `frontend/src/utils/api.js`
  - Add `pauseIngestion()` and `resumeIngestion()` wrappers:
    - `POST /admin/pause`
    - `POST /admin/resume`
- `frontend/src/App.jsx`
  - Extend `actionState` with `pauseToggle: boolean`.
  - Add `handlePauseToggle()`:
    - If `stats.ingestPaused` → call `resumeIngestion()`, else `pauseIngestion()`.
    - Then `refreshStats()` (counts/preview state unchanged).
  - Pass new props to `TopBar`: `ingestPaused`, `onPauseToggle`, `pauseToggleLoading`.
- `frontend/src/components/TopBar/TopBar.jsx` + CSS
  - Render the toggle button.
  - Render the status chip in the meta section.

## Edge Cases / Semantics
- “Pause” is best-effort: it does not cancel an already-running DB transaction mid-flight, but should stop future ingestion quickly.
- When paused:
  - `/api/admin/reset` and `/api/admin/reload` remain callable and do not resume background ingestion.
  - Kafka listener remains stopped after reset/reload.
- Backend restart:
  - Pause state resets to running (since state is in-memory).
  - Kafka listener follows normal startup behavior.

## Test Plan
### Backend unit tests (JUnit)
- `JsonlIngestService`:
  - When paused, `pollFile()` returns without persisting entries and without advancing ingest state.
  - (If implementing in-loop checks) verify a long ingest pass can stop early when paused at a batch boundary and persists a consistent `byteOffset`.
- `JsonlController`:
  - `/api/admin/pause` calls `IngestAdminService.pause()`.
  - `/api/admin/resume` calls `IngestAdminService.resume()`.
  - `/api/stats` includes `ingestPaused` reflecting `IngestPauseState`.

### Manual smoke checks (Docker Compose)
- Start: `docker compose up --build` and confirm backend + UI start.
- File mode (with a growing file, e.g., mock generator compose):
  - Verify `totalCount` increases while running.
  - Click `Pause` and verify counts stop increasing.
  - Click `Resume` and verify counts increase again.
  - While paused, run `Reload File` / `Delete All` and confirm ingestion remains paused afterward.
- Kafka mode (if you have a dev broker):
  - Verify pause stops consumption and resume restarts it.

## Assumptions
- A single global pause flag is sufficient (only one active source is used by the UI at a time).
- No auth is required for admin endpoints (local-first dev tool).
- UI polling `/api/stats` every ~3s is sufficient to reflect paused state changes promptly.

