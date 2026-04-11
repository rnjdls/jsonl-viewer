# Kafka Consumer Ingest Mode (Backend) — v2

## Summary
- Add a new backend ingest mode that consumes JSON records from Kafka (one JSON object per message) instead of reading/tailing a local JSONL file.
- Keep existing file-tailing mode as the default.
- Keep API endpoints and response shapes **backward compatible** (especially `StatsResponse.filePath`), but allow `filePath` to represent a **logical source id** in Kafka mode.
- Kafka connectivity supports both:
  - **PLAINTEXT (default)** for local/dev
  - **SSL** when `KAFKA_SECURITY_PROTOCOL=SSL` and JKS env vars are provided

This document supersedes `kafka-consumer-mode-plan.md` to match the current repo layout, current controller/service wiring, and the current test situation.

---

## Compatibility Snapshot (Current System)
- Backend config today: `app.jsonl-file-path`, `app.jsonl-timestamp-field`, `app.ingest-poll-interval-ms`, `app.ingest-batch-size`
- File ingest today: `JsonlIngestService` scheduled poll + `resetToFileEnd()` + `reloadFromStart()`
- API endpoints today:
  - `GET /api/stats`
  - `POST /api/filters/count`
  - `POST /api/filters/preview`
  - `GET /api/entries/{id}`
  - `GET /api/entries/{id}/raw`
  - `POST /api/admin/reset`
  - `POST /api/admin/reload`
- DB scoping key today: `jsonl_entry.file_path` and `ingest_state.file_path`
- Frontend already displays `stats.filePath` as “Source”

---

## Configuration

### 1) Ingest mode switch
Add `app.ingest-mode`:
- `file` (default)
- `kafka`

### 2) Source identity (`filePath` / DB key)
Define `activeSourceId` (used for DB scoping and returned as `StatsResponse.filePath`):

- **File mode** (`app.ingest-mode=file`):
  - `activeSourceId = app.jsonl-file-path`
  - Ignore `app.source-id` (no DB-key override in file mode; preserves current behavior)

- **Kafka mode** (`app.ingest-mode=kafka`):
  - If `app.source-id` is set and non-blank: `activeSourceId = app.source-id`
  - Else: `activeSourceId = kafka:<topic>`

### 3) Required Kafka settings
When `app.ingest-mode=kafka`, require:
- `app.kafka.topic` (env `KAFKA_TOPIC`)
- `spring.kafka.bootstrap-servers` (env `KAFKA_BOOTSTRAP_SERVERS`)

### 4) `application.yml` updates (env-driven)
Update `backend/src/main/resources/application.yml` to include:

```yml
app:
  ingest-mode: ${INGEST_MODE:file}
  # used only when ingest-mode=kafka (see activeSourceId rules)
  source-id: ${INGEST_SOURCE_ID:}

  jsonl-file-path: ${JSONL_FILE_PATH:}
  jsonl-timestamp-field: ${JSONL_TIMESTAMP_FIELD:timestamp}
  ingest-poll-interval-ms: ${INGEST_POLL_INTERVAL_MS:1000}
  ingest-batch-size: ${INGEST_BATCH_SIZE:500}

  kafka:
    topic: ${KAFKA_TOPIC:}
    concurrency: ${KAFKA_CONCURRENCY:1}

spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:}
    consumer:
      group-id: ${KAFKA_GROUP_ID:jsonl-viewer}
      client-id: ${KAFKA_CLIENT_ID:jsonl-viewer}
      auto-offset-reset: ${KAFKA_AUTO_OFFSET_RESET:latest}
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      max-poll-records: ${KAFKA_MAX_POLL_RECORDS:${INGEST_BATCH_SIZE:500}}
    listener:
      type: batch
      ack-mode: batch
    properties:
      # default to PLAINTEXT; allow SSL via env var
      security.protocol: ${KAFKA_SECURITY_PROTOCOL:PLAINTEXT}
      ssl.endpoint.identification.algorithm: ${KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM:https}
    ssl:
      # SSL settings are used only when security.protocol is SSL / SASL_SSL.
      key-store-location: ${KAFKA_SSL_KEYSTORE_LOCATION:file:/data/identity.jks}
      key-store-password: ${KAFKA_SSL_KEYSTORE_PASSWORD:}
      key-store-type: ${KAFKA_SSL_KEYSTORE_TYPE:JKS}
      trust-store-location: ${KAFKA_SSL_TRUSTSTORE_LOCATION:${KAFKA_SSL_KEYSTORE_LOCATION:file:/data/identity.jks}}
      trust-store-password: ${KAFKA_SSL_TRUSTSTORE_PASSWORD:${KAFKA_SSL_KEYSTORE_PASSWORD:}}
      trust-store-type: ${KAFKA_SSL_TRUSTSTORE_TYPE:JKS}
      key-password: ${KAFKA_SSL_KEY_PASSWORD:${KAFKA_SSL_KEYSTORE_PASSWORD:}}
```

Notes:
- Docker Compose already mounts `./data:/data`, so `file:/data/identity.jks` works when needed.
- Do not commit any `.jks` files or passwords; provide via runtime env vars.

