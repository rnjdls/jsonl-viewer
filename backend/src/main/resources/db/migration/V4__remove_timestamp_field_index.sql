DROP INDEX IF EXISTS jsonl_entry_field_index_path_ts_id_idx;

ALTER TABLE jsonl_entry_field_index
    DROP COLUMN IF EXISTS value_ts;

UPDATE ingest_state
SET indexed_revision = 0,
    ingest_status = 'building'
WHERE source_revision > 0;
