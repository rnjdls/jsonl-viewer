# Decode Root Headers Before Insert

## Summary
- Decode base64 values in the root-level `headers` field before inserting parsed JSON into Postgres.
- Keep `raw_line` untouched; only mutate the `parsed` JSON node used for storage.

## Implementation Changes
- Create feature branch `feature/base64-headers`.
- Add a backend helper that walks only the root-level `headers` field after JSON parse.
- If `headers` is a string, attempt base64 decode and replace it only if it decodes to mostly printable text.
- If `headers` is an object, attempt base64 decode for each string value and replace only when valid.
- Leave non-string values untouched and leave invalid base64 strings unchanged.
- Apply the transform just after `objectMapper.readTree` and before creating `JsonlEntry`.
- Update README to note that `parsed.headers` is stored decoded when possible.

## Test Plan
- Ingest a JSONL line with root `headers` as base64 string and verify `parsed.headers` is decoded.
- Ingest a JSONL line with root `headers` as object and verify each decodable string value is decoded.
- Verify invalid base64 values remain unchanged.
- Confirm `raw_line` stored in DB is still the original line.

## Assumptions
- Only the root-level key `headers` is decoded.
- The decode heuristic uses the same "printable threshold" rule as the frontend helper.
