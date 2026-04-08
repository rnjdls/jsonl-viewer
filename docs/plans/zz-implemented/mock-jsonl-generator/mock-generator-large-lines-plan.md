# Large JSONL Line Format in Mock Generator (Feature Branch)

## Summary
- Create branch `feature/mock-generator-large-lines` from current `main`, carrying current local uncommitted changes as-is.
- Keep existing top-level fields (`id`, `timestamp`, `user`, `action`, `details`, `headers`) for compatibility.
- Add a new top-level `payload` object with many nested string-heavy fields and enforce `100KB+` line size by default.
- Keep max-stress generation mode in compose (aggressive batch/rate) and make payload size configurable via env vars.

## Implementation Changes
1. Branch step:
- Run `git switch -c feature/mock-generator-large-lines` (no stash/cleanup first).

2. Generator config:
- Extend `GeneratorProperties` and `application.yml` with new properties/env vars:
- `GENERATOR_PAYLOAD_TARGET_BYTES` default `131072` (128KB).
- `GENERATOR_PAYLOAD_SECTION_COUNT` default aggressive value (for many fields).
- `GENERATOR_PAYLOAD_FIELDS_PER_SECTION` default aggressive value.
- `GENERATOR_PAYLOAD_FIELD_LENGTH` default aggressive value.
- Add validation so these values must be positive.

3. Line format update in `MockJsonlGeneratorService`:
- Keep current randomization logic for existing fields.
- Add `applyLargePayload(...)` to inject `payload` object containing:
- Multiple nested sections.
- Multiple large string fields per section.
- Structured metadata arrays/objects (not a single flat blob).
- Add padding logic so serialized line length reaches at least `payloadTargetBytes` (guarantees `100KB+` even if template/base fields vary).
- Update `StringBuilder` preallocation to reflect large per-line size to reduce reallocations.

4. Runtime config/docs:
- Keep/confirm aggressive compose settings (max stress) and add new payload env vars in `docker-compose.generated.yml`.
- Update README generator configuration section with new env vars and note that this mode causes very fast disk/database growth.

## Public Interface / Output Shape Changes
- Generated JSONL schema adds top-level `payload` object on every line.
- New generator env vars become part of the module's runtime interface:
- `GENERATOR_PAYLOAD_TARGET_BYTES`
- `GENERATOR_PAYLOAD_SECTION_COUNT`
- `GENERATOR_PAYLOAD_FIELDS_PER_SECTION`
- `GENERATOR_PAYLOAD_FIELD_LENGTH`

## Test Plan
1. Update `MockJsonlGeneratorServiceTest` to assert:
- Existing top-level compatibility fields are still present and updated correctly.
- `payload` exists and is an object with expected nested structure.
- Serialized line length is `>= payloadTargetBytes` (use smaller target in unit test for speed/determinism).

2. Run:
- `cd mock-generator && mvn test`

3. Manual validation (compose run):
- Start generated stack and confirm rapid growth of `data/generated.jsonl`.
- Validate line sizes with `awk` length check and confirm `100KB+` records are being appended.

## Assumptions and Defaults
- Work is done on `feature/mock-generator-large-lines`.
- Existing local changes (`docker-compose.generated.yml` modified, `jsonl-viewer.zip` untracked) are intentionally carried into the new branch.
- JSONL compatibility with existing viewer behavior is preserved by keeping current core fields and adding `payload` rather than replacing schema.
