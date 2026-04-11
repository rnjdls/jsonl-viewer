# AND/OR Operators for Multi-Filter Search

## Summary
- Today, when multiple filters are added, they are combined implicitly with **AND** (match-all).
- Add a user-selectable boolean operator to combine active filters:
  - **AND** (default): entry must satisfy every active filter (current behavior)
  - **OR**: entry may satisfy any active filter
- Apply consistently to:
  - Backend: `/api/filters/count`, `/api/filters/preview`
  - Frontend: `SearchBar` UI + request payload mapping

## UX / Product
- Add a small toggle in the SearchBar toolbar: **Match: All (AND) / Any (OR)**.
- Default to **AND** for backward compatibility.
- When there are 0–1 active filters, the toggle is optionally hidden/disabled (no effect).

## API Changes (backward compatible)
- Add optional request field: `filtersOp` to both count + preview requests.
  - allowed values: `and` | `or` (case-insensitive)
  - default: `and` when missing/blank

Example:
```json
{
  "filtersOp": "or",
  "filters": [
    { "type": "field", "fieldPath": "status", "op": "contains", "valueContains": "500" },
    { "type": "text", "query": "timeout" }
  ]
}
```

## Backend Changes

### 1) DTOs
- Update:
  - `backend/src/main/java/com/jsonl/viewer/api/dto/FilterCountRequest.java`
  - `backend/src/main/java/com/jsonl/viewer/api/dto/PreviewRequest.java`
- Add `filtersOp` field + getters/setters.

### 2) Filter SQL construction
- Update `backend/src/main/java/com/jsonl/viewer/service/FilterService.java`:
  - Extend `buildFilterSql(...)` to accept `filtersOp` (default AND).
  - Join the per-filter conditions with:
    - AND mode: `... AND ...` (existing behavior)
    - OR mode: `(... OR ...)` (must be wrapped in parentheses for correctness)
- Keep the current parse-error handling behavior to avoid changing semantics:
  - When any **text** filter is present, keep `parsed IS NOT NULL` gating (current behavior).
  - Otherwise keep `parsed IS NULL OR (parsed IS NOT NULL AND ...)` (current behavior).

### 3) Controller wiring
- Update `backend/src/main/java/com/jsonl/viewer/api/JsonlController.java`:
  - Read `filtersOp` from the request (default AND).
  - Pass it into the FilterService when building SQL for both endpoints.

### 4) Tests
- Extend `backend/src/test/java/com/jsonl/viewer/service/FilterServiceTest.java`:
  - OR mode builds ` OR ` joins with correct parentheses
  - Parameter ordering remains stable across mixed filters in OR mode
  - AND mode remains unchanged (regression)

## Frontend Changes

### 1) State + request payload
- Track `filtersOp` (default AND):
  - Prefer in `frontend/src/hooks/useJsonlSearch.js` so it’s tied to filter state.
- Include `filtersOp` alongside `filters` in:
  - `frontend/src/App.jsx` `filterPayload` mapping

### 2) SearchBar UI
- Update `frontend/src/components/SearchBar/SearchBar.jsx`:
  - Add the **All/Any** toggle in the toolbar row.
  - When toggled, update `filtersOp` state.

### 3) Optional: local filter helper (if kept)
- If client-side filtering is still desired, replace `entryMatchesAllFilters(...)` with:
  - `entryMatchesFilters(entry, filters, filtersOp)`

## Open Questions / Follow-ups
- Parse-error rows: should they always be included in match counts/preview regardless of filters?
  - Today, parse-error rows are included for field/timestamp filters but excluded when any text filter is present.
- Future enhancement: expression builder with grouping/mixed operators (e.g. `A AND (B OR C)`).
