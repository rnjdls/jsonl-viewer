# Expand All JSON Fields (Body View)

## Summary
- Create feature branch `feature/expand-all-json-body`.
- Improve preview-card UX by adding an **Expand all** action after a row body has been loaded.
- Expand all uncollapses the entire JSON tree for that card so users don’t need to click each nested node.
- No backend/API changes.

## UX Details
- Placement:
  - In each `JsonCard` header action area.
  - Only shown when the card is expanded **and** the parsed body is present (i.e., after `Load body` succeeds).
- Label:
  - Button text: `Expand all`
  - Tooltip/title: `Expand all JSON fields`
- Behavior:
  - Clicking `Expand all` uncollapses every object/array node in the rendered body tree for that card.
  - No additional network requests; this is purely a UI state change.
  - Clicking `Collapse` returns the card to compact view and resets the body tree to the default collapsed depth on next expand.
- Accessibility:
  - Add `aria-label="Expand all JSON fields"`.
  - Keep keyboard focus and tab order consistent with the existing `Copy` / `Collapse` buttons.

## Implementation Changes (Frontend)
- `frontend/src/App.jsx`
  - Add per-row state `jsonTreeExpandTokenById` (number) to drive “expand all” as a simple incrementing token.
  - Add `handleExpandAllJsonTree(id)`:
    - `setJsonTreeExpandTokenById((prev) => ({ ...prev, [id]: (prev[id] ?? 0) + 1 }))`
  - Update `handleCollapseBody(id)` to also clear `jsonTreeExpandTokenById[id]` so reopening defaults to standard collapse depth.
  - Pass new props into `JsonCard`:
    - `onExpandAll={() => handleExpandAllJsonTree(row.id)}`
    - `expandAllToken={jsonTreeExpandTokenById[row.id] ?? 0}`
- `frontend/src/components/JsonCard/JsonCard.jsx`
  - Add props: `onExpandAll`, `expandAllToken`.
  - Render an `Expand all` secondary button when `expanded && hasParsedBody`.
  - Pass `expandAllToken` down only for the Body renderer:
    - `<JsonValue val={body.parsed} depth={0} expandAllToken={expandAllToken} />`
- `frontend/src/components/JsonValue/JsonValue.jsx`
  - Add prop: `expandAllToken = 0`.
  - Initialize collapsed state so that when `expandAllToken > 0`, nodes start uncollapsed.
  - Add an effect that runs when `expandAllToken` changes and calls `setCollapsed(false)`.
  - Thread `expandAllToken` through recursive `JsonValue` calls so already-mounted and newly-mounted descendants both expand.

## Test Plan (Manual)
- Load preview and expand a row with a nested body (depth ≥ 2):
  - Click `Load body`, confirm `Expand all` appears.
  - Click `Expand all`, confirm all nested nodes become visible without per-node clicking.
  - Confirm DevTools shows **no** new API requests on `Expand all`.
- Click `Collapse`, then expand again and confirm the tree is back to the default collapsed-depth behavior.
- Confirm `Expand all` does not appear for parse-error rows (no `parsed` body).

## Acceptance Criteria
- Users can fully open the body tree with a single click after loading the body.
- The control is per-card and does not affect other cards.
- Performance remains acceptable for typical bodies; any heavy work is user-initiated.

## Assumptions
- Default node collapse threshold remains `depth > 3`.
- Scope is Body view only (no changes to the compact Kafka preview `{ key, headers }`).
