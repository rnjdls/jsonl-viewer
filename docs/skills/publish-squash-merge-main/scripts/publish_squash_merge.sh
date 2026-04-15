#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
publish_squash_merge.sh

Commits (if needed) and pushes the current branch, then squash-merges it into main and pushes main.

Usage:
  publish_squash_merge.sh --branch-message "..." --main-message "..."

Options:
  --branch-message  Commit message for the feature branch (used only if working tree is dirty)
  --main-message    Commit message for the squashed commit on main (required)
  --remote          Remote name (default: origin)
  --main-branch     Main branch name (default: main)
  --no-return       Do not switch back to the original branch at the end
EOF
}

branch_message=""
main_message=""
remote="origin"
main_branch="main"
return_to_branch="true"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --branch-message)
      branch_message="${2:-}"
      shift 2
      ;;
    --main-message)
      main_message="${2:-}"
      shift 2
      ;;
    --remote)
      remote="${2:-origin}"
      shift 2
      ;;
    --main-branch)
      main_branch="${2:-main}"
      shift 2
      ;;
    --no-return)
      return_to_branch="false"
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Not inside a git repo." >&2
  exit 1
fi

current_branch="$(git branch --show-current)"
if [[ -z "$current_branch" ]]; then
  echo "Could not determine current branch (detached HEAD?)." >&2
  exit 1
fi
if [[ "$current_branch" == "$main_branch" ]]; then
  echo "Refusing to run on '$main_branch'. Checkout a feature branch first." >&2
  exit 1
fi
if [[ -z "$main_message" ]]; then
  echo "--main-message is required." >&2
  exit 2
fi

dirty="false"
if ! git diff --quiet || ! git diff --cached --quiet; then
  dirty="true"
fi

if [[ "$dirty" == "true" ]]; then
  if [[ -z "$branch_message" ]]; then
    branch_message="Update ${current_branch}"
  fi
  git add -A
  git commit -m "$branch_message"
fi

if git rev-parse --abbrev-ref --symbolic-full-name '@{u}' >/dev/null 2>&1; then
  git push "$remote" "$current_branch"
else
  git push -u "$remote" "$current_branch"
fi

git fetch "$remote"

git checkout "$main_branch"
git pull --ff-only "$remote" "$main_branch"
git merge --squash "$current_branch"
git commit -m "$main_message"
git push "$remote" "$main_branch"

if [[ "$return_to_branch" == "true" ]]; then
  git checkout "$current_branch"
fi

