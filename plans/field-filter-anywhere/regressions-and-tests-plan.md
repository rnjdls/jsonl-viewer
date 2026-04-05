# Regression/Test Plan: Field filter = key anywhere

## Summary
- `main` and `feature/field-filter-anywhere` currently point to the same commit; the "diff vs main" is entirely uncommitted working-tree changes.
- Behavior change (accepted): the Field filter input is now treated as a literal JSON key name searched anywhere in the JSON tree (dot-path no longer supported).
- Missing coverage: no automated tests exist for backend filter SQL generation or frontend key-anywhere matching.

## Implementation Changes

### Backend (unit tests only)
- Add JUnit test dependencies in `backend/pom.xml` (use `spring-boot-starter-test` with `test` scope).
- Add `backend/src/test/java/com/jsonl/viewer/service/FilterServiceTest.java` to cover:
  - No filters / empty filters → `whereClause == "WHERE file_path = ?1"` and `params` empty.
  - Field filter (`fieldPath="details"`, `valueContains="Auto-generated"`) → `whereClause` contains:
    - `EXISTS (` ... `jsonb_path_query(parsed, ?2::jsonpath)` ... `ILIKE ?3`
    - and `params == ["$.**.\"details\"", "%Auto-generated%"]`.
  - Field + timestamp filters → parameter indexing increments (field uses `?2/?3`, timestamps use `?4/?5`).
  - Escaping regression checks: `fieldPath` containing `"` and `\` produces a JSONPath param that remains quoted and properly escaped.
  - Blank `fieldPath` is ignored (no conditions added).

### Frontend (Vitest unit tests)
- Add `vitest` to `frontend/package.json` `devDependencies` and add scripts:
  - `test`: `vitest run`
  - (optional) `test:watch`: `vitest`
- Add `frontend/src/utils/search.test.js` covering `entryMatchesAllFilters()` for Field filters:
  - Root key match (e.g., `action`).
  - Nested key match (e.g., `details.notes` by searching key `notes`).
  - Key inside arrays of objects.
  - Multiple occurrences of the same key → matches if any value contains the needle.
  - Empty value → matches any entry where the key exists.
  - Missing key → does not match.
  - `entry.parsed === null` (parse error rows) → always matches.

### Housekeeping
- Optionally add a short “Tests” section to `README.md`:
  - `cd backend && mvn test`
  - `cd frontend && npm test`

## Test Plan
- Automated:
  - `cd backend && mvn test`
  - `cd frontend && npm test`
  - `cd frontend && npm run build`
- Manual smoke:
  - `docker compose up -d db backend jsonl-viewer`
  - In UI, add Field filter:
    - key `notes`, value `Auto-generated` → should match many rows
    - key `details`, value `{` (or `value`) → should match (object values stringify in backend/JS)

## Assumptions
- Field filter is key-only (breaking change) and case-sensitive; dot-path input is not supported.
- Backend tests validate SQL string/params only (no Postgres execution).
