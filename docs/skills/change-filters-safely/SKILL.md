---
name: change-filters-safely
description: Add or modify filter and search behavior in this repo while keeping backend filter logic, frontend payload mapping, and search UI in sync with count-first preview and keyset pagination invariants.
---

# `change-filters-safely`

Use this workflow whenever filter/search behavior, filter payloads, or filter UI changes.

## Required touchpoints

- Backend filter logic: `backend/src/main/java/com/jsonl/viewer/service/FilterService.java`
- Frontend payload mapping: `frontend/src/App.jsx`
- Search UI: `frontend/src/components/SearchBar/SearchBar.jsx`

## Invariants

- UI requests counts and preview pages from `/api/*`; do not load full files in the browser.
- Preserve count-first behavior.
- Preserve keyset pagination with the opaque cursor, including `sortBy/sortDir` plus stable `id` tie-breaking.

## Checklist

1. Update filter normalization and SQL/query behavior in `FilterService`.
2. Update frontend request payload construction in `App.jsx` whenever a filter field, operator, or payload shape changes.
3. Update visible filter controls and labels in `SearchBar` when the UI needs to expose the new behavior.
4. Add or update regression coverage for the changed backend filter behavior, especially `backend/src/test/java/com/jsonl/viewer/service/FilterServiceTest.java`.
5. Re-check preview requests and responses so filtering still works with sorting, cursor navigation, and page-to-page preview loading.

## Regression checks

- The request payload shape still matches backend expectations.
- Applying or clearing filters produces the expected preview rows and counts.
- Changing filters does not break cursor flow or preview pagination.
- New operators or fields behave correctly alongside existing text, timestamp, and field filters.
