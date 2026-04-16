# JSONL Viewer (Spring Boot + Postgres + React)

A local-first JSONL viewer optimized for large files by moving parsing and filtering to a Spring Boot backend backed by Postgres. The UI is count-first and uses a lazy preview to avoid rendering millions of rows in the browser.

## Features

- Backend ingests a JSONL file from a configured path and tails new lines.
- Optional Kafka ingest mode consumes one JSON object per Kafka message.
- Postgres storage for parsed JSON and raw lines.
- Count-first UI: returns immediate totals and deferred exact match counts for heavy filters.
- Lazy preview with keyset pagination ("Load Preview" with Next/Prev paging).
- Field contains filter (JSON key searched anywhere in the JSON tree), full-text search over parsed JSON, and timestamp range filter.
- Field index stores one row per object-field occurrence, keeps metadata (`field_key`, `field_path`, null/empty/type) for the full tree, and stores `value_text` only for scalar values.
- Admin actions: reload file from start, delete all ingested rows.
- Resumes ingestion after restart using persisted byte offsets.

## Tech Stack

Frontend
- Vite + React
- Plain CSS

Backend
- Java 21
- Spring Boot (Web, Data JPA)
- Jackson for JSON parsing
- Hibernate native JSON mapping for JSONB columns

Database
- Postgres 16

Infra / Dev
- Docker Compose for local orchestration
- Nginx for static hosting and API proxy in production container

## How It Works

1. The backend chooses ingest mode from `INGEST_MODE` (`file` by default).
2. In `file` mode, it reads and tails `JSONL_FILE_PATH`.
3. In `kafka` mode, it consumes from `KAFKA_TOPIC`.
4. It parses each record and inserts rows into Postgres in batches.
5. The UI requests counts and a small preview page from the backend instead of loading the full file.
6. Filters are evaluated on the server using Postgres JSONB and timestamp columns.

## Running With Docker Compose

Prerequisites
- Docker and Docker Compose
- A JSONL file on your machine

Steps
1. Create a local `data/` directory in the repo root.
2. Place your JSONL file at `data/sample.jsonl` (or change `JSONL_FILE_PATH` in `docker-compose.yml`).
3. Start the viewer stack:

```bash
docker compose up --build
```

Postgres in `docker-compose.yml` is started with `max_wal_size=1GB` to reduce checkpoint pressure during large ingests.

4. Or run viewer + mock generator (backend reads `/data/generated.jsonl`):

```bash
docker compose -f docker-compose.yml -f docker-compose.generated.yml up --build
```

Services
- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- Postgres: `localhost:5432`

## Development (Frontend)

```bash
cd frontend
npm install
npm run dev
```

The dev server proxies `/api` to the backend. You can override the proxy target via `VITE_PROXY_TARGET`.

## Configuration

Backend environment variables (Docker Compose defaults shown in `docker-compose.yml`):

- `INGEST_MODE` (default: `file`): `file` or `kafka`.
- `INGEST_SOURCE_ID` (optional, Kafka mode only): logical source id used for API `stats.filePath` and DB scoping. Default is `kafka:<topic>`.
- `JSONL_FILE_PATH` (required): path to the JSONL file inside the backend container.
- `INGEST_POLL_INTERVAL_MS` (default: `1000`): polling interval in ms.
- `INGEST_BATCH_SIZE` (default: `500`): insert batch size.
- `APP_PREVIEW_STATEMENT_TIMEOUT` (default: `20s`): statement timeout for preview queries.
- `APP_COUNT_JOB_STATEMENT_TIMEOUT` (default: `10m`): statement timeout for background exact-count jobs.
- `SPRING_MVC_ASYNC_REQUEST_TIMEOUT` (default: `305s`): async MVC request timeout guardrail.

Kafka (required when `INGEST_MODE=kafka`):

