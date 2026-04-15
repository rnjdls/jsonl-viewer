# Remove Preview Sort-By Selector and Backend Sort Variants

## Summary
- Remove the preview `Sort by` control from the UI and keep only `Lines/page` and `Direction`.
- Fix preview ordering to `lineNo` with `id` as the stable tie-breaker.
- Remove backend preview logic that supports `timestamp` and `id` sort modes.
- Preserve preview filtering, page size selection, direction switching, and keyset pagination.

## Implementation Changes

### Frontend
- Remove `DEFAULT_SORT_BY`, `previewSortBy` state, its change handler, and the `Sort by` `<select>` from `frontend/src/App.jsx`.
- Keep `previewLimit` and `previewSortDir`; preview requests should send only `filtersOp`, `filters`, `cursor`, `sortDir`, and `limit`.
- Preserve the current preview reset behavior when `Lines/page` or `Direction` changes.
- Update `frontend/src/App.test.jsx` to verify the `Sort by` control is no longer rendered and preview requests do not include `sortBy`.

### Backend Preview Flow
- Remove `sortBy` from `PreviewRequest`, preview controller handling, repository method signatures, and the preview cursor model.
- Simplify `JsonlController` preview handling to normalize only `sortDir`, build/decode cursors for fixed line ordering, and stop resolving timestamp field paths for preview sorting.
- Collapse `PreviewQueryBuilder` to a single ordering path:
  `ORDER BY e.line_no {dir}, e.id {dir}`
- Keep keyset pagination based on `line_no` plus `id`, with matching cursor predicates for both ascending and descending direction.
- Simplify `PreviewCursorCodec` so the cursor payload contains only the fields needed for line-based paging: `sortDir`, `lineNo`, and `id`.

### Docs
- Update the preview API section in `README.md` to remove `sortBy` from the request contract and describe fixed line-based ordering.
- Remove documentation that describes preview timestamp/id sort behavior.

## Public API / Interface Changes
- `POST /api/filters/preview` request body becomes `{ filters, filtersOp, sortDir, cursor, limit }`.
- `sortBy` is no longer a supported preview request field.
- Preview cursor format changes from multi-sort metadata to fixed line-order metadata.
- Repository preview contracts drop `sortBy` and preview-only timestamp sort parameters.

## Test Plan
- Run `cd backend && mvn test`.
- Run `cd frontend && npm run test -- --run`.
- Run a boot smoke check with `docker compose up --build` and confirm:
  - Preview shows only `Lines/page` and `Direction`.
  - Loading preview still works with and without filters.
  - Changing direction resets preview and reverses line order.
  - Next, Prev, and Page navigation still work with the simplified cursor.
- Add or update regression coverage for:
  - missing `sortDir` defaulting to `desc`
  - cursor/request direction mismatch returning `400`
  - unchanged preview row response shape

## Assumptions
- Default preview direction remains `desc` so the first preview load stays newest-first by line number.
- If older clients still send `sortBy`, the backend may ignore it because `PreviewRequest` is already annotated with `@JsonIgnoreProperties(ignoreUnknown = true)`; it is no longer part of the supported contract.
- Timestamp filtering behavior remains unchanged; this plan removes preview sort variants only.
