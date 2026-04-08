# Fix: Unsupported Unicode Escape Sequence (Sanitize Parsed JSON Before Insert)

## Summary
- Create fix branch `fix/ingest-unicode-sanitize`.
- Prevent ingestion failures caused by Unicode sequences that Postgres rejects in `jsonb` (commonly `\\u0000` and unpaired surrogates).
- Keep `raw_line` stored **exactly** as read from the file.

## Decisions (locked)
- Sanitization applies to the **parsed JSON** only.
- Replacement strategy: replace problematic code units with **U+FFFD** (replacement character):
  - NUL (`\\u0000` → `\u0000` in Java string) → `\uFFFD`
  - any surrogate code unit in range `\\uD800-\\uDFFF` → `\uFFFD`

## Backend Implementation
- Add a small utility that walks a `JsonNode` tree **in-place**:
  - If node is `ObjectNode`: recurse into fields.
  - If node is `ArrayNode`: recurse into elements.
  - If node is textual: sanitize the string and replace the node with sanitized text if it changed.
- Apply sanitizer in ingest flow:
  1) `objectMapper.readTree(rawLine)`
  2) `Base64HeadersDecoder.decodeRootHeaders(node)`
  3) `JsonUnicodeSanitizer.sanitize(node)`  ✅ new step
  4) extract timestamp + persist
- Do not modify `rawLine`.

## Test Plan
- Add a JUnit test using the existing reflection pattern for `JsonlIngestService.parseLine`:
  - Input contains `\\u0000` inside a JSON string.
  - Input contains unpaired surrogate escape `\\uD800`.
  - Assert:
    - `entry.getRawLine()` equals the original raw JSON string
    - `entry.getParsed().toString()` does not contain `\\u0000` or `\\uD800`
    - string values contain no `\u0000` and no surrogate code units

## Acceptance Criteria
- Ingestion does not fail when encountering production lines that previously caused “unsupported Unicode escape sequence”.
- Stored `parsed` JSON is valid for Postgres `jsonb`.
- Stored `raw_line` is unchanged.

## Assumptions
- The production error comes from Postgres JSONB input restrictions (not from invalid UTF-8 bytes in the source file).

