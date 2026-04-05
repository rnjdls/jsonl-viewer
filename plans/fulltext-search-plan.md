# Optional Full-Text Search (Parsed JSON)

## Summary
- Add an optional full-text search filter over parsed JSON text.
- Allow users to add a "Text" filter that matches any parsed JSON content.

## Implementation Changes
- Create feature branch `feature/fulltext-search`.
- Add a new filter type `text` with a `query` string in frontend and backend DTOs.
- Update filter normalization and SQL builder to include:
  - `to_tsvector('simple', parsed::text) @@ plainto_tsquery('simple', ?)`
- Add a new "Text" filter row in the UI, similar to Field/Timestamp filters.
- Update README API docs to include the new filter type.
- Document an optional GIN index for performance:
  - `CREATE INDEX jsonl_entry_parsed_fts_idx ON jsonl_entry USING GIN (to_tsvector('simple', parsed::text));`

## Test Plan
- Add a text filter query and verify counts/preview match expected rows.
- Confirm empty query does not affect results.
- If index is created manually, sanity-check that query latency improves on larger files.

## Assumptions
- Full-text search uses the `simple` dictionary.
- Search applies only to `parsed` JSON text, not `raw_line`.
