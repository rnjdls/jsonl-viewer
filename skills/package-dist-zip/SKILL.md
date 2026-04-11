# `package-dist-zip`

Create a distribution zip of this repo **without**:

- `mock-generator/`
- `docs/`
- `skills/`
- `docker-compose.generated.yml`
- `AGENT.md`
- `SKILLS.md`
- Git-related files (e.g. `.git/`, `.gitignore`, `.gitattributes`, `.gitmodules`, `.github/`)

## Usage

From the repo root:

```bash
bash skills/package-dist-zip/scripts/package_dist_zip.sh
```

## Output

- Creates `./jsonl-viewer-v2.zip` in the repo root (overwrites if it already exists).
