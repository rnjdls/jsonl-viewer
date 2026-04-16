UPDATE ingest_state
SET indexed_revision = 0,
    ingest_status = 'building'
WHERE source_revision > 0;
