package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.config.AppProperties;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonlEntryParserTimestampTest {
  private JsonlEntryParser parser;

  @BeforeEach
  void setUp() {
    AppProperties properties = new AppProperties();
    properties.setJsonlTimestampField("timestamp");
    parser = new JsonlEntryParser(new ObjectMapper(), properties);
  }

  @Test
  void parsesValidJsonRecord() {
    JsonlEntryParseResult parsed = parser.parse("{\"key\":\"value\"}");

    assertNotNull(parsed.parsed());
    assertNull(parsed.parseError());
  }

  @Test
  void returnsParseErrorForInvalidJson() {
    JsonlEntryParseResult parsed = parser.parse("{\"key\":\"value\"");

    assertNull(parsed.parsed());
    assertNotNull(parsed.parseError());
    assertNull(parsed.ts());
  }

  @Test
  void extractsEpochSecondsTimestamp() {
    JsonlEntryParseResult parsed = parser.parse("{\"timestamp\":1712812800}");

    assertEquals(Instant.ofEpochSecond(1712812800L), parsed.ts());
  }

  @Test
  void extractsEpochMillisTimestamp() {
    JsonlEntryParseResult parsed = parser.parse("{\"timestamp\":1712812800123}");

    assertEquals(Instant.ofEpochMilli(1712812800123L), parsed.ts());
  }

  @Test
  void extractsIsoTimestamp() {
    JsonlEntryParseResult parsed = parser.parse("{\"timestamp\":\"2026-01-01T08:30:45Z\"}");

    assertEquals(Instant.parse("2026-01-01T08:30:45Z"), parsed.ts());
  }

  @Test
  void returnsNullTimestampForInvalidStringTimestamp() {
    JsonlEntryParseResult parsed = parser.parse("{\"timestamp\":\"not-a-date\"}");

    assertNull(parsed.ts());
  }
}