---

## Dependencies
Update `backend/pom.xml`:
- Add `org.springframework.kafka:spring-kafka`
- Add `org.springframework.kafka:spring-kafka-test` in test scope (optional; only if implementing embedded-kafka integration tests)

---

## Implementation Changes

### 1) Centralize “active source id” lookup
Add a small service (or properties helper) to compute:
- current ingest mode
- `activeSourceId` per the rules above
- `fileReadPath` (only for file mode) = `app.jsonl-file-path`

Controller and repositories should always scope queries by `activeSourceId`.

### 2) Extract shared parsing into a reusable component
Create `JsonlEntryParser` (name flexible) that:
- parses `rawLine` to JsonNode with Jackson
- applies `Base64HeadersDecoder.decodeRootHeaders(node)`
- applies `JsonUnicodeSanitizer.sanitize(node)`
- extracts `ts` using `app.jsonl-timestamp-field`
- returns `{parsed, parseError, ts}`

Both ingest paths must use this parser so behavior matches exactly.

### 3) File ingest: keep behavior, gate by mode
- Keep current scheduled file tailing and admin operations.
- Add a fast guard so file ingest only runs when `app.ingest-mode=file`.
- Continue using DB key `activeSourceId == fileReadPath` (unchanged).

### 4) Kafka ingest service (batch listener)
Add a Kafka ingest service enabled only when `app.ingest-mode=kafka`:
- `@KafkaListener(topics = "${app.kafka.topic}", concurrency = "${app.kafka.concurrency:1}")`
- Batch listener signature uses either `List<String>` or `List<ConsumerRecord<String,String>>`
- For each record:
  - `rawLine = record.value()` (trim; if empty, skip)
  - Load `IngestState` for `activeSourceId` and take `lineNo` as last used
  - For each ingested record: increment `lineNo` and persist a `JsonlEntry(filePath=activeSourceId, lineNo, rawLine, parsed|parseError, ts)`
- After successful batch:
  - Save `ingest_state` for `activeSourceId` with:
    - `byte_offset = 0` (unused in Kafka mode; keep for schema compatibility)
    - `line_no = lastLineNoUsed`
    - `last_ingested_at = now`
- Transactionality:
  - Wrap processing in a DB transaction; if persistence fails, **throw** so offsets are not committed (at-least-once is acceptable).

### 5) Admin endpoints: keep URLs, route by mode
Keep existing endpoints unchanged:
- `POST /api/admin/reset` => “tail from now”
- `POST /api/admin/reload` => “replay from earliest”

Update implementation so the controller delegates to a mode-aware admin handler:
- File mode: call existing `resetToFileEnd()` / `reloadFromStart()`
- Kafka mode:
  1) Stop the listener container
  2) Use a temporary `KafkaConsumer` (same core props + same `group.id`) to:
     - subscribe to the topic
     - poll until partitions are assigned
     - `seekToEnd()` for reset OR `seekToBeginning()` for reload
     - `commitSync()` the resulting positions
     - close consumer
  3) Clear DB rows for `activeSourceId` (`deleteByFilePath(activeSourceId)`)
  4) Reset `ingest_state` for `activeSourceId` (`byte_offset=0`, `line_no=0`, `last_ingested_at=now`)
  5) Restart the listener container

Important: these admin operations modify offsets for the configured `KAFKA_GROUP_ID`. Recommend using a dedicated group id per viewer instance.

---

## Repo Hygiene + Docs
- Update `.gitignore` to ignore JKS files (at minimum `data/*.jks`).
- Update `README.md`:
  - Document `INGEST_MODE=file|kafka`
  - Kafka env vars (`KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC`, `KAFKA_GROUP_ID`, `KAFKA_SECURITY_PROTOCOL`, SSL vars)
  - Provide two example env blocks:
    - PLAINTEXT example (minimal)
    - SSL example (with JKS mount path + passwords via env)

---

## Test Plan

### Unit (recommended baseline)
- Add parser unit tests for:
  - valid JSON parses
  - invalid JSON yields parseError and null parsed
  - base64 headers decode behavior (string + object)
  - timestamp extraction for number (sec/ms) and ISO string (and invalid)

### Integration (optional)
- If using `spring-kafka-test` embedded Kafka:
  - produce messages to a topic and verify rows ingested into DB under `activeSourceId`
  - verify lineNo monotonic increments across batches

### Manual smoke
- File mode regression:
  - run current compose; verify ingestion + preview still works
- Kafka mode:
  - set `INGEST_MODE=kafka`, set `KAFKA_BOOTSTRAP_SERVERS` + `KAFKA_TOPIC`, produce a few JSON messages
  - verify `/api/stats` shows `filePath` = `kafka:<topic>` (or `INGEST_SOURCE_ID` if set) and counts increment
  - verify preview + entry detail endpoints work
  - verify reset/reload semantics (tail-from-now vs replay-from-earliest)

---

## Assumptions
- Kafka message value is UTF-8 JSON text (one JSON object per message).
- Delivery semantics are at-least-once; duplicates are acceptable for v1.
- For SSL, a JKS is provided via runtime mount and env vars; no secrets are committed.