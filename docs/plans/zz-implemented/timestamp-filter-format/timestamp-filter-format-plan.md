# Configurable / Dynamic Timestamp Input Format (Timestamp Range Filter)

## Summary
- Create feature branch `feature/timestamp-filter-format`.
- Change the timestamp range filter inputs to accept **text** timestamps so users can paste production timestamps with:
  - fractional seconds (including nanos)
  - timezone offsets (`+08:00`)
  - optionally epoch seconds/millis
- Backend timestamp parsing becomes permissive and deterministic (defined below).

## Decisions (locked)
- Timestamp inputs are **free-text** (not `datetime-local`).
- Naive timestamps (no timezone) are interpreted as **UTC**.
- Epoch numeric strings are supported:
  - `> 1_000_000_000_000` = millis
  - otherwise = seconds

## Frontend Changes
- Update timestamp filter row UI:
  - `from` and `to` inputs become `type="text"`.
  - Add placeholder examples:
    - `2026-04-06T13:23:58.801145590Z`
    - `2026-04-06T13:23:58+08:00`
    - `1712560000` (epoch seconds) / `1712560000000` (epoch millis)
- Remove/replace the helper that force-appends `Z` (`toUtcTimestampPayload`), and send the trimmed string verbatim.

## Backend Changes
- Update filter timestamp parsing (used by both count and preview) with this exact parse order:
  1) blank → `null`
  2) integer string → epoch:
     - `> 1_000_000_000_000` → `Instant.ofEpochMilli(value)`
     - else → `Instant.ofEpochSecond(value)`
  3) `Instant.parse(raw)` (RFC3339 with `Z`, supports fractional seconds)
  4) `OffsetDateTime.parse(raw).toInstant()` (supports timezone offsets)
  5) `LocalDateTime.parse(raw)` interpreted as UTC (no timezone in input)
  6) If raw contains a single space separator (e.g. `2026-04-06 13:23:58`), replace first space with `T` and retry steps 4–5
- If none match, treat as invalid and ignore the bound (current behavior: invalid values do not constrain results).

## Test Plan
### Backend unit tests
- Accepts:
  - `2026-04-06T13:23:58.801145590Z`
  - `2026-04-06T13:23:58+08:00`
  - `2026-04-06T13:23:58` (treated as UTC)
  - `2026-04-06 13:23:58` (space separator)
  - epoch seconds and epoch millis
- Rejects/ignores:
  - malformed strings (no thrown exception; filter omitted)

### Manual
- Use `data/generated.jsonl` and filter by fractional-second ranges; verify counts and preview results match expectation.

## Acceptance Criteria
- User can paste production timestamps with fractional seconds and offsets and get correct filtering.
- Invalid timestamp strings do not break the UI or backend; they simply don’t apply.

## Assumptions
- Backend `ts` column remains `TIMESTAMPTZ` and filter is applied against `ts` (as today).

