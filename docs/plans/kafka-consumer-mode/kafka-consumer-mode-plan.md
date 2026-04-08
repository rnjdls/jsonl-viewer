# Kafka Consumer Ingest Mode (Backend)

## Summary
- Add a new backend ingest mode that consumes JSON records from Kafka instead of reading/tailing a local JSONL file.
- Keep the existing file-tailing mode as the default; switch modes via config.
- Configure Kafka SSL using `identity.jks` as both **keystore** and **truststore** (password provided via env var; not committed).

## Implementation Changes

### 1) Branch + plan doc
- Create feature branch: `feature/kafka-consumer-mode`.
- Add this plan as: `plans/kafka-consumer-mode/kafka-consumer-mode-plan.md`.

### 2) App-level mode + source identity
- Add `app.ingest-mode` with values:
  - `file` (default)
  - `kafka`
- Add `app.source-id` (env: `INGEST_SOURCE_ID`, optional):
  - Used as the value stored in DB `jsonl_entry.file_path` and returned in API `stats.filePath` (frontend label: “Source”).
  - Default behavior if unset:
    - `file` mode: use `app.jsonl-file-path` (the actual file path)
    - `kafka` mode: use `kafka:<topic>`
- Add `app.kafka.topic` (env: `KAFKA_TOPIC`, required when `app.ingest-mode=kafka`).

### 3) Kafka consumer configuration (all required settings)
- Add the following to `backend/src/main/resources/application.yml` (values via env vars; safe defaults where possible):

```yml
app:
  ingest-mode: ${INGEST_MODE:file}
  source-id: ${INGEST_SOURCE_ID:}
  jsonl-file-path: ${JSONL_FILE_PATH:}
  jsonl-timestamp-field: ${JSONL_TIMESTAMP_FIELD:timestamp}
  ingest-poll-interval-ms: ${INGEST_POLL_INTERVAL_MS:1000}
  ingest-batch-size: ${INGEST_BATCH_SIZE:500}
  kafka:
    topic: ${KAFKA_TOPIC:}

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
      security.protocol: ${KAFKA_SECURITY_PROTOCOL:SSL}
      ssl.endpoint.identification.algorithm: ${KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM:https}
    ssl:
      key-store-location: ${KAFKA_SSL_KEYSTORE_LOCATION:file:/data/identity.jks}
      key-store-password: ${KAFKA_SSL_KEYSTORE_PASSWORD:}
      key-store-type: ${KAFKA_SSL_KEYSTORE_TYPE:JKS}
      trust-store-location: ${KAFKA_SSL_TRUSTSTORE_LOCATION:${KAFKA_SSL_KEYSTORE_LOCATION:file:/data/identity.jks}}
      trust-store-password: ${KAFKA_SSL_TRUSTSTORE_PASSWORD:${KAFKA_SSL_KEYSTORE_PASSWORD:}}
      trust-store-type: ${KAFKA_SSL_TRUSTSTORE_TYPE:JKS}
      key-password: ${KAFKA_SSL_KEY_PASSWORD:${KAFKA_SSL_KEYSTORE_PASSWORD:}}
```

Notes:
- Set `KAFKA_SSL_KEYSTORE_PASSWORD=apisdksecret` at runtime (via env var or `.env`), and do not commit secrets.
- The default JKS mount path assumes Docker Compose mounts `./data:/data`.

### 4) Dependencies
- Update `backend/pom.xml`:
  - Add `org.springframework.kafka:spring-kafka`
  - (Optional but recommended) Add `org.springframework.kafka:spring-kafka-test` in test scope for embedded-kafka tests.

### 5) Ingestion architecture (file vs kafka)
- Extract the JSON parsing + base64 header decode + timestamp extraction into a reusable component (e.g., `JsonlEntryParser`) so both ingest paths behave identically.
- File mode:
  - Keep the current scheduled file tailing, but ensure it only runs when `app.ingest-mode=file` (conditional bean or early return).
- Kafka mode:
  - Add a Kafka ingest service enabled only when `app.ingest-mode=kafka`.
  - Implement `@KafkaListener(topics="${app.kafka.topic}")` as a **batch** listener.
  - For each Kafka record value:
    - Treat `record.value()` as the “raw line”
    - Create `JsonlEntry(filePath=sourceId, lineNo=nextSequence, rawLine=value, parsed|parseError, ts)`
  - Maintain `lineNo` as a monotonically increasing counter stored in `ingest_state.line_no` for the `sourceId` (single-threaded consumption; set concurrency=1).
  - Wrap the listener in `@Transactional` and **throw** on DB failures; offset commits happen only after successful processing (container-managed `ack-mode=batch`).

### 6) API changes (minimal + backward compatible)
- Update backend usage of the current “file path” concept:
  - Internally use `sourceId` (computed) as the query key for counts/preview/stats.
  - Keep API field name `filePath` unchanged (frontend compatibility); it now returns the `sourceId`.

### 7) Admin endpoints in Kafka mode (seek end/begin)
- Keep endpoints unchanged:
  - `POST /api/admin/reset`: “tail from now”
  - `POST /api/admin/reload`: “replay from earliest”
- Kafka-mode behavior:
  - Stop the listener container.
  - Use a **temporary KafkaConsumer** (same consumer props + same `group.id`) to:
    - subscribe to the topic
    - poll until partitions are assigned
    - `seekToEnd` (reset) or `seekToBeginning` (reload)
    - commit the resulting positions with `commitSync`
  - Clear DB rows for `sourceId` (`deleteByFilePath(sourceId)`).
  - Reset `ingest_state` for `sourceId` (`line_no=0`, `byte_offset=0`, `last_ingested_at` set to `now`).
  - Restart the listener container.

### 8) Repo hygiene + docs
- Update `.gitignore` to ignore JKS files (at minimum `data/*.jks` or `*.jks`) so `identity.jks` is never committed.
- Update `README.md`:
  - Document `INGEST_MODE=file|kafka`
  - List Kafka env vars (including SSL vars) and explain mounting `identity.jks` to `/data/identity.jks`
  - Provide a minimal Kafka-mode example env block (with `KAFKA_SSL_KEYSTORE_PASSWORD=apisdksecret` set via `.env`).

## Test Plan
- Unit:
  - Move/extend existing base64 decode tests to target the new shared parser component.
- Manual (Docker / local):
  - File mode regression: run current flow and verify ingestion still works.
  - Kafka mode: set `INGEST_MODE=kafka`, configure SSL env vars, produce a few JSON messages, verify:
    - `/api/stats` shows `filePath` = `kafka:<topic>` (or `INGEST_SOURCE_ID` if set) and counts increment
    - `/api/filters/preview` returns records with `raw` equal to Kafka message value
    - `reset` consumes only new messages going forward; `reload` replays from earliest

## Assumptions
- Kafka message value is UTF-8 JSON text (one JSON object per message).
- `identity.jks` contains what’s needed to both present the client cert/key and trust the broker cert chain (same file used for keystore + truststore).
- Delivery semantics are at-least-once; duplicates are acceptable for v1.
