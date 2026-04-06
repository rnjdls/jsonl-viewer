# Mock JSONL Generator (Spring Boot) + Compose Override

## Summary
- Add a new Spring Boot generator service that appends 250–350 JSONL lines every 2 seconds to `data/generated.jsonl`.
- Generated lines deep-copy schema from `data/sample.jsonl` and then randomize specific fields.
- Add `docker-compose.generated.yml` as an override file; base `docker-compose.yml` remains unchanged.
- `docker compose up` runs the current full stack (backend reads `/data/sample.jsonl`).
- `docker compose -f docker-compose.yml -f docker-compose.generated.yml up --build` runs full stack + generator (backend reads `/data/generated.jsonl`).

## Implementation Changes

### New Maven module: `mock-generator/`
- Create `mock-generator/pom.xml` for Spring Boot 3.3.2 (Java 21) with dependencies:
  - `spring-boot-starter`
  - `jackson-databind` (or `spring-boot-starter-json`)
  - `spring-boot-starter-test` (test scope)
- Add `com.jsonl.generator.MockJsonlGeneratorApplication` with `@SpringBootApplication` + `@EnableScheduling`.

### Config
- Add `GeneratorProperties` (`@ConfigurationProperties(prefix="generator")`) with env vars:
  - `GENERATOR_SAMPLE_PATH` default `/data/sample.jsonl`
  - `GENERATOR_OUTPUT_PATH` default `/data/generated.jsonl`
  - `GENERATOR_INTERVAL_MS` default `2000`
  - `GENERATOR_BATCH_MIN` default `250`
  - `GENERATOR_BATCH_MAX` default `350`
  - `GENERATOR_TRUNCATE_ON_START` default `true`

### Template loading (schema source)
- On startup, read `GENERATOR_SAMPLE_PATH` line-by-line:
  - Parse each non-empty line as JSON.
  - Keep only JSON objects; skip invalid lines with a log warning.
- Fail fast if no templates are loaded.

### Line generation
- Maintain an `AtomicLong nextId` starting at `1` (since we truncate on start).
- Every scheduled tick (`@Scheduled(fixedRateString="${generator.interval-ms:2000}")`):
  - Pick a random batch size in `[batchMin, batchMax]`.
  - For each line:
    - Choose a random template and deep-copy it.
    - Update fields if present:
      - `id`: incrementing number
      - `timestamp`: `Instant.now()` formatted as ISO-8601 UTC (`...Z`)
      - `user`: random from observed `user` values in templates (fallback `user_0..user_19`)
      - `action`: random from observed `action` values in templates (fallback `access|create|update|delete`)
      - `details.value`: random double in observed `details.value` range (fallback `100..1000`)
      - `details.flag`: random boolean
      - `details.notes`: `"Auto-generated entry #<id>"`
      - `headers` (if object): keep keys, but update values to stay base64-like:
        - `correlationId`: base64(`corr-<zero-padded id>`)
        - `sessionId`: base64(`session-<random small int>`)
        - `eventTime`: base64(timestamp string)
        - keep `source` / `environment` from template (or set them if present-but-blank)
    - Serialize to compact single-line JSON and append `\n`.

### File writing behavior
- On startup, if `truncateOnStart=true`, truncate/create `GENERATOR_OUTPUT_PATH`.
- Each tick appends and flushes once per batch (ensure complete lines so the backend tailer never ingests partial JSON).

### Docker
- Add `mock-generator/Dockerfile` (Maven build stage + Temurin 21 runtime) similar to `backend/Dockerfile`.

### Compose override
- Add `docker-compose.generated.yml`:
  - New service `mock-generator` mounting `./data:/data` and setting generator env vars.
  - Override `backend.environment.JSONL_FILE_PATH` to `/data/generated.jsonl` (no other changes).
- Commands:
  - Viewer only: `docker compose up --build`
  - Viewer + generator: `docker compose -f docker-compose.yml -f docker-compose.generated.yml up --build`

### Gitignore
- Add `data/generated.jsonl` to `.gitignore`.

## Test Plan
- Unit test in generator module:
  - Load a small fixture (or `data/sample.jsonl`) and generate one line.
  - Assert output parses as JSON object and preserves template keys; assert `id`, `timestamp`, and `details.notes` are updated when present.
- Manual:
  - Run Compose override and confirm `data/generated.jsonl` grows by ~250–350 lines every ~2 seconds and UI counts increase.

## Assumptions
- `data/sample.jsonl` exists and contains at least one valid JSON object line.
- Truncate-on-start is desired for `data/generated.jsonl`; backend will auto-reset ingest for that file when it shrinks.
