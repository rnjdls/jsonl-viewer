# JSONL Viewer v2 — AGENT Guide

## Overview
Local-first JSONL viewer optimized for huge files: backend ingests + filters in Postgres; UI is count-first with a small paged preview.

## Tech stack
- Frontend: Vite + React + CSS (served by Nginx in prod)
- Backend: Java 21 + Spring Boot (Web, Data JPA) + Jackson
- Data: Postgres 16 (JSONB + native queries)
- Infra: Docker Compose

## Repo layout
- `backend/`: Spring Boot API + ingestion + DB access
- `frontend/`: Vite/React UI + Nginx config + Dockerfile
- `data/`: Mounted JSONL files (`sample.jsonl`, `generated.jsonl`); avoid committing large/volatile data
- `docs/`: Plans/notes (implemented plans live in `docs/plans/zz-implemented/` and always skip browsing the implemented folder)
- `mock-generator/`: Optional Spring Boot service that appends to `data/generated.jsonl`

## Plans workflow
- Before implementing a plan (i.e., before any code changes), create a fresh category branch for the work (e.g., `feature/<slug>`, `fix/<slug>`, `chore/<slug>`, `docs/<slug>`).
- Plan markdown files live under `docs/plans/`; once a plan is implemented and verified (tests/smoke check pass), move the plan file into `docs/plans/zz-implemented/`.

## Architecture rules
- UI never loads full files; it requests counts + preview pages from `/api/*`.
- Preview pagination stays keyset-based using an opaque cursor (`sortBy/sortDir` + stable `id` tie-breaker).
- Ingestion is restart-safe via persisted offsets (`ingest_state`) and resets when the file shrinks.
- New/changed filters must be updated in both backend (`FilterService`) and frontend (payload mapping in `frontend/src/App.jsx` + UI in `SearchBar`).

## Coding standards
- Keep changes small and behavior explicit (batching, pagination, no large in-memory loads).
- Backend: controllers thin; logic in services/repos; constructor injection; SLF4J logging.
- Frontend: functional components + hooks; keep API payload shape centralized.

## Testing (≥ 80% coverage)
- For every implemented feature/plan: run a quick boot smoke test (bring the stack up and confirm backend + UI start). If boot fails, immediately investigate logs, find the root cause, and implement a fix before moving on.
- Target: **≥ 80% line coverage** for backend + mock-generator core logic; add regression tests with bug fixes.
- Run: `cd backend && mvn test` / `cd mock-generator && mvn test`
- Frontend: no runner configured yet; if adding non-trivial logic, add tests + coverage (recommended: Vitest + React Testing Library).

## Common commands
- Full stack: `docker compose up --build`
- Full stack + generator: `docker compose -f docker-compose.yml -f docker-compose.generated.yml up --build`
- Frontend dev: `cd frontend && npm install && npm run dev`