- `KAFKA_BOOTSTRAP_SERVERS` (required): broker list, e.g. `kafka:9092`.
- `KAFKA_TOPIC` (required): source topic.
- `KAFKA_GROUP_ID` (default: `jsonl-viewer`): consumer group id used by ingest and admin reset/reload.
- `KAFKA_CLIENT_ID` (default: `jsonl-viewer`)
- `KAFKA_AUTO_OFFSET_RESET` (default: `latest`)
- `KAFKA_CONCURRENCY` (default: `1`)
- `KAFKA_MAX_POLL_RECORDS` (default: `INGEST_BATCH_SIZE`)
- `KAFKA_SECURITY_PROTOCOL` (default: `PLAINTEXT`; set `SSL` for TLS)
- `KAFKA_SSL_KEYSTORE_LOCATION` (default: `file:/data/identity.jks`)
- `KAFKA_SSL_KEYSTORE_PASSWORD`
- `KAFKA_SSL_KEYSTORE_TYPE` (default: `JKS`)
- `KAFKA_SSL_TRUSTSTORE_LOCATION` (defaults to keystore path)
- `KAFKA_SSL_TRUSTSTORE_PASSWORD` (defaults to keystore password)
- `KAFKA_SSL_TRUSTSTORE_TYPE` (default: `JKS`)
- `KAFKA_SSL_KEY_PASSWORD` (defaults to keystore password)

Kafka mode examples:

PLAINTEXT
```bash
INGEST_MODE=kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_TOPIC=json-events
KAFKA_GROUP_ID=jsonl-viewer-dev
```

SSL (JKS mounted at `/data/identity.jks`)
```bash
INGEST_MODE=kafka
KAFKA_BOOTSTRAP_SERVERS=broker.example.com:9093
KAFKA_TOPIC=json-events
KAFKA_GROUP_ID=jsonl-viewer-prod
KAFKA_SECURITY_PROTOCOL=SSL
KAFKA_SSL_KEYSTORE_LOCATION=file:/data/identity.jks
KAFKA_SSL_KEYSTORE_PASSWORD=change-me
```

Database:

- `SPRING_DATASOURCE_URL` (default: `jdbc:postgresql://db:5432/jsonl`)
- `SPRING_DATASOURCE_USERNAME` (default: `jsonl`)
- `SPRING_DATASOURCE_PASSWORD` (default: `jsonl`)
- `spring.jpa.hibernate.ddl-auto=validate` (schema validated at startup)
- `spring.flyway.enabled=true` (schema managed by Flyway migrations)
- `spring.sql.init.mode=never` (SQL init disabled)

Frontend nginx proxy timeout env vars:

- `API_PROXY_CONNECT_TIMEOUT` (default: `30s`)
- `API_PROXY_SEND_TIMEOUT` (default: `300s`)
- `API_PROXY_READ_TIMEOUT` (default: `300s`)

## API Endpoints

- `GET /api/stats`
  - Returns source id (`filePath`), counts from ingest state, last ingestion time, `sourceRevision`, `searchStatus` (`ready|building`), and `ingestPaused` (`true|false`).

- `POST /api/filters/count`
  - Body: `{ filters: [ { type, fieldPath, valueContains, query, from, to } ] }`
  - For `type: "field"`, `fieldPath` is treated as a key name and matched anywhere in the JSON tree (not dot-path syntax).
  - For `type: "text"`, `query` is matched with Postgres full-text search over `parsed::text`.
  - Timestamp payload format: `YYYY-MM-DDTHH:mm:ssZ` (UTC)
  - Returns `{ totalCount, matchCount, status, requestHash, sourceRevision, computedRevision, lastComputedAt }`
  - `status` is `pending|ready`; `matchCount` is `null` while pending.

- `GET /api/filters/count/{requestHash}`
  - Polls deferred exact-count completion for a prior `POST /api/filters/count`.
  - Returns the same shape as `POST /api/filters/count`.

- `POST /api/filters/preview`
  - Body: `{ filters: [...], filtersOp, sortDir, cursor, limit }`
  - `sortDir`: `asc | desc` (default: `desc`)
  - `limit`: page size (default: `10`, max: `500`)
  - `cursor`: opaque Base64URL cursor returned by the previous page (`null` for page 1)
  - Field/text/timestamp filter semantics are identical to `/api/filters/count`.
  - Timestamp payload format: `YYYY-MM-DDTHH:mm:ssZ` (UTC)
  - Returns `{ rows, nextCursor }`, where each row includes:
    - `id`, `lineNo`, `ts`
    - `key` (`parsed->'key'`)
    - `headers` (`parsed->'headers'`)
    - `error` (`parse_error`)
    - `rawSnippet` and `rawTruncated` only for parse-error rows
  - Uses keyset pagination with stable ordering:
    - fixed line ordering: `ORDER BY line_no {ASC|DESC}, id {ASC|DESC}`

