# Skills

This repo includes a small set of reusable agent workflows as folders under `docs/skills/`.

## `implement-plan-workflow`

- **When to use**: You are implementing an approved plan, feature, fix, chore, or docs task and need the repo's required branching, validation, smoke-test, and plan archival workflow.
- **Skill file**: `docs/skills/implement-plan-workflow/SKILL.md`

## `change-filters-safely`

- **When to use**: You are adding or changing filter/search behavior and need to keep backend filter logic, frontend payload mapping, and the filter UI in sync.
- **Skill file**: `docs/skills/change-filters-safely/SKILL.md`

## `publish-squash-merge-main`

- **When to use**: You want to commit + push all current changes on your current feature branch, then squash-merge that branch into `main` and push `main`.
- **Skill file**: `docs/skills/publish-squash-merge-main/SKILL.md`
- **Optional helper script**: `docs/skills/publish-squash-merge-main/scripts/publish_squash_merge.sh`

## `package-dist-zip`

- **When to use**: You want a shareable zip of the repo that excludes `mock-generator/`, `docs/`, `docker-compose.generated.yml`, `backend/target/`, `frontend/coverage/`, `frontend/dist/`, `frontend/node_modules/`, `AGENTS.md`, `SKILLS.md`, and git-related files.
- **Skill file**: `docs/skills/package-dist-zip/SKILL.md`
- **Helper script**: `docs/skills/package-dist-zip/scripts/package_dist_zip.sh`
