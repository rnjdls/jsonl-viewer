#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

repo_root="$script_dir"
while [[ "$repo_root" != "/" ]] && [[ ! -f "$repo_root/AGENTS.md" ]] && [[ ! -f "$repo_root/docker-compose.yml" ]]; do
  repo_root="$(dirname "$repo_root")"
done

if [[ "$repo_root" == "/" ]]; then
  echo "Error: could not detect repo root (expected AGENTS.md or docker-compose.yml in an ancestor directory)." >&2
  exit 1
fi
repo_name="$(basename "$repo_root")"

out_zip="$repo_root/${repo_name}.zip"

stage_dir="$(mktemp -d)"
cleanup() {
  rm -rf "$stage_dir"
}
trap cleanup EXIT

rsync -a \
  --delete \
  --exclude ".git/" \
  --exclude ".github/" \
  --exclude ".gitignore" \
  --exclude ".gitattributes" \
  --exclude ".gitmodules" \
  --exclude "mock-generator/" \
  --exclude "docs/" \
  --exclude "skills/" \
  --exclude "docker-compose.generated.yml" \
  --exclude "AGENT.md" \
  --exclude "AGENTS.md" \
  --exclude "SKILLS.md" \
  --exclude ".DS_Store" \
  --exclude "$(basename "$out_zip")" \
  "$repo_root/" "$stage_dir/$repo_name/"

(
  cd "$stage_dir"
  rm -f "$out_zip"
  zip -rq "$out_zip" "$repo_name"
)

echo "Wrote: $out_zip"
