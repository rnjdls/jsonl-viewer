# Kafka Rows: Key + Headers Preview, Lazy-load Full Body Per Row

## Summary
- Create feature branch `feature/kafka-key-headers-lazy-body`.
- Change preview rendering to be Kafka-friendly:
  - default compact view shows **`key` + `headers`**
  - full JSON body is fetched **per row on demand** (lazy, cached)
- Reduce preview payload size by **not returning full `parsed` and full `raw_line`** for every row.

## Dependency (locked)
- This feature is intended to land **after** `feature/server-pagination-sort` (it modifies the preview response shape again).
- Implement this branch on top of the commit that already has sorted cursor pagination.

## API Changes
### `POST /api/filters/preview`
- Preview rows should include only:
  - `id`, `lineNo`, `ts`
  - `key` (JSON) = `parsed->'key'` (nullable)
  - `headers` (JSON) = `parsed->'headers'` (nullable; already decoded at ingest when possible)
  - `error` (string) = `parse_error` (nullable)
  - `rawSnippet` (string, optional) = `LEFT(raw_line, 500)` for parse-error rows only
  - `rawTruncated` (boolean, optional) for parse-error rows only
- Do **not** include `parsed` or full `raw` in preview rows.

### Row detail endpoint (lazy load)
- Add `GET /api/entries/{id}`:
  - Response: `{ id, lineNo, ts, parsed, error }`
  - “Parsed JSON only” is the primary payload; `error` included when `parsed=null`.
- Add `GET /api/entries/{id}/raw`:
  - Response: `text/plain` raw line (full), for troubleshooting parse errors.

## Backend Implementation
- Repository changes:
  - Preview SQL selects:
    - `id, line_no, ts, (parsed->'key') AS key, (parsed->'headers') AS headers, parse_error`
    - plus `LEFT(raw_line, 500)` and `rawTruncated` only when `parse_error IS NOT NULL`
  - `GET /api/entries/{id}` selects `parsed` and `parse_error` (and `ts`) for the current file path.
  - `GET /api/entries/{id}/raw` selects `raw_line` for the current file path.
- Controller:
  - Keep file scoping: always constrain by configured `app.jsonl-file-path` (matches existing API behavior).

## Frontend Implementation
- Replace current always-full `JsonCard` usage with a Kafka-aware row component:
  - Compact view:
    - render `{ key, headers }` using `JsonValue`
    - show line label and ts (if available)
  - Actions:
    - “Load body” button triggers `GET /api/entries/{id}` and caches by row id
    - “Collapse” toggles back to compact view without refetch
  - Parse-error rows:
    - show `error` and `rawSnippet`
    - if `rawTruncated`, show “Load full raw” → `GET /api/entries/{id}/raw`

## Test Plan
### Manual
- Verify preview payload is smaller (no full parsed/raw per row).
- Expand a row and verify exactly one detail request is made and cached.
- Parse-error row shows snippet and can fetch full raw.

## Acceptance Criteria
- Preview list remains fast with large rows.
- User can scan `key` and `headers` quickly.
- Full body is fetched only when requested, per row.

## Assumptions
- Kafka envelope fields are root-level and literally named `key` and `headers`.
- Full body request returns `parsed` only (raw is separate endpoint).

