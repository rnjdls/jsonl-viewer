# Remove Field Filter And `jsonl_entry_field_index`

## Summary
- Remove the field-index storage and query path end-to-end.
- Remove the Field filter from the backend API, React filter state/UI, request payloads, tests, and docs.
- Keep text filtering, count-first behavior, deferred exact counts, and keyset preview pagination unchanged.
- Preserve reload/reset behavior by keeping the ingest state and search-text rebuild lifecycle coherent after the field-index code is deleted.

## Public/API Changes
- Narrow `/api/filters/count` and `/api/filters/preview` to text-only filters: `{ filtersOp, filters: [{ type: "text", query }] }`.
- Remove support for top-level legacy field payloads such as `fieldPath` and `valueContains`.
- Remove support for `filters[].type = "field"` plus `fieldPath`, `op`, and `valueContains`.
- Return `400 Bad Request` when a removed field-filter payload is sent, instead of silently ignoring it.
- Keep preview response shapes, count polling, admin endpoints, and pagination behavior unchanged.

## Implementation Changes
- Backend filter/API:
  - Remove field-filter normalization and field-index SQL generation from `FilterService`.
  - Keep only text filters active in normalized filter criteria and request hashing.
  - Remove field-filter members from `FilterSpec`, `FilterCountRequest`, and `PreviewRequest`.
  - Tighten request binding so removed field-filter members fail fast instead of being ignored.
  - Keep the single-text preview fast path and multi-text `AND` / `OR` filtering behavior.
- Backend ingest and rebuild:
  - Delete `JsonlEntryFieldIndex` and `JsonFieldIndexExtractor`.
  - Remove field-index persistence from both `JsonlIngestService` and `KafkaIngestService`.
  - Replace `FieldIndexBackfillService` with a search-text-only backfill service that rebuilds `jsonl_entry.search_text` for existing rows and then marks `indexed_revision = source_revision`.
  - Rename the pending-source repository query to match the remaining search-text backfill responsibility instead of field-index rebuilds.
  - Keep `indexed_revision`, `ingest_status`, and `searchStatus` semantics so the UI still shows `building` only while search text is being backfilled.
- Schema:
  - Add a new Flyway migration that drops `jsonl_entry_field_index` and its indexes.
  - Do not rewrite old migrations; keep historical migrations intact and make removal additive in the new migration.
  - Continue using `search_text` as the only derived search structure stored per entry.
- Frontend:
  - Remove the `+ Field` action, field-filter row, field-filter constants, and field-filter-specific search helpers.
  - Update `App.jsx` payload construction to emit only text filters.
  - Simplify `useJsonlSearch`, `frontend/src/utils/search.js`, and related tests/comments to text-only filtering.
- Docs:
  - Update `README.md` to remove all field-index and field-filter references.
  - Document text-only filter payloads and keep the existing admin reset/reload behavior notes.

## Delete All / Reload Behavior
- `POST /api/admin/reset` must still:
  - delete existing `jsonl_entry` rows for the active source
  - move file-mode ingest to the file end or Kafka mode to the newest committed offsets
  - zero counts and leave the source in a ready state
- `POST /api/admin/reload` must still:
  - clear existing `jsonl_entry` rows for the active source
  - reset file-mode ingest to byte offset `0` or Kafka mode to the beginning offsets
  - allow the source to rehydrate counts, preview, and search data from scratch
- Removing `jsonl_entry_field_index` must not introduce any extra cleanup dependency into either admin path.

## Test Plan
- Backend unit tests:
  - update `FilterServiceTest` to cover only text-filter normalization and SQL generation
  - add request-validation coverage proving removed field-filter payloads return `400`
  - keep preview-controller coverage for the single-text fast path and generic multi-text path
- Backend ingest/rebuild tests:
  - replace field-index backfill tests with search-text backfill tests that verify `search_text` rebuild plus `indexed_revision` and `ingest_status` updates
  - add migration coverage for the new drop-table migration
  - update file-mode and Kafka ingest tests to remove field-index mocks/dependencies
  - add or update regression coverage for reset/reload behavior after field-index removal
- Frontend tests:
  - assert the Field filter controls are gone
  - assert search payloads are text-only
  - keep clear-all, deferred-count, preview, reload, and delete-all coverage working with the simplified payload
- Validation:
  - run `cd backend && mvn test`
  - run frontend tests covering `App.test.jsx`
  - run `docker compose up --build` and verify ingest, text search, preview, `Delete All`, and `Reload File`

## Assumptions
- Field filtering is being removed completely rather than replaced with a slower whole-record SQL fallback.
- Existing clients should fail fast on removed field-filter payloads instead of receiving broader results silently.
- `searchStatus` should remain `ready | building`; `building` now refers only to `search_text` rebuild progress.
- Scope includes docs and test cleanup, but not changes inside `docs/plans/zz-implemented/`.
