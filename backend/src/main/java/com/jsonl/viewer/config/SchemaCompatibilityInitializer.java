package com.jsonl.viewer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaCompatibilityInitializer {
  private static final Logger log = LoggerFactory.getLogger(SchemaCompatibilityInitializer.class);

  private final JdbcTemplate jdbcTemplate;

  public SchemaCompatibilityInitializer(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ensureTextColumnsForJsonlEntry() {
    try {
      jdbcTemplate.execute(
          "ALTER TABLE IF EXISTS jsonl_entry " +
          "ALTER COLUMN file_path TYPE text, " +
          "ALTER COLUMN raw_line TYPE text, " +
          "ALTER COLUMN parse_error TYPE text"
      );
      log.info("Ensured jsonl_entry text columns for file_path/raw_line/parse_error");
    } catch (Exception ex) {
      log.warn("Failed to apply jsonl_entry schema compatibility updates: {}", ex.getMessage());
    }
  }
}
