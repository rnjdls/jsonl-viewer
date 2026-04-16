# Remove Timestamp Filter And Narrow Field Index To Root Header Scalars

## Summary
- Remove the timestamp range filter end-to-end from the backend API, React filter state/UI, request payloads, and docs.
- Stop walking the full parsed JSON tree for `jsonl_entry_field_index`.
- Only index leaf scalar descendants under the top-level `header` and `headers` objects.
- Keep the existing field filter, but narrow it to indexed header/header leaf fields only. Full-text search, count-first behavior, and keyset preview pagination remain unchanged.

## Public/API Changes
- Remove timestamp filter support from `/api/filters/count` and `/api/filters/preview` request payloads and frontend UI.
- Narrow field filter semantics so `fieldPath` refers to a leaf key found under indexed top-level `header` or `headers` objects, not any key anywhere in the JSON tree.
- Keep preview pagination, count polling, and response shapes unchanged for the remaining filter types.

## Implementation Changes
- Backend filter/API:
  - Remove timestamp filter normalization and SQL generation from `FilterService`.
  - Remove timestamp-specific request fields from `FilterSpec`, `FilterCountRequest`, and `PreviewRequest`.
  - Remove `JsonlController` timestamp-filter validation and any timestamp-specific bad-request path.
  - Keep only `field` and `text` filter types active in the backend.
- Backend indexing:
  - Update `JsonFieldIndexExtractor` so it only inspects top-level `header` and `headers`.
  - Recurse only within those root objects.
  - Emit `jsonl_entry_field_index` rows only for leaf primitives: `string`, `number`, `boolean`, and `null`.
  - Skip arrays and objects entirely, including empty containers.
  - Keep `field_key` as the leaf key and `field_path` as the rooted path, for example `headers.eventTime`.
  - Stop parsing and storing timestamp values in the field index.
- Schema and rebuild:
  - Add a Flyway migration that drops the timestamp-specific field-index DB index and removes `value_ts`.
  - Mark existing sources for rebuild by resetting `indexed_revision` and `ingest_status`.
  - Reuse `FieldIndexBackfillService` to rebuild existing `jsonl_entry_field_index` rows with the new extractor scope.
- Frontend:
  - Remove timestamp filter constants, filter state, payload mapping, and UI from `App.jsx`, `SearchBar`, `useJsonlSearch`, and related search utilities.
  - Remove the `+ Timestamp Range` action from the filter bar.
  - Update field filter labels/placeholders/help text so the UI clearly describes header-only indexed matching.
- Docs:
  - Update `README.md` feature list, API contract, filter semantics, and data-model description to reflect the removed timestamp filter and the reduced field-index scope.

## Behavioral Impact
- Field filters no longer match keys outside the top-level `header` or `headers` objects.
- `EMPTY` and `NOT_EMPTY` no longer apply to object or array containers because container rows are no longer indexed.
- Full-text search remains the fallback for whole-record searching and non-header content.
- Timestamp range filtering is removed completely.

## Test Plan
- Backend unit tests:
  - Update `FilterServiceTest` to cover only `field` and `text` filters.
  - Update `JsonFieldIndexExtractorTest` to verify indexing under top-level `header` and `headers`, nested scalar leaves, nulls, empty strings, and skipped arrays/objects/non-header fields.
  - Add or update migration coverage to verify timestamp-specific schema cleanup and source rebuild marking.
- Frontend tests:
  - Update `src/App.test.jsx` to assert timestamp controls are absent and count/preview payloads only include `field` and `text` filters.
- Validation:
  - Run `cd backend && mvn test`.
  - Run `cd frontend && npm test -- --run src/App.test.jsx`.
  - Run `docker compose up --build` and verify ingest, counts, field filtering, and preview still work after reindex.

## Assumptions
- “headers/header object” means only the top-level `header` and top-level `headers` properties.
- Field matching remains leaf-key based, not full dot-path based.
- Introducing a slower whole-tree fallback query for field filtering is out of scope.
