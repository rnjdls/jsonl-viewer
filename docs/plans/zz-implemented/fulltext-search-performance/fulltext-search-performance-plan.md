# Full-Text Search Performance Plan

## Summary

This plan targets slow full-text search preview requests that can time out once the dataset grows to roughly 60k+ rows. The fix has two parts:

1. Raise the synchronous preview query timeout from `20s` to `5m` so legitimate long-running requests stop failing immediately.
2. Replace full-text search against `parsed::text` with an ingest-built per-entry search document so query-time work becomes smaller, cleaner, and more selective.

The count-job timeout stays at `10m` because count jobs already run asynchronously and are not the main user-visible timeout path.

## Current Problem

The current text filter builds SQL like:

```sql
to_tsvector('simple', parsed::text) @@ plainto_tsquery('simple', :query)
```

That search shape has two problems:

- It indexes the JSON serialization, not a purpose-built search document.
- The preview path still has to join matching candidate ids back to `jsonl_entry`, sort them, and page them synchronously.

At larger row counts, broad searches can produce large candidate sets and make the preview request exceed the current `20s` statement timeout.

## Implementation Changes

### 1. Increase preview timeout to 5 minutes

Update preview-query timeout settings only:

- Change `app.preview-statement-timeout` in `backend/src/main/resources/application.yml` from `20s` to `5m`.
- Change `APP_PREVIEW_STATEMENT_TIMEOUT` in `docker-compose.yml` from `20s` to `5m`.

Do not change:

- `APP_COUNT_JOB_STATEMENT_TIMEOUT` in `docker-compose.yml`
- `app.count-job-statement-timeout` in `backend/src/main/resources/application.yml`

Do not change frontend proxy timeout defaults in this pass because `API_PROXY_READ_TIMEOUT` is already `300s` and already matches 5 minutes.

### 2. Build a dedicated search document during ingest

Add a dedicated search column to `jsonl_entry`. Prefer:

- `search_vector TSVECTOR`

Fallback option if implementation simplicity wins:

- `search_text TEXT` plus a GIN index on `to_tsvector('simple', search_text)`

The ingest path should populate that search document when each row is stored, alongside the existing field-index extraction in `JsonlIngestService`.

### 3. Search document rules

The search document is **per entry**, not globally unique.

- Every `jsonl_entry` gets its own search document.
- If the same scalar value appears in multiple rows, it is stored in each row's search document.
- Repeated scalar values may be deduplicated within the same row before building the final document.

Default document contents:

- include leaf field-path tokens
- include scalar values from the parsed JSON
- include scalar values inside arrays
- include text, numbers, and booleans
- exclude JSON punctuation and serialization noise
- exclude null values
- exclude empty strings
- exclude `raw_line`
- exclude `parse_error`

Recommended default behavior:

- Recursively walk the entire parsed JSON body, not just `header` or `headers`.
- For each leaf scalar, add both the leaf path and the scalar value to the per-entry token set.
- Deduplicate tokens within the row before building the stored document.

Example:

```json
{
  "headers": { "status": "500", "traceId": "abc" },
  "message": "worker failed to connect to gateway"
}
```

One row-level document may become:

```text
headers status 500 traceId abc message worker failed connect gateway
```

This preserves useful field-name discoverability without indexing raw JSON syntax.

### 4. Backfill existing rows

Add a backfill flow for existing data, modeled after the existing field-index backfill pattern:

- create a migration for the new search column and its index
- mark existing sources as needing search-document backfill
- process rows in batches
- populate the search document for rows that already exist
- mark the source ready once backfill reaches the current revision

The backfill should be restart-safe and should not require loading all rows into memory.

### 5. Switch text search to the new column

Update the text-filter SQL in `FilterService` so it no longer searches `parsed::text`.

Preferred query shape:

```sql
search_vector @@ websearch_to_tsquery('simple', :query)
```

If the implementation keeps `plainto_tsquery` for compatibility in the first pass, the search column change should still happen first.

### 6. Add a text-only preview fast path

For requests that contain only one text filter and no field filters:

- bypass the generic candidate-id subquery path
- query `jsonl_entry` directly with the text predicate, sort, and `LIMIT`
- keep the existing candidate-id `UNION` and `INTERSECT` flow for mixed `text + field` filters

This reduces preview overhead for the common case where the user is only doing a full-text search.

## Public Interfaces

No frontend API request or response shape changes are required.

Internal changes are allowed in:

- database schema
- ingest services
- backfill services
- repository query construction

User-facing search behavior should remain compatible at the filter API level. The main intended behavior change is internal: full-text search should match against a cleaner, per-entry search document instead of raw serialized JSON.

## Test Plan

### Backend tests

Add or update backend coverage for:

- search-document extraction from parsed JSON
- per-entry token deduplication behavior
- migration/backfill readiness behavior
- text-filter SQL generation against the dedicated search column
- text-only preview fast-path selection
- mixed `text + field` filters still using the generic path

### Performance scenarios

Measure before and after for:

- broad full-text search on 60k+ rows
- selective full-text search on 60k+ rows
- text-only preview request
- mixed text + field filter preview request

### Regression scenarios

Confirm:

- preview pagination order remains stable
- preview requests can run for up to 5 minutes before backend statement timeout
- count jobs remain asynchronous with the existing 10-minute timeout
- backfill completes without loading the full dataset into memory

## Assumptions and Defaults

- Branch for implementation work: `fix/fulltext-search-performance`
- Plan file path: `docs/plans/fulltext-search-performance/fulltext-search-performance-plan.md`
- Timeout increase applies to preview-query execution only
- The new search document is row-scoped, not a global unique token store
- Duplicate tokens across different rows are expected and required
- Duplicate tokens within a single row may be collapsed before storage
