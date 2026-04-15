CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS jsonl_entry (
    id BIGSERIAL PRIMARY KEY,
    file_path TEXT NOT NULL,
    line_no BIGINT NOT NULL,
    raw_line TEXT NOT NULL,
    parsed JSONB,
    parse_error TEXT,
    ts TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE IF EXISTS jsonl_entry
    ALTER COLUMN file_path TYPE TEXT,
    ALTER COLUMN raw_line TYPE TEXT,
    ALTER COLUMN parse_error TYPE TEXT;

CREATE INDEX IF NOT EXISTS jsonl_entry_file_id_idx ON jsonl_entry (file_path, id);
CREATE INDEX IF NOT EXISTS jsonl_entry_file_line_no_id_idx ON jsonl_entry (file_path, line_no, id);
CREATE INDEX IF NOT EXISTS jsonl_entry_file_ts_idx ON jsonl_entry (file_path, ts);
CREATE INDEX IF NOT EXISTS jsonl_entry_parsed_fts_idx ON jsonl_entry USING GIN (to_tsvector('simple', parsed::text));

CREATE TABLE IF NOT EXISTS ingest_state (
    file_path TEXT PRIMARY KEY,
    byte_offset BIGINT NOT NULL DEFAULT 0,
    line_no BIGINT NOT NULL DEFAULT 0,
    last_ingested_at TIMESTAMPTZ,
    total_count BIGINT NOT NULL DEFAULT 0,
    parsed_count BIGINT NOT NULL DEFAULT 0,
    error_count BIGINT NOT NULL DEFAULT 0,
    source_revision BIGINT NOT NULL DEFAULT 0,
    indexed_revision BIGINT NOT NULL DEFAULT 0,
    ingest_status TEXT NOT NULL DEFAULT 'ready'
);

ALTER TABLE IF EXISTS ingest_state
    ADD COLUMN IF NOT EXISTS total_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS parsed_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS error_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS source_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS indexed_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ingest_status TEXT NOT NULL DEFAULT 'ready';

UPDATE ingest_state
SET total_count = counts.total_count,
    parsed_count = counts.parsed_count,
    error_count = counts.error_count
FROM (
    SELECT
        file_path,
        COUNT(*) AS total_count,
        COUNT(*) FILTER (WHERE parsed IS NOT NULL) AS parsed_count,
        COUNT(*) FILTER (WHERE parse_error IS NOT NULL) AS error_count
    FROM jsonl_entry
    GROUP BY file_path
) AS counts
WHERE ingest_state.file_path = counts.file_path;

INSERT INTO ingest_state (
    file_path,
    byte_offset,
    line_no,
    last_ingested_at,
    total_count,
    parsed_count,
    error_count,
    source_revision,
    indexed_revision,
    ingest_status
)
SELECT
    counts.file_path,
    0,
    COALESCE(lines.max_line_no, 0),
    lines.max_created_at,
    counts.total_count,
    counts.parsed_count,
    counts.error_count,
    CASE WHEN counts.total_count > 0 THEN 1 ELSE 0 END,
    0,
    CASE WHEN counts.total_count > 0 THEN 'building' ELSE 'ready' END
FROM (
    SELECT
        file_path,
        COUNT(*) AS total_count,
        COUNT(*) FILTER (WHERE parsed IS NOT NULL) AS parsed_count,
        COUNT(*) FILTER (WHERE parse_error IS NOT NULL) AS error_count
    FROM jsonl_entry
    GROUP BY file_path
) AS counts
LEFT JOIN (
    SELECT
        file_path,
        MAX(line_no) AS max_line_no,
        MAX(created_at) AS max_created_at
    FROM jsonl_entry
    GROUP BY file_path
) AS lines ON lines.file_path = counts.file_path
ON CONFLICT (file_path) DO NOTHING;

UPDATE ingest_state
SET source_revision = CASE
        WHEN total_count > 0 AND source_revision = 0 THEN 1
        ELSE source_revision
    END,
    indexed_revision = LEAST(indexed_revision, CASE
        WHEN total_count > 0 AND source_revision = 0 THEN 1
        ELSE source_revision
    END);

UPDATE ingest_state
SET ingest_status = CASE
        WHEN indexed_revision < source_revision THEN 'building'
        ELSE 'ready'
    END;

CREATE TABLE IF NOT EXISTS jsonl_entry_field_index (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL REFERENCES jsonl_entry(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    field_key TEXT NOT NULL,
    value_text TEXT,
    value_type TEXT NOT NULL,
    is_null BOOLEAN NOT NULL DEFAULT FALSE,
    is_empty BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS jsonl_entry_field_index_key_idx
    ON jsonl_entry_field_index (file_path, field_key, entry_id);
CREATE INDEX IF NOT EXISTS jsonl_entry_field_index_null_idx
    ON jsonl_entry_field_index (file_path, field_key, is_null, entry_id);
CREATE INDEX IF NOT EXISTS jsonl_entry_field_index_empty_idx
    ON jsonl_entry_field_index (file_path, field_key, is_empty, entry_id);
CREATE INDEX IF NOT EXISTS jsonl_entry_field_index_value_trgm_idx
    ON jsonl_entry_field_index USING GIN (value_text gin_trgm_ops);

CREATE TABLE IF NOT EXISTS filter_count_cache (
    file_path TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    source_revision BIGINT NOT NULL,
    computed_revision BIGINT,
    match_count BIGINT,
    status TEXT NOT NULL DEFAULT 'pending',
    last_computed_at TIMESTAMPTZ,
    request_payload JSONB,
    PRIMARY KEY (file_path, request_hash)
);

CREATE INDEX IF NOT EXISTS filter_count_cache_source_revision_idx
    ON filter_count_cache (file_path, source_revision);
