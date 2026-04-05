# UTC Timestamp + JPA Migration + Docs Update

## Summary
- Make timestamp filters UTC-only and send payloads as `YYYY-MM-DDTHH:mm:ssZ`.
- Replace JDBC repositories with Spring Data JPA using hibernate-types for JSONB.
- Update docs to reflect JPA, ddl-auto schema generation, and UTC payload format.

## Implementation Changes

### 1) Frontend UTC filter payload
- Update timestamp filter inputs to be UTC-only:
  - Keep `datetime-local`, label as `UTC`, and treat the value as UTC.
  - Ensure `step="1"` so seconds are supported.
- Add a helper to normalize to `YYYY-MM-DDTHH:mm:ssZ`:
  - If value is `YYYY-MM-DDTHH:mm`, append `:00Z`.
  - If value is `YYYY-MM-DDTHH:mm:ss`, append `Z`.
- Apply the helper in `filterPayload` construction for timestamp filters.
- Update UI copy in the timestamp filter row to clarify UTC.

### 2) Backend JPA migration
- Dependencies:
  - Add `spring-boot-starter-data-jpa`.
  - Add `hibernate-types-60` (JsonNode to jsonb mapping).
  - Remove `spring-boot-starter-jdbc`.
- Config:
  - Set `spring.jpa.hibernate.ddl-auto=update`.
  - Disable SQL init: `spring.sql.init.mode=never`.
  - Remove `schema.sql` (or leave unused); schema comes from JPA annotations.
- Entities:
  - `JsonlEntry` entity with `@Table(name="jsonl_entry", indexes=...)` and `@Column` mapping to existing schema.
  - `parsed` uses `@Type(JsonBinaryType.class)` and `@Column(columnDefinition="jsonb")`.
  - `IngestState` entity with `@Id filePath` and matching column names.
- Repositories:
  - `JsonlEntryRepository extends JpaRepository<JsonlEntry, Long>`.
  - `IngestStateRepository extends JpaRepository<IngestState, String>`.
  - Custom repository (EntityManager + native SQL) for:
    - `countMatching(filters)`
    - `preview(filters, cursorId, limit)`
    - `getCounts(filePath)` (total/parsed/errors)
- Ingestion service:
  - Replace JdbcTemplate batch insert with JPA batching:
    - `entityManager.persist()` in a loop.
    - `flush()` and `clear()` every batch size.
  - Replace ingest state upsert with:
    - `findById` + update fields + `save`.
- Filter logic:
  - Reuse JSON path SQL via native queries (`jsonb_path_query_first(parsed, ?::jsonpath)`).
  - Keep `Instant.parse()` for UTC payloads.

### 3) Documentation update
- Update README to note:
  - JPA + Hibernate ddl-auto schema generation.
  - JSONB handled via hibernate-types.
  - Timestamp filter expects UTC payloads in `YYYY-MM-DDTHH:mm:ssZ`.

## Test Plan
- `docker compose up --build` with `data/sample.jsonl`.
- Verify ingestion starts and `jsonl_entry` gets rows.
- Frontend:
  - Add timestamp filter with UTC values and confirm payload ends in `Z`.
  - Confirm counts update and preview loads.
- Backend:
  - Confirm `/api/filters/count` and `/api/filters/preview` return results for timestamp range.

## Assumptions
- Use `spring.jpa.hibernate.ddl-auto=update` for local dev safety.
- Keep existing table/column names via JPA annotations to avoid migrations.
- UTC-only UI uses `datetime-local` inputs but treats values as UTC strings.
