# Skills

This repo includes a small set of reusable “skills” (agent workflows) as folders under `skills/`.

## `publish-squash-merge-main`

- **When to use**: You want to commit + push all current changes on your current feature branch, then squash-merge that branch into `main` and push `main`.
- **Skill file**: `skills/publish-squash-merge-main/SKILL.md`
- **Optional helper script**: `skills/publish-squash-merge-main/scripts/publish_squash_merge.sh`

## `package-dist-zip`

- **When to use**: You want a shareable zip of the repo that excludes `mock-generator/`, `docs/`, `skills/`, `docker-compose.generated.yml`, `AGENT.md`, `SKILLS.md`, and git-related files.
- **Skill file**: `skills/package-dist-zip/SKILL.md`
- **Helper script**: `skills/package-dist-zip/scripts/package_dist_zip.sh`
