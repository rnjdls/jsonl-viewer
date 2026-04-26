package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonlEntryParserTimestampTest {
  private JsonlEntryParser parser;

  @BeforeEach
  void setUp() {
    parser = new JsonlEntryParser(new ObjectMapper());
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
  void doesNotExtractTimestampFromParsedPayload() {
    JsonlEntryParseResult parsed = parser.parse("{\"timestamp\":1712812800}");

    assertNull(parsed.ts());
  }
}
