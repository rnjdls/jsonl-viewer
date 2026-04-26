package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class SearchTextBackfillMigrationTest {

  @Test
  void migrationAddsSearchTextIndexDropsParsedFtsAndMarksSourcesForRebuild() throws Exception {
    InputStream stream = Objects.requireNonNull(
        getClass().getResourceAsStream("/db/migration/V6__search_text_document_backfill.sql")
    );

    String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

    assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS search_text TEXT"));
    assertTrue(sql.contains("jsonl_entry_search_text_fts_idx"));
    assertTrue(sql.contains("to_tsvector('simple', search_text)"));
    assertTrue(sql.contains("DROP INDEX IF EXISTS jsonl_entry_parsed_fts_idx"));
    assertTrue(sql.contains("UPDATE ingest_state"));
    assertTrue(sql.contains("indexed_revision = 0"));
    assertTrue(sql.contains("ingest_status = 'building'"));
    assertTrue(sql.contains("WHERE source_revision > 0"));
  }
}
