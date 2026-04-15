# Copy JSON Line to Clipboard (Preview Cards)

## Summary
- Add a per-row **Copy** button in the preview list so users can copy the *exact* raw JSONL line for that entry.
- Copy uses `GET /api/entries/{id}/raw` on-demand (no change to preview payload size).
- Provide lightweight UX feedback (`Copy` → `Copying…` → `Copied`) and a clear error on failure.

## UX Details
- Place the button in each `JsonCard` header, beside the existing `Load body` / `Collapse` action.
- Behavior:
  - Default label: `Copy`
  - While fetching/copying: `Copying…` (disabled)
  - Success: `Copied` for ~1.2s, then returns to `Copy`
  - Failure: show an error message (reuse the existing app-level error banner) and return to `Copy`
- Accessibility:
  - Add `title="Copy raw line"` and `aria-label="Copy raw JSON line to clipboard"`
  - Keep the button keyboard-focusable and reachable without expanding the row.

## Implementation Changes (Frontend Only)
- `frontend/src/components/JsonCard/JsonCard.jsx`
  - Add a `Copy` button to the header action area.
  - Accept props for `onCopy`, `copyLabel`, and `copyDisabled` (or a single `copyState`).
- `frontend/src/components/JsonCard/JsonCard.css`
  - Update `.card-actions` to support multiple buttons (e.g., `display: flex; gap: 8px; align-items: center;`).
- `frontend/src/App.jsx`
  - Add a `handleCopyRawLine(id)` handler that:
    - Uses cached `entryRawById[id]` if present, otherwise calls `getEntryRaw(id)` and caches it in `entryRawById`.
    - Writes the raw text to the clipboard using a utility helper.
    - Tracks per-row copy status (`idle|copying|copied`) to drive button label/disabled state.
  - Reset copy state inside `resetPreviewState()` so it stays bounded to the active preview session.
- `frontend/src/utils/clipboard.js` (new)
  - `copyText(text)` helper:
    - Prefer `navigator.clipboard.writeText(text)`
    - Fallback for restricted environments: temporary hidden `<textarea>` + `document.execCommand("copy")`
    - Throw a descriptive error if both mechanisms fail.

## Edge Cases
- Very large lines:
  - Copy always uses the raw endpoint; show `Copying…` while the request is in-flight.
- Parse-error rows:
  - Copy still fetches `/raw` so users get the full original line even if the preview shows a truncated snippet.
- Clipboard restrictions:
  - Clipboard API requires a user gesture and is most reliable on `https` or `localhost`; fallback path should cover the common non-secure cases.

## Test Plan (Manual)
- Load a dataset, click **Load Preview**, then:
  - Click `Copy` on a normal row and paste into a text editor; verify it matches `GET /api/entries/{id}/raw`.
  - Click `Copy` multiple times quickly; ensure the button disables while copying and does not spam requests.
  - Expand a row and ensure `Copy` still works when `Load body` has been used.
- Validate a parse-error row:
  - For a row with `Load full raw`, click `Copy` and verify the clipboard contains the full raw line (not the truncated snippet).
- Force an error (stop backend / break proxy) and confirm:
  - Copy shows an error banner and does not get stuck in `Copying…`.

## Assumptions
- The backend `GET /api/entries/{id}/raw` returns the exact raw JSONL line as plain text for all entries.
- Keeping copied raw lines cached only for the current preview session is acceptable.
