# Large-Dataset Timeout Fix With Timeout Guardrails

## Summary
- Primary fix: remove expensive exact-count and recursive JSONB scan work from the synchronous request path for large datasets.
- Secondary fix: add explicit, longer proxy and query timeout settings so valid long-running operations do not die at default gateway limits.
- Exact counts become deferred background work; preview stays synchronous and fast.

## Public/API and Config Changes
- `GET /api/stats` returns aggregate counters from ingest state plus `sourceRevision` and `searchStatus` (`ready|building`).
- `POST /api/filters/count` returns `{ totalCount, matchCount, status, requestHash, sourceRevision, computedRevision, lastComputedAt }`; `status` is `pending|ready`, and `matchCount` is nullable while pending.
- Add `GET /api/filters/count/{requestHash}` for polling deferred exact-count completion.
- Add env-driven operational settings in `frontend/nginx.conf`, `backend/src/main/resources/application.yml`, and `docker-compose.yml`:
  - `API_PROXY_CONNECT_TIMEOUT=30s`
  - `API_PROXY_SEND_TIMEOUT=300s`
  - `API_PROXY_READ_TIMEOUT=300s`
  - `APP_PREVIEW_STATEMENT_TIMEOUT=20s`
  - `APP_COUNT_JOB_STATEMENT_TIMEOUT=10m`
  - `SPRING_MVC_ASYNC_REQUEST_TIMEOUT=305s` for any async count/status endpoint evolution
- If another upstream gateway or ingress exists outside this repo, set its read timeout to `>= 300s` so it is not stricter than nginx.

## Implementation Changes
- Adopt Flyway; switch JPA schema handling from `ddl-auto=update` to `validate`; move compatibility DDL into versioned migrations.
- Extend `ingest_state` with `total_count`, `parsed_count`, `error_count`, `source_revision`, `indexed_revision`, and `ingest_status`.
- Create `jsonl_entry_field_index` to store one row per key occurrence anywhere in the parsed JSON tree with `entry_id`, `file_path`, `field_key`, `value_text`, `value_type`, `is_null`, and `is_empty`.
- Enable `pg_trgm`; add:
  - `GIN (to_tsvector('simple', parsed::text))` on `jsonl_entry`
  - btree indexes on `jsonl_entry_field_index(file_path, field_key, entry_id)`, `(file_path, field_key, is_null, entry_id)`, `(file_path, field_key, is_empty, entry_id)`
  - `GIN (value_text gin_trgm_ops)` on `jsonl_entry_field_index`
- Add ingest-time field extraction so file and Kafka ingest write base rows, field-index rows, and aggregate counters in the same transaction, then bump `source_revision`.
- Add startup/backfill indexing for existing rows until `indexed_revision == source_revision`.
- Replace the current raw JSONB field-filter scan with candidate-id subqueries:
  - field filters from `jsonl_entry_field_index`
  - text filters from indexed `to_tsvector`
  - timestamp filters from `jsonl_entry`
  - combine with `INTERSECT` for `AND` and `UNION` for `OR`
- Keep preview cursor pagination unchanged, but run it against `jsonl_entry JOIN candidate_ids`.
- Add `filter_count_cache` keyed by `(file_path, request_hash)` and a single-flight background count worker so repeated polling never duplicates the same expensive job.
- Frontend changes:
  - stop refreshing `/filters/count` on every stats poll
  - refresh counts only on `Search` and while polling a pending `requestHash`
  - keep preview usable while counts are pending
  - hide or disable page-number selection until exact `matchCount` is ready
- Timeout guardrails:
  - nginx `/api/` location gets `proxy_connect_timeout`, `proxy_send_timeout`, and `proxy_read_timeout` from env
  - preview queries run with `APP_PREVIEW_STATEMENT_TIMEOUT`, intentionally below proxy read timeout
  - background count jobs run with `APP_COUNT_JOB_STATEMENT_TIMEOUT`
  - stats and lightweight endpoints stay on the fast path and should never approach proxy timeout

## Test Plan
- Unit-test field extraction for nested objects, arrays, repeated keys, nulls, empty values, numbers, and booleans.
- Add Postgres-backed integration tests for field/text/timestamp filter parity, AND/OR composition, preview pagination, and reset/reload cleanup.
- Add integration tests for backfill and count-cache invalidation across `sourceRevision` changes.
- Add frontend tests proving counts no longer refetch on each stats poll and pending counts do not block preview.
- Add timeout-focused tests:
  - nginx config renders the new timeout values
  - preview queries fail at the app statement timeout before nginx hits `proxy_read_timeout`
  - background count jobs can run longer without blocking preview
- Validate with 100k and 500k generated datasets: no normal search or preview path returns 504; exact counts may remain pending until the worker completes.

## Assumptions
- `300s` proxy read/send timeout is a safety net, not the target latency.
- Exact count may lag the latest ingest batch and is tied to `sourceRevision`.
- Do not use `server.tomcat.connection-timeout` as the long-request fix; it controls connection/request-line timing, not long-running handler execution.
- References:
  - Spring Boot application properties: `https://docs.spring.io/spring-boot/docs/3.2.10/reference/html/application-properties.html`
  - NGINX proxy module timeouts: `https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_read_timeout`
