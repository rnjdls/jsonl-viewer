# Large-Ingest UI Responsiveness Plan

## Summary

- Fix browser lag during large file ingest by removing O(totalPages) page navigation rendering, isolating preview rendering from filter edits and stats polling, and deferring exact filtered counts while file-mode ingest is still catching up.
- Preserve preview browsing during active ingest, but only restore exact match totals and exact page totals once the backend reports that exact counting is safe again.
- Keep the scope focused on the existing file-ingest flow. Kafka or any source without reliable byte progress keeps current count behavior.

## Implementation Changes

- Replace the preview page-number `<select>` in `frontend/src/App.jsx` with sequential cursor navigation only.
- Keep `Prev` and `Next`, always show `Page X`, and only show `of Y` when exact count is available and ready.
- Remove the current code path that generates missing cursors for arbitrary page jumps, since random-access page jumping is being removed.
- Extract the preview area into a memoized preview subtree so typing in filters does not rerender the preview header, pager, or card list unless preview-specific props changed.
- Memoize `JsonCard` so stats refreshes and filter edits do not rebuild each visible row when the row payload is unchanged.
- Keep the current filter ownership in `App` for this pass, but stabilize preview props and handlers so parent rerenders stay cheap.
- Extend `StatsResponse` and `JsonlController` with a new `exactCountAvailable` boolean.
- Set `exactCountAvailable=true` when ingestion is paused.
- Set `exactCountAvailable=true` in file mode when `ingestedBytes` and `targetBytes` are both known and `ingestedBytes >= targetBytes`.
- Set `exactCountAvailable=true` when size progress is unavailable, so Kafka and unknown-progress modes preserve current behavior.
- Set `exactCountAvailable=false` only for active file-mode ingest where progress is known and `ingestedBytes < targetBytes`.
- Change the frontend count flow so it does not call `getCounts` and does not poll `getCountStatus` while `exactCountAvailable=false`.
- Introduce a frontend-only deferred-count UI state for filtered searches during active ingest.
- When `exactCountAvailable` flips back to `true`, submit one fresh count request for the current applied filters and resume the normal pending-to-ready flow.
- Ignore any count response whose `sourceRevision` no longer matches the latest stats revision.
- Update `SearchBar` messaging so filtered searches show that preview is available while exact count waits for ingest to catch up during the deferred state.
- Avoid redundant top-level rerenders by only updating `stats` and `counts` state when the meaningful payload actually changed.

## Public Interfaces

- `GET /api/stats` adds `exactCountAvailable: boolean`.
- Preview navigation behavior changes from random page selection to sequential navigation only.
- The page-number dropdown is removed from the frontend UI.

## Test Plan

- Add a frontend regression test that the preview page-number combobox is no longer rendered and `Prev` and `Next` still work.
- Add a frontend test that filtered search still loads preview while `exactCountAvailable=false` and does not call `getCounts`.
- Add a frontend test that counts are requested once `exactCountAvailable` becomes `true` and the UI transitions from deferred to pending to ready.
- Add a frontend test that `Page X of Y` appears only when exact count is ready, and deferred or pending states show only the current page with sequential navigation.
- Add a frontend test that unchanged stats payloads do not trigger extra preview work or unnecessary state churn.
- Extend backend stats controller tests to cover `exactCountAvailable=false` when active file ingest is behind target bytes.
- Extend backend stats controller tests to cover `exactCountAvailable=true` when bytes match and when ingestion is paused.

## Assumptions

- This plan targets the large file-ingest path that exposed the lag. Kafka and unknown-progress sources intentionally keep current exact-count behavior.
- Removing arbitrary page jumping is acceptable and is the primary fix for the O(totalPages) DOM and render cost.
- The 3-second stats poll interval stays as-is in this pass. The optimization is to make polling cheaper and to stop exact-count churn during active ingest.
- No database migration is required for this work.
