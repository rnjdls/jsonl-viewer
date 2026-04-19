package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class DropFieldIndexTableMigrationTest {

  @Test
  void migrationDropsFieldIndexTableAndIndexes() throws Exception {
    InputStream stream = Objects.requireNonNull(
        getClass().getResourceAsStream("/db/migration/V7__drop_field_index_table.sql")
    );

    String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

    assertTrue(sql.contains("DROP INDEX IF EXISTS jsonl_entry_field_index_entry_id_idx"));
    assertTrue(sql.contains("DROP INDEX IF EXISTS jsonl_entry_field_index_value_trgm_idx"));
    assertTrue(sql.contains("DROP INDEX IF EXISTS jsonl_entry_field_index_empty_idx"));
    assertTrue(sql.contains("DROP INDEX IF EXISTS jsonl_entry_field_index_null_idx"));
    assertTrue(sql.contains("DROP INDEX IF EXISTS jsonl_entry_field_index_key_idx"));
    assertTrue(sql.contains("DROP TABLE IF EXISTS jsonl_entry_field_index"));
  }
}
