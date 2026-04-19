ALTER TABLE jsonl_entry
    ADD COLUMN IF NOT EXISTS search_text TEXT;

CREATE INDEX IF NOT EXISTS jsonl_entry_search_text_fts_idx
    ON jsonl_entry USING GIN (to_tsvector('simple', search_text));

DROP INDEX IF EXISTS jsonl_entry_parsed_fts_idx;

UPDATE ingest_state
SET indexed_revision = 0,
    ingest_status = 'building'
WHERE source_revision > 0;
