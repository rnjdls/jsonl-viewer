ALTER TABLE jsonl_entry_field_index
    ADD COLUMN IF NOT EXISTS field_path TEXT;

ALTER TABLE jsonl_entry_field_index
    ADD COLUMN IF NOT EXISTS value_ts TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS jsonl_entry_field_index_path_ts_id_idx
    ON jsonl_entry_field_index (file_path, field_path, value_ts, entry_id);

UPDATE ingest_state
SET indexed_revision = 0,
    ingest_status = 'building'
WHERE source_revision > 0;
