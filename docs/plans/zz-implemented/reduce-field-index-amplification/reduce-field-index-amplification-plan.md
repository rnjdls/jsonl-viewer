# Reduce Field-Index Amplification And Raise Postgres WAL Size

## Summary
- Reduce `jsonl_entry_field_index` storage growth by keeping field rows for traversal/filter metadata, but stop storing serialized object and array subtrees in `value_text`.
- Populate `value_ts` only for timestamp-like scalar fields and ignore out-of-range values so ingest does not fail on invalid timestamp candidates.
- Rebuild existing field-index data once after deployment so prior rows are regenerated with the new extraction rules.
- Raise local Postgres `max_wal_size` in `docker-compose.yml` to reduce checkpoint pressure during ingest.

## Public/API and Config Changes
- No HTTP API changes.
- No database table shape changes.
- `docker-compose.yml` adds a Postgres runtime override for `max_wal_size=1GB`.

## Implementation Changes
- Update `JsonFieldIndexExtractor` so it still walks the full JSON object tree and still emits one `jsonl_entry_field_index` row per object-field occurrence.
- Keep `value_text` for scalar values only:
  - strings, numbers, booleans, nulls keep the current scalar text behavior
  - objects and arrays keep their metadata row but store `value_text = null`
- Keep recursion into nested objects and arrays so field existence, null, empty, and nested-path filtering still work.
- Preserve current behavior for scalar array elements: do not emit standalone rows for bare scalar items.
- Add selective timestamp extraction for scalar fields only:
  - attempt `value_ts` only when the terminal field token is timestamp-like
  - treat `timestamp`, `time`, `ts`, `date`, and names ending in `At` as timestamp-like
  - continue accepting ISO instant, offset datetime, local datetime interpreted as UTC, and epoch seconds/millis
  - if parsing fails or the parsed instant is outside Postgres `TIMESTAMPTZ` safe bounds, store `null`
- Keep root `timestamp` and nested header timestamps such as `headers.eventTime` working through the existing `field_path` + `value_ts` filter SQL.
- Add a Flyway migration that marks existing sources for field-index rebuild by setting `indexed_revision = 0` and `ingest_status = 'building'` where `source_revision > 0`.
- Reuse `FieldIndexBackfillService` to delete and rebuild `jsonl_entry_field_index` rows on next startup.
- Update `README.md` to document the reduced indexing strategy and the Postgres WAL override.

## Behavioral Impact
- Field filters based on `field_key`, `field_path`, `is_null`, and `is_empty` continue to work for the whole JSON object tree.
- Field `contains` matching no longer works against serialized object or array container values because those subtrees are no longer copied into `value_text`.
- Full-text search over `parsed::text` remains unchanged.
- Timestamp filtering continues to work for supported scalar timestamp paths, but no longer tries to interpret arbitrary scalar fields such as status codes or large numeric IDs as timestamps.

## Test Plan
- Update `JsonFieldIndexExtractorTest` to verify:
  - scalar fields still store `value_text`
  - object and array container rows keep metadata but have `value_text = null`
  - `status: 200` no longer produces `value_ts`
  - `timestamp`, `headers.eventTime`, and `ts` still populate `value_ts`
- Add regression tests for out-of-range epoch and ISO values producing `null` `value_ts`.
- Add migration/backfill coverage proving existing sources are marked for rebuild and return to `ready` after backfill.
- Run `cd backend && mvn test`.
- Run `docker compose up --build` and verify:
  - backend and UI boot successfully
  - ingest completes without the timestamp out-of-range SQL failure
  - timestamp filtering still works for supported paths
  - Postgres starts with the configured `max_wal_size`

## Assumptions
- Keeping container rows is required so nested field existence/null/empty filters remain supported.
- Losing substring search against serialized object and array containers is acceptable.
- The compose-level WAL override is intended for this repo's local/prod-like Docker deployment and does not cover external Postgres infrastructure.
