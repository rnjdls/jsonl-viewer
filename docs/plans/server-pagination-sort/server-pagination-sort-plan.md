# Server-side Pagination + Sort (Preview)

## Summary
- Create feature branch `feature/server-pagination-sort`.
- Upgrade preview to **server-side pagination with sort** using **keyset/cursor** pagination (performance-friendly).
- UI uses **Next/Prev + Page N indicator** (no random jump-to-page).
- Default sort: **`timestamp` DESC (NULLS LAST)**.
- Sortable fields: **`timestamp`, `lineNo`, `id`** (always with a stable `id` tie-breaker).

## Decisions (locked)
- Paging UX: **cursor-based Next/Prev + Page indicator** (frontend maintains cursor history).
- Default sort: **timestamp desc**.
- Timestamp sort uses `NULLS LAST` (so `ts=null` rows don’t float to the top).

## API / DTO Changes
### `POST /api/filters/preview`
- Replace `cursorId` with an **opaque cursor token**.
- Request body (JSON):
  ```json
  {
    "filters": [ { "type": "field|text|timestamp", "...": "..." } ],
    "limit": 200,
    "sortBy": "timestamp|lineNo|id",
    "sortDir": "asc|desc",
    "cursor": "opaque-string-or-null"
  }
  ```
- Response body (JSON):
  ```json
  {
    "rows": [ /* preview rows */ ],
    "nextCursor": "opaque-string-or-null"
  }
  ```
- Backward-compat: none (frontend updated in this branch).

### Cursor encoding (decision-complete)
- Cursor is **Base64URL (no padding)** of a JSON object (UTF-8).
- Cursor JSON must include `sortBy` and `sortDir`; server returns **400** if they mismatch request.
- Cursor payload shapes:
  - `sortBy="id"`: `{ "sortBy":"id", "sortDir":"asc|desc", "id": 123 }`
  - `sortBy="lineNo"`: `{ "sortBy":"lineNo", "sortDir":"asc|desc", "lineNo": 456, "id": 123 }`
  - `sortBy="timestamp"`: `{ "sortBy":"timestamp", "sortDir":"asc|desc", "ts": "RFC3339-or-null", "id": 123 }`
- `ts` in cursor is the exact `Instant` string the backend emits (e.g. `2026-04-06T13:23:58.807619673Z`), or `null`.

## Backend Implementation
### Controller / DTOs
- Update `PreviewRequest` to include:
  - `sortBy` (String, optional; default applied in controller/service)
  - `sortDir` (String, optional; default applied)
  - `cursor` (String, optional)
  - keep `limit` and `filters`
- Update response to include `nextCursor` instead of `nextCursorId`.

### Repository (keyset query)
- Extend custom preview method to accept: `(sortBy, sortDir, decodedCursor, limit)`.
- Always apply `file_path = ?1` and filter SQL from `FilterService`.
- Always include a stable tie-breaker by `id` to avoid duplicate/skip across pages.

#### ORDER BY (exact)
- `id`: `ORDER BY id {ASC|DESC}`
- `lineNo`: `ORDER BY line_no {ASC|DESC}, id {ASC|DESC}`
- `timestamp`: `ORDER BY ts {ASC|DESC} NULLS LAST, id {ASC|DESC}`

#### Cursor WHERE (exact; forward-only pagination)
Let cursor values be `cId`, `cLineNo`, `cTs` (nullable).

- `id asc`:
  - `AND id > cId`
- `id desc`:
  - `AND id < cId`

- `lineNo asc`:
  - `AND (line_no > cLineNo OR (line_no = cLineNo AND id > cId))`
- `lineNo desc`:
  - `AND (line_no < cLineNo OR (line_no = cLineNo AND id < cId))`

- `timestamp asc NULLS LAST`:
  - if `cTs != null`:
    - `AND (ts > cTs OR (ts = cTs AND id > cId) OR ts IS NULL)`
  - if `cTs == null`:
    - `AND (ts IS NULL AND id > cId)`
- `timestamp desc NULLS LAST`:
  - if `cTs != null`:
    - `AND (ts < cTs OR (ts = cTs AND id < cId) OR ts IS NULL)`
  - if `cTs == null`:
    - `AND (ts IS NULL AND id < cId)`

### Indexing (to keep sort fast)
- Add DB index via JPA annotation:
  - `jsonl_entry(file_path, line_no, id)` for `lineNo` sorts.
- Keep existing:
  - `jsonl_entry(file_path, id)`
  - `jsonl_entry(file_path, ts)`

## Frontend Implementation
- Add sort UI in Preview header:
  - `Sort by`: `Timestamp | Line | ID`
  - `Direction`: `Asc | Desc`
- Replace `previewCursor` (number) with:
  - `cursorHistory: [null, cursorForPage2, cursorForPage3, ...]`
  - `pageIndex` (0-based; displayed as Page `pageIndex+1`)
- Requests:
  - Page 1 uses `cursor=null`
  - Next page uses last received `nextCursor`
  - Prev page uses `cursorHistory[pageIndex - 1]`
- On filter apply or sort change:
  - reset to Page 1 and clear history.

## Test Plan
### Backend (unit)
- Cursor encoding/decoding:
  - round-trip for `id`, `lineNo`, `timestamp` (including `ts=null`).
  - mismatch between cursor `sortBy/sortDir` and request returns 400.
- Query builder (string/params) for each sort mode contains the correct ORDER BY and cursor predicate.

### Manual
- Verify each sort option returns correctly ordered rows.
- Verify Next then Prev returns you to the previous set without duplicates.
- Verify switching sort resets to Page 1.

## Acceptance Criteria
- Preview is navigable via Next/Prev without duplicates/skips (stable ordering).
- Sorting works for `timestamp`, `lineNo`, and `id`.
- Default is timestamp DESC with NULLS LAST.

## Assumptions
- Preview remains “count-first”: counts endpoint unchanged.
- Prev page is implemented client-side using stored cursors (no backend “prev cursor” required).

