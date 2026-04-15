# Auto-load Preview When Results Exist

## Summary
- Automatically load the first preview page when the app has data (`totalCount > 0`).
- After each Search, automatically load the first preview page for the newly-applied filters.
- Allow auto-preview even while exact counts are still `pending` (do not block on count completion).
- Avoid repeated auto-loads caused by the stats/count polling loop.

## Implementation Changes

### Frontend App Flow
- Add an auto-preview `useEffect` in `frontend/src/App.jsx` that:
  - waits for `stats.filePath` and a non-empty dataset (`counts.totalCount > 0`, or fall back to `stats.totalCount > 0`)
  - runs only once per `{filePath, sourceRevision}` (use a `useRef` key guard)
  - calls `fetchPreviewPage(null, 0)` when preview is inactive, not loading, and the UI is not locked
  - does not re-trigger due to user changing Lines/page or Direction (guard prevents repeats)

### Search → Preview Coupling
- Update preview fetch helpers in `frontend/src/App.jsx` so `fetchPreviewPage` can accept an optional filter payload override.
  - Goal: after clicking Search, fetch preview using `activeFilterPayload` immediately (without waiting for React state to settle).
- In `handleSearch`:
  - keep existing behavior: `applyFilters()`, `resetPreviewState()`, `await refreshCounts(activeFilterPayload)`
  - then auto-load preview page 1 via `fetchPreviewPage(null, 0, undefined, activeFilterPayload)` when:
    - the file exists (`stats.filePath`)
    - the dataset is non-empty (`totalCount > 0`)
    - and (optimization) skip when exact match is ready and `matchCount === 0`

### UX Copy
- Update the preview-empty copy (“preview is off by default…”) to reflect the new default behavior.
- Ensure the preview-empty block does not render while an auto-load is in progress (`!previewActive && !previewLoading`).

### Tests
- Update/add frontend tests in `frontend/src/App.test.jsx`:
  - Auto-load preview on initial render for a non-empty file (assert `getPreview` is called without clicking Load Preview).
  - Auto-load preview after Search (add a filter, click Search, assert a new `getPreview` call includes that filter payload).
  - No auto-load when `totalCount === 0`.
  - Preserve the assertion that preview requests omit `sortBy` and include `sortDir`.

## Public API / Interface Changes
- None. Reuses existing `POST /api/filters/preview` contract.

## Test Plan
- `cd frontend && npm test`
- Smoke: `docker compose up --build` and confirm:
  - Preview rows appear automatically on initial load when the file has rows.
  - After Search, preview reloads automatically with the new filters.
  - Manual Load/Reload Preview still works and paging still works.

## Assumptions
- Auto-preview triggers on initial load and on Search only (not on Lines/page or Direction changes).
- When exact counts are `pending`, preview still auto-loads immediately; if there are no matches, the preview may show “No preview rows available.”

