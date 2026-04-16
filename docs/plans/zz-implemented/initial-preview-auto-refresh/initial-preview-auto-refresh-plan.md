# Keep Auto-Refreshing Initial Preview Until Page 1 Matches The Current Lines/Page Setting

## Summary
- Keep this as a frontend-only change in `frontend/src/App.jsx` with test updates in `frontend/src/App.test.jsx`.
- Change the initial page-load preview flow so page 1 keeps reloading in the background until the rendered row count matches the current page-1 expectation for the active `Lines/page` setting.
- Do not change backend APIs, database state, or preview cursor semantics.

## Public/API and Config Changes
- No backend, database, or HTTP API changes.
- Reuse the existing `/api/stats` polling and `/api/filters/preview` endpoints.

## Implementation Changes
- Treat this as an `initial page 1 auto-refresh` mode only. It applies to the default auto-loaded preview on first load and is cleared by user-driven preview actions.
- Keep the existing one-shot initial auto-preview trigger, then add a follow-up retry loop that stays active while all of these remain true:
  - no applied filters
  - current preview is page 1 (`cursor = null`, `pageIndex = 0`)
  - the preview was entered by the automatic initial-load path, not by manual reload, search, or paging
  - the UI is not locked and no preview request is already in flight
- Define “matches the line/page configuration” for this scope as:
  - matched immediately when `previewRows.length === previewLimit`
  - otherwise matched when `previewRows.length >= stats.totalCount`
  - otherwise not matched, so retry page 1 after 3 seconds
- Use `stats.totalCount` as the source of truth for the retry target in this mode, not `counts.totalCount`, because the unfiltered `counts` payload is not continuously refreshed while ingest advances.
- Re-evaluate the match condition whenever either the preview response changes or the latest stats poll changes `totalCount`. This allows the page to refill again if ingest grows after the first preview already rendered.
- Clear the auto-refresh mode as soon as the user leaves the initial-load context:
  - Search
  - manual `Load Preview` / `Reload Preview`
  - changing `Lines/page`
  - changing direction
  - paging next, prev, or page-select
  - admin reload/reset lock
  - file/source change
- Keep current empty-state and paging behavior unchanged outside this initial page-1 mode.

## Test Plan
- Extend `frontend/src/App.test.jsx` with fake-timer coverage for:
  - initial auto-preview returns fewer rows than `stats.totalCount`, then keeps retrying until page 1 reaches the expected row count
  - initial auto-preview stops retrying once page 1 reaches `min(previewLimit, stats.totalCount)`
  - initial auto-preview re-arms when later stats polls increase `totalCount` beyond the currently rendered page-1 rows
  - retry loop is not used for manual reload, search, sort, lines-per-page, or page navigation flows
  - retry loop stops when the user leaves the initial page-1 auto-preview context
- Keep the existing initial auto-load and zero-total-count tests passing.
- Run `cd frontend && npm test -- --run App.test.jsx`.
- Run `docker compose up --build` and confirm backend + UI start.

## Assumptions
- Scope is only the initial auto-loaded page 1 preview.
- “Matches the line/page configuration” means matching the expected row count for page 1 under the current `Lines/page` value, not keeping later user-selected pages live-refreshed.
- No new UI indicator is needed for the background refresh loop.
