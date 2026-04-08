# Field Filter: Key Anywhere in JSON Tree

## Summary
- Change the "Field" filter so the input is treated as a key name and searched anywhere in the JSON tree.
- Keep the value-contains behavior; a match occurs when any occurrence of that key has a value containing the query.

## Implementation Changes
- Create feature branch `feature/field-filter-anywhere`.
- Update the field filter UI label/placeholder from "field.path" to "field key".
- Update frontend filter semantics to treat the input as a key, not a dot path.
- Update backend filter SQL to use a recursive JSONPath that finds the key anywhere:
  - Use a JSONPath like `$.**."key"` and test for any match with `jsonb_path_query` or `jsonb_path_exists`.
  - Apply `ILIKE` against each matching value’s text.
- Keep timestamp filters unchanged.
- Update README to document the new field filter behavior.

## Test Plan
- Filter by a nested key and verify counts/preview match.
- Filter by a root key and verify behavior still works.
- Confirm empty key does not affect results.
- Confirm value-contains still performs partial match.

## Assumptions
- Key matching is case-sensitive and literal.
- The field input no longer accepts dot-path syntax.
