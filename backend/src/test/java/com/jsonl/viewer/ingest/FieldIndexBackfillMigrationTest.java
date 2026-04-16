package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class FieldIndexBackfillMigrationTest {

  @Test
  void migrationDropsTimestampFieldIndexAndMarksSourcesForRebuild() throws Exception {
    InputStream stream = Objects.requireNonNull(
        getClass().getResourceAsStream("/db/migration/V4__remove_timestamp_field_index.sql")
    );

    String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

    assertTrue(sql.contains("DROP INDEX IF EXISTS jsonl_entry_field_index_path_ts_id_idx"));
    assertTrue(sql.contains("DROP COLUMN IF EXISTS value_ts"));
    assertTrue(sql.contains("UPDATE ingest_state"));
    assertTrue(sql.contains("indexed_revision = 0"));
    assertTrue(sql.contains("ingest_status = 'building'"));
    assertTrue(sql.contains("WHERE source_revision > 0"));
  }
}
