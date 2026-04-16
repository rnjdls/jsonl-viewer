---
name: implement-plan-workflow
description: Implement an approved plan, feature, fix, chore, or docs task in this repo with the required branch creation, validation sequence, smoke test, and plan archival workflow.
---

# `implement-plan-workflow`

Use this workflow whenever you are about to make repo changes, not when you are only analyzing or planning.

## Branch choice

- `feature/<slug>` for user-facing features or behavior additions
- `fix/<slug>` for bugs, regressions, or broken behavior
- `chore/<slug>` for maintenance, tooling, or infrastructure work
- `docs/<slug>` for documentation and repo-guidance-only changes

## Procedure

1. Check `git status -sb` and `git branch --show-current`.
2. If you are on `main` or not already on a fresh branch for the task, create the category branch before any file edits.
3. Implement the change.
4. Run validation in this order:
   - `cd backend && mvn test` if `backend/` changed
   - `cd mock-generator && mvn test` if `mock-generator/` changed
   - add frontend tests when you introduce non-trivial frontend logic
   - boot smoke test the stack with `docker compose up --build`
   - use `docker compose -f docker-compose.yml -f docker-compose.generated.yml up --build` only when the change touches generated-data or mock-generator behavior
5. Confirm backend and UI both start.
6. If a test or boot step fails, inspect output or logs immediately, fix the root cause, and rerun the failing step plus the smoke test before calling the task done.
7. If the work was driven by a plan file under `docs/plans/`, move that plan into `docs/plans/zz-implemented/` only after verification passes.

## Failure handling

- For boot failures, start with `docker compose logs <service>` and fix the root cause before proceeding.

## Completion checks

- No repo-tracked changes are left out of the task branch unexpectedly.
- Validation matches the areas changed.
- Plan archival happens last, never before passing verification.
