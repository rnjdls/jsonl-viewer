# Dynamic Timestamp Field (UI-Driven)

## Summary
- Make the timestamp field **request-driven** (from the Timestamp Range textbox) instead of `JSONL_TIMESTAMP_FIELD`.
- Timestamp range filtering **and** Preview “Timestamp” sorting both use the same textbox field path (supports dot-paths like `headers.eventTime`).
- Remove the TopBar `ts:` chip (no longer a server-configured concept).

## UX / Behavior
- Timestamp Range filter row:
  - The **field path** textbox is the single source of truth for which JSON field is treated as the timestamp.
  - Supports dot-paths (e.g. `headers.eventTime`).
  - If blank, default to `"timestamp"`.
- Only one Timestamp Range filter is allowed:
  - If one exists, disable/hide the “+ Timestamp Range” add button until it’s removed.
- Preview sorting:
  - When sorting by **Timestamp**, the backend uses the Timestamp Range field path if present; otherwise defaults to `"timestamp"`.
  - Pagination remains keyset-based and cursor-safe.
- TopBar:
  - Remove the `ts: <field>` chip entirely.

## API Changes
- `GET /api/stats`
  - Remove `timestampField` from the response.
- `POST /api/filters/count` and `POST /api/filters/preview`
  - Request payload shape stays the same:
    - Timestamp filter continues to send `{ type: "timestamp", fieldPath, from, to }`.
  - Preview cursor encoding changes for timestamp sorting:
    - Cursor will include the effective timestamp field path used for sorting (to prevent cursor reuse across different timestamp fields).

## Backend Implementation (Spring Boot + Postgres)

### 1) DB: extend the field index for dot-path + timestamp values
- Add Flyway migration `V2__field_index_timestamp_path.sql`:
  - `ALTER TABLE jsonl_entry_field_index ADD COLUMN IF NOT EXISTS field_path TEXT;`
  - `ALTER TABLE jsonl_entry_field_index ADD COLUMN IF NOT EXISTS value_ts TIMESTAMPTZ;`
  - Add index for range + ordering:
    - `CREATE INDEX IF NOT EXISTS jsonl_entry_field_index_path_ts_id_idx ON jsonl_entry_field_index (file_path, field_path, value_ts, entry_id);`
  - Force a rebuild of existing field index data so `field_path` and `value_ts` are populated:
    - `UPDATE ingest_state SET indexed_revision = 0, ingest_status = 'building' WHERE source_revision > 0;`
    - This triggers the existing `FieldIndexBackfillService` to regenerate rows from `jsonl_entry.parsed`.

### 2) Indexing: populate `field_path` and `value_ts`
- Update `JsonlEntryFieldIndex` entity:
  - Add `field_path` (String) and `value_ts` (Instant) fields + mappings.
- Update `JsonFieldIndexExtractor`:
  - While walking objects, compute `field_path` as a dot-path:
    - top-level: `status`
    - nested: `headers.eventTime`
  - Parse `value_ts` when the JSON value is a scalar that looks like a timestamp:
    - numbers: epoch seconds/millis
    - strings: RFC3339 / offset datetime / naive datetime (`YYYY-MM-DDTHH:mm:ss` or `YYYY-MM-DD HH:mm:ss`)
    - Treat naive datetimes as UTC (match `FilterService` timestamp parsing behavior).
  - Non-parseable values or non-scalars → `value_ts = null`.
- Stop relying on `JSONL_TIMESTAMP_FIELD`:
  - Remove `app.jsonl-timestamp-field` / `JSONL_TIMESTAMP_FIELD` from configuration/docs and remove usage in ingest parsing (timestamp is now derived via the field index on demand).

### 3) Timestamp range filtering uses the textbox field path
- Update `FilterService.buildFilterSql()` for `type=timestamp`:
  - Read and trim `filter.fieldPath`.
  - Default field path to `"timestamp"` when blank.
  - Build candidate IDs from `jsonl_entry_field_index`:
    - `SELECT DISTINCT entry_id AS id FROM jsonl_entry_field_index WHERE file_path = ?1 AND field_path = ?N`
    - Add bounds using `value_ts`:
      - `value_ts >= ?` for `from`
      - `value_ts <= ?` for `to`

### 4) Timestamp sorting + cursor stability
- Enforce single timestamp filter:
  - If a request contains more than one `filters[].type="timestamp"`, return `400 Bad Request`.
- Make Timestamp sort use the effective field path:
  - Effective field path:
    - If a timestamp filter exists: `trim(fieldPath)` or `"timestamp"` if blank
    - Else: `"timestamp"`
- Update preview query behavior for `sortBy=timestamp`:
  - Sort/paginate using `jsonl_entry_field_index.value_ts` for the effective `field_path`, with `id` tie-breaker.
  - Preserve `NULLS LAST` semantics (rows missing a sortable timestamp for that field path come after non-null timestamps).
  - Preview row’s `ts` should be the effective `value_ts` (so the UI timestamp display continues working).
- Update cursor encoding/decoding:
  - Extend the encoded cursor payload for timestamp sorting to include the effective timestamp field path (e.g. `tsFieldPath`).
  - Reject cursors whose `sortBy/sortDir/tsFieldPath` don’t match the request.

### 5) Remove timestamp field from `/api/stats`
- Update `StatsResponse` to remove `timestampField` and update `JsonlController.stats()` accordingly.

## Frontend Implementation (React)
- Remove server timestamp field plumbing:
  - Stop reading `stats.timestampField`.
  - Remove TopBar `ts:` chip and any props/state supporting it.
- Timestamp Range filter UI:
  - Keep the timestamp field path textbox.
  - Update placeholder text to no longer imply “(server)” configured field.
  - Ensure default field value is `"timestamp"` when creating a new timestamp filter.
  - Enforce only one timestamp filter at a time (disable/hide add button when one exists).

## Edge Cases / Semantics
- If the selected timestamp field is missing/unparseable for a row:
  - The row behaves like “no timestamp” for sorting (nulls last) and won’t match range bounds.
- Dot-paths traverse object keys only:
  - Array-index semantics are out of scope; timestamp fields are expected to be a single scalar per entry.

## Test Plan
- Backend (`cd backend && mvn test`):
  - Update/add tests for `JsonFieldIndexExtractor`:
    - `field_path` is correct for nested objects.
    - `value_ts` parses ISO/offset/epoch correctly.
  - Update `FilterServiceTest`:
    - Timestamp filter SQL uses `jsonl_entry_field_index.field_path` and `value_ts`.
  - Update `PreviewCursorCodecTest`:
    - Round-trip timestamp cursor includes `tsFieldPath`; mismatches are rejected.
- Smoke test:
  - `docker compose up --build`
  - Verify:
    - Backfill runs after migration and `searchStatus` returns to `ready`.
    - Timestamp Range works with dot-path (`headers.eventTime`).
    - Preview “Timestamp” sort paginates correctly with cursor.

## Acceptance Criteria
- Timestamp Range filter uses the textbox field path and supports dot-paths.
- Preview “Timestamp” sort uses the same field path and paginates with a cursor safely.
- TopBar no longer displays `ts: ...`.
- No dependency on `JSONL_TIMESTAMP_FIELD` for query behavior.
