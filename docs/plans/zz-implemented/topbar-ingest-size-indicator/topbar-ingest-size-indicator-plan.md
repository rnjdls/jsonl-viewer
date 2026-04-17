# Topbar Parsed Chip Removal + Ingest Size Indicator

## Summary
- Remove the `parsed` count chip from the top bar.
- Add a non-interactive ingest progress row inside the hamburger dropdown, above the admin action buttons.
- Display the row as `ingested: <current-size>/<target-size>`.
- Keep `parsedCount` in the backend stats payload; this is a UI removal, not a backend removal.

## UX / Behavior
- Topbar:
  - Remove the visible `parsed` chip entirely.
  - Keep the existing `total`, `errors`, `search`, `ingest`, `updated`, `now`, and `tz` chips unchanged.
- Hamburger menu:
  - Add a presentational status row above `Reload File` and `Delete All`.
  - The row is informational only, not clickable, and must not be exposed as a `menuitem`.
  - Keep the existing menu actions and their current handlers, disabled states, and loading states unchanged.
- Ingest size text:
  - Format: `ingested: <current-size>/<target-size>`.
  - Use the target size to choose the display unit, then apply that same unit to both values.
  - If `targetBytes < 1,000,000,000`, render both values in `MB` with no decimals.
  - If `targetBytes >= 1,000,000,000`, render both values in `GB` with 2 decimals.
  - Use decimal units:
    - `1 MB = 1,000,000 bytes`
    - `1 GB = 1,000,000,000 bytes`
  - Use standard rounding for display only.
- Color rules:
  - When `ingestedBytes !== targetBytes`, render the current size in red and the target size in yellow.
  - When `ingestedBytes === targetBytes`, render both sizes in green.
  - Keep the `ingested:` label and `/` separator in the normal dropdown text color.
- Unavailable data:
  - If there is no file source, the app is not in file mode, or the target size cannot be determined, hide the row entirely.

## Implementation Notes
- Backend:
  - Extend `backend/src/main/java/com/jsonl/viewer/api/dto/StatsResponse.java` with nullable `ingestedBytes` and `targetBytes`.
  - Update `backend/src/main/java/com/jsonl/viewer/api/JsonlController.java` so `/api/stats` returns:
    - `ingestedBytes` from `IngestState.byteOffset`
    - `targetBytes` from the current on-disk file size in file mode
  - Do not add a DB migration; reuse existing persisted `byteOffset`.
  - In non-file mode or when file size lookup is unavailable, return `null` for both new size fields.
- Frontend:
  - `frontend/src/App.jsx`
    - Pass the new stats fields into `TopBar`.
    - Keep the rest of the stats wiring unchanged.
  - `frontend/src/components/TopBar/TopBar.jsx`
    - Remove the rendered `parsed` chip.
    - Add a formatter/helper for the ingest size label and match state.
    - Render the new informational row in the dropdown above the action buttons.
  - `frontend/src/components/TopBar/TopBar.css`
    - Add styles for the informational row and per-value color states.
    - Reuse the existing visual language and add an explicit success green token/class for matched state.

## API Changes
- `GET /api/stats`
  - Add `ingestedBytes: number | null`
  - Add `targetBytes: number | null`

## Test Plan
- Backend tests:
  - Add controller coverage proving `/api/stats` returns `ingestedBytes` and `targetBytes` in file mode.
  - Add controller coverage proving the new fields are `null` when file-size progress is unavailable.
- Frontend tests:
  - Update `frontend/src/App.test.jsx` to verify the `parsed` chip is no longer rendered.
  - Verify the menu shows an incomplete MB case such as `ingested: 512 MB/1024 MB`.
  - Verify the menu shows a GB case such as `ingested: 1.25 GB/2.00 GB`.
  - Verify exact byte equality switches both values to green.
  - Verify the row is hidden when the size fields are unavailable.
- Stack smoke:
  - `docker compose up --build`
  - Confirm backend and UI both start successfully.
  - Open the admin menu and verify the indicator transitions from mismatch colors to green when ingestion catches up.

## Assumptions
- GB mode uses exactly 2 decimal places.
- MB mode uses no decimal places.
- Matching is based on raw bytes, not the formatted text.
- The new row is shown on all screen sizes whenever both size values are available.
