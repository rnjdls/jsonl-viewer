# Topbar Clock + Dynamic Timezone

## Summary
Add a live “now” clock beside the existing **updated** topbar chip, plus a timezone dropdown that dynamically changes how timestamps are displayed. The selected timezone persists across reloads.

## UX / Behavior
- Topbar:
  - Keep the existing `updated` chip (shown only when `lastIngestedAt` is present), but format it using the selected timezone.
  - Add a new `now` chip **immediately beside** `updated` that shows the current client time and updates every second.
  - Add a `tz` chip with a dropdown selector (native `<select>`), placed next to the clock chips.
- Footer:
  - Format `Last ingest:` using the selected timezone (same as topbar `updated`).
- Timezone selection:
  - Default: Local/system timezone.
  - Persist selection to `localStorage` under key `jsonlLive.timeZone`.
  - Selection options:
    - `Local (<resolved IANA tz>)` (value: `local`, meaning “use system timezone”)
    - `UTC`
    - If available, include `Intl.supportedValuesOf("timeZone")` (sorted, deduped; exclude duplicates like `UTC`)
    - Fallback list (if `supportedValuesOf` is unavailable):
      - `America/New_York`
      - `America/Los_Angeles`
      - `Europe/London`
      - `Europe/Berlin`
      - `Asia/Manila`
      - `Asia/Singapore`
      - `Asia/Tokyo`
      - `Australia/Sydney`
- Edge cases:
  - If `lastIngestedAt` is missing, still show `tz` and `now`.
  - If `localStorage` is unavailable/blocked, the app should still work (ignore persistence failures).
  - If a persisted timezone value is invalid, fall back to `local`.

## Implementation Notes (Frontend)
- `frontend/src/App.jsx`
  - Add `timeZone` state with values: `local` or an IANA timezone (e.g. `UTC`, `America/New_York`).
  - On mount:
    - Load `jsonlLive.timeZone` from `localStorage` in a `try/catch`.
    - Validate the value; fallback to `local` if missing/invalid.
  - On change:
    - Update state and persist back to `localStorage` (also in `try/catch`).
  - Pass `timeZone` + `onTimeZoneChange` into `TopBar`.
  - Update footer `Last ingest:` formatting to use the shared datetime helper with `timeZone`.
- `frontend/src/components/TopBar/TopBar.jsx`
  - Add props: `timeZone`, `onTimeZoneChange`.
  - Implement the live clock with `useEffect` + `setInterval(1000)`; clear on unmount.
  - Replace direct `toLocaleTimeString()` usage for `updated` with the shared formatter.
  - Build timezone options from `Intl.supportedValuesOf("timeZone")` when available; otherwise use the fixed fallback list.
- `frontend/src/utils/datetime.js`
  - Export:
    - `TIMEZONE_LOCAL = "local"`
    - `isValidTimeZone(tz)` (defensive `Intl.DateTimeFormat(..., { timeZone: tz })` probe)
    - `coerceTimeZoneOrLocal(tz)` (returns `local` if invalid)
    - `formatTime(value, timeZone)` (HH:MM:SS in the chosen timezone; use system timezone when `local`)
    - `formatDateTimeTooltip(value, timeZone)` (for `title=` tooltips; include `timeZoneName: "short"`)
- Styling:
  - `frontend/src/components/TopBar/TopBar.css`: style the timezone `<select>` to visually match existing chips (mono font, compact height/padding, clear focus state).

## Test Plan
- Frontend dev:
  - `cd frontend && npm install && npm run dev`
  - Verify topbar shows `tz`, `updated` (when present), and `now` beside it.
  - Verify `now` updates every second.
  - Change timezone dropdown and confirm:
    - topbar `updated` changes formatting
    - footer `Last ingest:` changes formatting
  - Reload the page; confirm the timezone selection persists.
- Stack smoke:
  - `docker compose up --build`
  - Confirm frontend + backend boot and topbar renders without console errors.

## Acceptance Criteria
- A visible, ticking clock appears beside the existing `updated` chip.
- A timezone dropdown exists and changes the displayed timezone for topbar `updated`/`now` and footer `Last ingest`.
- The timezone choice persists across reloads without breaking in restricted-storage environments.

