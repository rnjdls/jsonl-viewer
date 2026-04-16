# Bounded File-Ingest Passes

## Summary

Implement bounded file-mode ingest so each scheduled pass processes at most `500` complete JSONL records or about `2 MiB` of raw file input, whichever limit is reached first, and change the default poll interval to `500 ms`.

Preserve the current snapshot-based ingest behavior, restart-safe offsets, shrink reset handling, pause/resume, and partial-line retry semantics. Leave Kafka ingest unchanged.

## Public Interfaces And Config Changes

- Add two new backend properties and env vars:
  - `app.ingest-max-records-per-pass` / `INGEST_MAX_RECORDS_PER_PASS` with default `500`
  - `app.ingest-max-bytes-per-pass` / `INGEST_MAX_BYTES_PER_PASS` with default `2097152`
- Change the default file ingest poll interval to `500 ms` in backend config and `docker-compose.yml`.
- Keep `INGEST_BATCH_SIZE` unchanged and document it as the JPA flush batch size, not the per-pass cap.
- Update README configuration docs and Docker Compose instructions to describe:
  - bounded file-mode passes
  - the new defaults
  - the fact that the optional generated-data profile can still outrun bounded ingest

## Implementation Changes

- Extend `AppProperties` and `backend/src/main/resources/application.yml` with the new bounded-pass settings.
- Extend `IngestConfigurationValidator` to fail fast when the new limits are non-positive, instead of silently allowing invalid configs.
- Update `JsonlIngestService` file-mode ingest loop to track:
  - `recordsThisPass`
  - raw bytes consumed from the snapshot during this pass
- Stop a pass when either cap is reached, with these exact semantics:
  - record cap is hard: stop after persisting the `N`th complete non-empty line
  - byte cap is soft at line boundaries: stop before starting the next record once the pass has consumed at least the configured bytes and has already completed at least one full line
  - if the first unread line alone exceeds the byte cap, ingest that one full line anyway so the offset advances and the service cannot livelock on a single oversized record
- Keep current behavior unchanged for:
  - initial snapshot sizing
  - not chasing growth beyond the starting snapshot
  - rolling back `byteOffset` to the start of a partial trailing line
  - resetting when the source file shrinks
  - single-pass mutual exclusion via the existing ingest lock
- Persist updated offset/count/revision state at the end of each capped pass so the next scheduled poll resumes from the correct newline boundary.
- Add concise debug logging that states whether the pass stopped because it exhausted the snapshot, hit the record cap, or hit the byte cap.

## Test Plan

- Add a regression test that a pass stops after the configured record limit and the next poll resumes at the next unread line.
- Add a regression test that a pass stops on the byte cap only after finishing the current line, and resumes correctly on the next poll.
- Add a regression test for an oversized first line to verify the service still ingests that line and advances the offset instead of stalling forever.
- Keep and adapt the existing tail-snapshot test so appended data beyond the starting snapshot is still deferred to a later pass even when caps are enabled.
- Run `cd backend && mvn test`.
- Run `docker compose up --build` as the smoke test for the changed backend defaults.

## Assumptions

- The bounded-pass feature applies only to file-mode ingest. Kafka ingest behavior and Kafka polling settings remain unchanged.
- `500 ms`, `500 records/pass`, and `2 MiB/pass` are the new default operating values for the backend and default Docker Compose stack.
- The byte cap is approximate by design and may be exceeded by one complete line so the ingester always makes progress.
- This task does not retune `docker-compose.generated.yml`; it only documents that generated mode may still build backlog under these safer defaults.
