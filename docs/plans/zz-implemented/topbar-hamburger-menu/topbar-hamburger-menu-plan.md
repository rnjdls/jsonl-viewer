# Topbar Hamburger Menu + Revision Chip Removal

## Summary
- Add an always-visible hamburger menu on the right side of the `TopBar`.
- Move `Reload File` and `Delete All` into that dropdown menu.
- Remove the `rev` chip from the topbar entirely.
- Keep `Pause` / `Resume` as the only always-visible admin action in the topbar.
- Keep all backend APIs and data flow unchanged.

## UX Details
- Location:
  - Add the hamburger trigger at the far right of the topbar controls.
  - Keep the existing source block, status chips, and timezone controls in place.
- Menu contents:
  - `Reload File`
  - `Delete All`
- Button/menu behavior:
  - `Pause` / `Resume` stays directly visible in the topbar.
  - `Reload File` and `Delete All` use the existing handlers and disabled/loading states.
  - `Delete All` keeps destructive styling inside the menu.
  - On menu item click, invoke the action and close the menu immediately.
- Accessibility and interaction:
  - The trigger must be a real `button`.
  - Add `aria-haspopup="menu"` and `aria-expanded`.
  - Close the menu on outside click.
  - Close the menu on `Escape`.
  - Return focus to the trigger when the menu closes from keyboard interaction.
- Revision display:
  - Remove the `rev` chip completely.
  - Do not move `sourceRevision` into another visible topbar element.
- Responsive behavior:
  - Preserve the current wrapped topbar layout on narrower widths.
  - Ensure the hamburger remains reachable and the dropdown is visually anchored to it.

## Frontend Implementation
- `frontend/src/App.jsx`
  - Stop passing `sourceRevision` into `TopBar`.
  - Keep `stats.sourceRevision` for existing exact-count freshness logic.
  - Reuse the current `handleReload`, `handleReset`, and loading state wiring without changing behavior.
- `frontend/src/components/TopBar/TopBar.jsx`
  - Remove the rendered revision chip.
  - Replace the inline `Reload File` and `Delete All` buttons with a local-state dropdown menu.
  - Keep `Pause` / `Resume` inline.
  - Add the minimal React state/effects needed for menu open/close, outside-click handling, and keyboard dismissal.
- `frontend/src/components/TopBar/TopBar.css`
  - Add styles for the hamburger trigger, dropdown container, menu items, and focus states.
  - Keep the current visual language for button variants.
  - Add a destructive menu-item treatment for `Delete All`.

## API Changes
- None.

## Test Plan
- Frontend verification:
  - `cd frontend && npm run build`
  - Verify the topbar renders without layout regressions.
  - Verify the hamburger is visible on desktop and narrow widths.
  - Verify `Reload File` and `Delete All` are available only from the menu.
  - Verify `Pause` / `Resume` remains directly clickable.
  - Verify disabled/loading states still match current behavior when no file is loaded and while admin actions are in flight.
  - Verify outside click and `Escape` close the menu.
  - Verify the revision chip is no longer rendered.
- Stack smoke:
  - `docker compose up --build`
  - Confirm backend + UI start successfully and the topbar renders without console errors.

## Assumptions
- The hamburger menu is shown on all screen sizes, not only mobile.
- Only `Reload File` and `Delete All` move into the menu.
- No new frontend test tooling is added for this UI-only change.
