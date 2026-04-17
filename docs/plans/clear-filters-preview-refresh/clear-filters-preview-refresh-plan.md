# Clear-All Preview Refresh

## Summary
- Fix the frontend so clicking the toolbar `✕ Clear all` always reloads the preview to page 1 using an empty filter payload, instead of only clearing local filter state.
- Keep the current count-first flow: refresh counts first, then reload preview if data is available.
- Scope is limited to the explicit `✕ Clear all` action. Per-row filter removal keeps its current behavior.

## Implementation Changes
- In `frontend/src/App.jsx`, add a dedicated `handleClearAll` callback instead of passing the hook's raw `clearAllFilters` directly to `SearchBar`.
- `handleClearAll` should mirror `handleSearch` behavior, but use a locally constructed empty payload such as `{ filtersOp, filters: [] }` so it does not depend on React state settling after `clearAllFilters()`.
- Handler sequence:
  1. Bail out if `uiLocked`.
  2. Call `clearAllFilters()`.
  3. Call `resetPreviewState()` so stale rows, cursor history, expanded cards, and page index are cleared.
  4. Call `refreshCounts(emptyPayload)`.
  5. If there is no file or the refreshed total count is `0`, stop there.
  6. Otherwise call `fetchPreviewPage(null, 0, undefined, emptyPayload)` so preview restarts from page 1 with the same `sortDir` and `limit`.
- Keep `filtersOp` unchanged when clearing filters; only the filter list and applied set reset.
- Rewire `onClearAll` in `frontend/src/components/SearchBar/SearchBar.jsx` to use the new App-owned handler. No UI copy or prop-shape changes are needed.

## Public Interfaces
- No backend or API changes.
- No request payload shape changes.
- `SearchBar`'s public prop contract stays the same: `onClearAll` remains a no-arg callback.

## Test Plan
- Add a regression test in `frontend/src/App.test.jsx` covering the applied-filter path:
  - initial auto-preview runs
  - apply a filter and click `Search`
  - click `✕ Clear all`
  - assert the next `getCounts` and `getPreview` calls use `filters: []`, preserve `sortDir` and `limit`, and request `cursor: null`
- Add a second regression test for the draft-only path:
  - initial auto-preview runs
  - add a filter but do not click `Search`
  - click `✕ Clear all`
  - assert preview still reloads with the empty payload
- Validate that clear-all resets pagination to page 1 by asserting the reload uses `cursor: null`.
- Run `cd frontend && npm test -- --run` after implementation.

## Assumptions
- "Clear filters" means the toolbar `✕ Clear all` action only.
- Clearing filters should always attempt an unfiltered refresh, even if the current filters were only drafts and had not been applied yet.
- Existing behavior for individual row removal, AND/OR toggle state, and backend filter evaluation remains unchanged.
