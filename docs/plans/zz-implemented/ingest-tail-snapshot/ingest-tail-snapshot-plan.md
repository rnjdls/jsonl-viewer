# Fix Docker “Can’t Keep Up” Ingest (Stop Chasing EOF)

## Summary
- When containerized, `JsonlIngestService.pollFile()` can run inside one long `@Transactional` ingest pass and keep reading while the JSONL file is continuously appended to.
- If the producer appends faster than the backend can read, the ingest loop may never reach EOF, so the transaction never commits and `/api/stats` appears “stuck” (counts + `lastIngestedAt` stay flat).
- Fix by reading a **snapshot** of the file per poll: only ingest bytes that existed at the start of the poll, then commit; newly appended bytes are picked up on the next poll.

## Key Changes
### 1) Snapshot-based tailing
- Update `backend/src/main/java/com/jsonl/viewer/ingest/JsonlIngestService.java`:
  - Capture `long targetSize = Files.size(path)` once per ingest run.
  - Only read bytes in `[offset, targetSize)` (do not read past `targetSize` even if the file grows during the loop).
  - Preserve current semantics:
    - Increment `lineNo` only for complete newline-terminated lines.
    - If the run ends on a partial line, do not advance `byteOffset` into it (roll back to the start of the partial line as today).

### 2) Faster IO (reduce per-byte overhead)
- Replace the current byte-by-byte `InputStream.read()` loop with buffered reads (e.g., `BufferedInputStream` + `byte[]` buffer) and scan for `\n`.
- Ensure the “bytes remaining” cap uses `targetSize` so the loop finishes even when the file is still growing.

### 3) Optional debug signal (recommended)
- Add a `log.debug(...)` when an ingest pass advances state (bytes ingested, lines ingested, elapsed ms).

## Test Plan
### Unit regression: “does not chase growth past snapshot”
- Add `backend/src/test/java/com/jsonl/viewer/ingest/JsonlIngestServiceTailSnapshotTest.java` that:
  1. Creates a temp JSONL file with N complete lines (each ending in `\n`).
  2. Sets up a service with mocked repositories and an `EntityManager` that pauses on first `persist()` using a `CountDownLatch` (so we know `targetSize` was computed already).
  3. While paused, appends additional lines to the file (increasing size beyond `targetSize`).
  4. Releases the latch and waits for ingest to finish.
  5. Asserts:
     - saved `IngestState.byteOffset == initialFileSize` (not the grown size)
     - persisted entry count == N (does not include the appended lines)

## Assumptions
- It’s acceptable that each poll processes a snapshot and leaves newly appended bytes for the next scheduled poll (this is required for live stats updates under continuous writes).
- `/api/admin/reload` and `/api/admin/reset` behavior stays the same, but benefits from improved IO/commit behavior.

