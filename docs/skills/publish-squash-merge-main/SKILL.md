---
name: publish-squash-merge-main
description: Commit and push all current branch changes, then squash-merge the branch into main and push main.
---

# Publish + Squash-Merge to `main`

Use this workflow when the user asks for: “commit and push all the changes, then squash and merge the current branch to main”.

## Guardrails

- Never run on `main` as the “current branch”.
- Prefer `git pull --ff-only` for `main` (do not create merge commits).
- Use `git merge --squash` to keep `main` history clean.
- Do not delete branches unless explicitly requested.

## Procedure (manual)

1. Confirm current branch + status:
   - `git status -sb`
   - `git branch --show-current` (must not be `main`)
2. Commit and push the current branch:
   - `git add -A`
   - `git commit -m "<message>"`
   - `git push -u origin <current-branch>`
3. Squash-merge into `main`:
   - `git fetch origin`
   - `git checkout main`
   - `git pull --ff-only origin main`
   - `git merge --squash <current-branch>`
   - `git commit -m "<squash message>"`
   - `git push origin main`

## Optional validation (repo-specific)

If the repo has tests/builds configured, run them before merging:

- Backend: `cd backend && mvn test`
- Frontend: `cd frontend && npm run build`

## Helper script

If you want a deterministic one-shot command, use:

- `bash docs/skills/publish-squash-merge-main/scripts/publish_squash_merge.sh --branch-message "..." --main-message "..."`
