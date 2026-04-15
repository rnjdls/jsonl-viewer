# Confirmed Reload/Delete With Global UI Lock

## Summary
- Add a custom confirmation dialog for `Reload File` and `Delete All`.
- After the user confirms, switch the app into a global admin-operation lock state with a full-screen loading overlay.
- Keep the app locked until the selected admin action finishes and the immediate UI refresh completes.
- Keep the existing backend admin endpoints and data flow unchanged.

## UX / Behavior
- Confirmation step:
  - Clicking `Reload File` opens a dialog explaining that current rows will be cleared and the source will be reloaded from the beginning.
  - Clicking `Delete All` opens a dialog explaining that current rows will be removed and the source position will move to the end/newest point.
  - The dialog uses explicit `Cancel` and `Yes` actions.
  - Default focus lands on `Cancel`.
- Blocking state:
  - After the user confirms, replace the dialog with a full-screen loading overlay.
  - While the overlay is shown, the user cannot interact with search, filters, preview controls, row actions, topbar controls, or the admin menu.
  - Show action-specific progress copy such as `Reloading source...` or `Deleting rows...`.
- Completion:
  - Keep the UI blocked until the admin request finishes and the app completes its immediate `stats` and initial `count` refresh.
  - Do not wait for deferred exact-count background polling to finish before unlocking the UI.
- Error handling:
  - If the admin action fails, remove the overlay, restore interactivity, and surface the existing error banner.

## Frontend Implementation
- `frontend/src/App.jsx`
  - Add a small admin action state model for:
    - which action is being confirmed
    - which action is currently executing
    - whether the UI is globally locked
  - Replace direct `handleReload` / `handleReset` menu execution with a two-step flow:
    - request confirmation
    - execute after the user confirms
  - Keep the existing success flow after confirmation:
    - call the selected admin API
    - await `refreshStats()`
    - await the first `refreshCounts()` response for the current applied filters
    - reset preview state
    - clear the lock/overlay
  - Add shared action metadata so dialog title/body, confirm button label, and loading text stay centralized for `reload` and `reset`.
- `frontend/src/components/TopBar/TopBar.jsx`
  - Keep the hamburger menu behavior from the current implementation.
  - Change `Reload File` and `Delete All` so they open the confirmation dialog instead of executing immediately.
  - Close the hamburger menu before opening the dialog.
  - Accept a global lock prop and disable the pause/resume button, menu trigger, and admin menu items while the app is locked.
- `frontend/src/components/SearchBar/SearchBar.jsx`
  - Accept a global disabled prop and apply it to:
    - add-filter buttons
    - AND/OR toggle buttons
    - clear-all button
    - search button
    - all filter row inputs/selects/remove buttons
- `frontend/src/components/JsonCard/JsonCard.jsx`
  - Accept a global disabled prop and disable copy, load body, load full raw, collapse, and expand-all actions while locked.
- Styling
  - Add dialog and full-screen overlay styles in the frontend CSS using the app’s existing visual language.
  - Mark the app root as busy while the overlay is visible and ensure the overlay captures pointer interaction.

## API / Interface Changes
- Backend API:
  - None. Continue using `POST /api/admin/reload` and `POST /api/admin/reset`.
- Frontend component props:
  - `TopBar`: add a global lock prop.
  - `SearchBar`: add a global disabled prop.
  - `JsonCard`: add a global disabled prop.

## Test Plan
- Add minimal frontend test tooling in `frontend`:
  - `vitest`
  - `jsdom`
  - `@testing-library/react`
  - `@testing-library/user-event`
  - `@testing-library/jest-dom`
- Update `frontend/vite.config.js` with test configuration and add `test` plus `test:coverage` scripts.
- Automated coverage:
  - Reload opens a confirmation dialog and `Cancel` makes no API call.
  - Delete All opens a distinct destructive confirmation dialog with the correct copy.
  - Confirming either action shows the blocking overlay and disables the rest of the UI until the admin API call plus immediate refreshes resolve.
  - A count response with `status: "pending"` still clears the overlay and leaves background polling intact.
  - Admin-action failure clears the overlay and shows an error.
- Verification:
  - `cd frontend && npm run build`
  - `cd frontend && npm run test -- --run --coverage`
  - `docker compose up --build`
  - Confirm backend + UI start successfully and manually verify both confirmation flows.

## Assumptions
- “Operation done” means the confirmed admin action has finished and the app has completed its immediate `stats` and initial `count` refresh.
- The global lock applies to the full interactive UI, not only search and filter controls.
- The prompt copy should stay accurate in both file and Kafka ingest modes even though the visible label remains `Reload File`.
