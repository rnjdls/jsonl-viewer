# Field Filter Ops: NULL / NOT NULL / EMPTY / NOT EMPTY

## Summary
- Create feature branch `feature/field-filter-null-empty-ops`.
- Extend the Field filter to support operations:
  - `CONTAINS` (existing behavior)
  - `NULL`
  - `NOT NULL`
  - `EMPTY`
  - `NOT EMPTY`
- Field filter remains “key anywhere in the JSON tree”.

## Decisions (locked)
- `EMPTY` means: value is exactly one of:
  - empty string `""`
  - empty array `[]`
  - empty object `{}`
  - and the key must exist somewhere in the JSON tree
- `NOT EMPTY` means:
  - key exists, and value is not empty by the rule above
  - numbers and booleans count as NOT EMPTY
  - JSON `null` is NOT EMPTY = false (use `NOT NULL` for that)

## API / DTO Changes
- For `type: "field"` filters, add `op`:
  - `op`: `"contains" | "null" | "not_null" | "empty" | "not_empty"`
- `valueContains` is only used when `op="contains"`; ignored otherwise.

Example payload:
```json
{
  "filters": [
    { "type": "field", "fieldPath": "status", "op": "not_null" },
    { "type": "field", "fieldPath": "details", "op": "contains", "valueContains": "Auto-generated" }
  ]
}
```

## Backend Implementation
- DTOs:
  - Add `op` to `FilterSpec`.
  - Extend internal filter representation (`FilterCriteria`) to include `op`.
- Normalization:
  - Default `op` to `contains` when missing.
- SQL generation (field filter):
  - Reuse existing “key anywhere” node search.
  - Let `v = jsonb_extract_path(node.value, <fieldKey>)` and require key exists via `jsonb_exists(node.value, <fieldKey>)`.
  - Implement op predicates:
    - `contains`: `v::text ILIKE %value%` (existing)
    - `null`: `jsonb_typeof(v) = 'null'`
    - `not_null`: `jsonb_typeof(v) <> 'null'`
    - `empty`:
      - `(jsonb_typeof(v) = 'string' AND v = '\"\"'::jsonb)`
      - `OR (jsonb_typeof(v) = 'array' AND jsonb_array_length(v) = 0)`
      - `OR (jsonb_typeof(v) = 'object' AND jsonb_object_length(v) = 0)`
    - `not_empty`:
      - `(jsonb_typeof(v) = 'string' AND v <> '\"\"'::jsonb)`
      - `OR (jsonb_typeof(v) = 'array' AND jsonb_array_length(v) > 0)`
      - `OR (jsonb_typeof(v) = 'object' AND jsonb_object_length(v) > 0)`
      - `OR (jsonb_typeof(v) IN ('number','boolean'))`
- Preserve current behavior: parse-error rows (`parsed IS NULL`) remain visible.

## Frontend Implementation
- Field filter row:
  - Add an op `<select>` with the 5 ops.
  - Hide/disable the value input unless `op=CONTAINS`.
- Default op for new Field filter is `CONTAINS`.
- Payload builder includes `op` and conditionally includes `valueContains`.

## Test Plan
### Backend unit tests
- Verify SQL fragments/params for each op.
- Verify parameter indices remain correct when combining multiple filters.

### Manual
- Create filters that target:
  - a missing key (should not match)
  - a key with `null`
  - a key with `""`, `[]`, `{}` and non-empty variants

## Acceptance Criteria
- Each op produces expected matches for key-anywhere semantics.
- UI prevents entering irrelevant “value” for non-CONTAINS ops.

## Assumptions
- Key matching stays literal and case-sensitive.