- `GET /api/entries/{id}`
  - Returns full row detail for the current file scope:
    `{ id, lineNo, ts, parsed, error }`

- `GET /api/entries/{id}/raw`
  - Returns the full original raw line as `text/plain`.

- `POST /api/admin/reset`
  - File mode: deletes all entries and updates ingest state to the end of the file.
  - Kafka mode: seeks consumer-group offsets to end, clears rows, and resets ingest state.

- `POST /api/admin/reload`
  - File mode: clears ingest state and re-reads the file from the start.
  - Kafka mode: seeks consumer-group offsets to beginning, clears rows, and resets ingest state.

- `POST /api/admin/pause`
  - Pauses background ingestion.
  - File mode: scheduled poll/tail loop stops ingesting new bytes.
  - Kafka mode: listener container is stopped.

- `POST /api/admin/resume`
  - Resumes background ingestion after a pause.
  - File mode: scheduled poll/tail loop continues on next poll.
  - Kafka mode: listener container is started.

## Data Model

Tables are managed via Flyway migrations.

- `jsonl_entry`
  - `id BIGSERIAL PRIMARY KEY`
  - `file_path TEXT`
  - `line_no BIGINT`
  - `raw_line TEXT`
  - `parsed JSONB`
  - `parse_error TEXT`
  - `ts TIMESTAMPTZ`
  - `created_at TIMESTAMPTZ`

Ingest behavior note:
- Root-level `parsed.headers` is stored decoded when it looks like printable base64 text.
- `raw_line` is always stored exactly as the original JSONL line.

- `ingest_state`
  - `file_path TEXT PRIMARY KEY`
  - `byte_offset BIGINT`
  - `line_no BIGINT`
  - `last_ingested_at TIMESTAMPTZ`
  - `total_count BIGINT`
  - `parsed_count BIGINT`
  - `error_count BIGINT`
  - `source_revision BIGINT`
  - `indexed_revision BIGINT`
  - `ingest_status TEXT`

- `jsonl_entry_field_index`
  - one row per JSON key occurrence
  - `entry_id`, `file_path`, `field_key`, `field_path`, `value_text`, `value_ts`, `value_type`, `is_null`, `is_empty`
  - `value_text` is populated for scalar values only (string, number, boolean, null)
  - object/array container rows keep metadata but set `value_text` to `null`
  - `value_ts` is populated only for timestamp-like scalar field names (`timestamp`, `time`, `ts`, `date`, `*Time`, `*At`)

- `filter_count_cache`
  - keyed by `(file_path, request_hash)`
  - stores deferred exact-count status and computed revision metadata

Indexes
- `jsonl_entry(file_path, id)` for keyset pagination
- `jsonl_entry(file_path, line_no, id)` for line sort pagination
- `jsonl_entry(file_path, ts)` for timestamp filtering
- `jsonl_entry_parsed_fts_idx` on `to_tsvector('simple', parsed::text)`
- `jsonl_entry_field_index` btree indexes for key/null/empty lookups
- `jsonl_entry_field_index` trigram GIN index on `value_text`

## Performance Notes

- Rendering is count-first to avoid huge DOM sizes.
- Preview is limited to small pages (default 10 rows).
- Backend uses batch inserts for ingestion.

## Suggested Next Optimizations

- Use Postgres COPY for faster ingestion on large files.
- Add field pinning (computed columns + indexes) for hot JSON paths.
- Add optional full-text search over `parsed` or a denormalized `search_text` column.
- Add file rotation detection by inode and auto-reset ingest state.

## Repository Layout

- `frontend/` - React UI (Vite app + nginx Dockerfile/config)
- `backend/` - Spring Boot service
- `mock-generator/` - Spring Boot mock JSONL generator service
- `docker-compose.yml` - Full local stack
- `docker-compose.generated.yml` - Compose override to run backend against generated JSONL
