package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.repo.JsonlEntryFieldIndex;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonFieldIndexExtractorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JsonFieldIndexExtractor extractor = new JsonFieldIndexExtractor();

  @Test
  void extractsOnlyLeafScalarsUnderTopLevelHeaderAndHeaders() throws Exception {
    JsonNode parsed = objectMapper.readTree("""
        {
          "header": {
            "traceId": "abc-123",
            "nested": {
              "status": 200,
              "ok": true,
              "note": "",
              "missingValue": null,
              "items": [],
              "meta": {}
            },
            "items": [],
            "meta": {}
          },
          "headers": {
            "eventTime": "2026-04-06T13:23:58Z",
            "meta": {
              "code": 503,
              "cached": false
            },
            "empty": ""
          },
          "status": "outside-root",
          "body": {
            "headers": {
              "ignored": "value"
            }
          }
        }
        """);

    List<JsonlEntryFieldIndex> rows = extractor.extract("source-a", 42L, parsed);

    assertEquals(9, rows.size());
    assertTrue(rows.stream().allMatch(row -> row.getEntryId() == 42L));
    assertTrue(rows.stream().allMatch(row -> "source-a".equals(row.getFilePath())));

    JsonlEntryFieldIndex traceId = findByPath(rows, "header.traceId");
    assertEquals("traceId", traceId.getFieldKey());
    assertEquals("abc-123", traceId.getValueText());
    assertEquals("string", traceId.getValueType());
    assertFalse(traceId.isNull());
    assertFalse(traceId.isEmpty());

    JsonlEntryFieldIndex nestedStatus = findByPath(rows, "header.nested.status");
    assertEquals("status", nestedStatus.getFieldKey());
    assertEquals("200", nestedStatus.getValueText());
    assertEquals("number", nestedStatus.getValueType());

    JsonlEntryFieldIndex nestedNull = findByPath(rows, "header.nested.missingValue");
    assertNull(nestedNull.getValueText());
    assertEquals("null", nestedNull.getValueType());
    assertTrue(nestedNull.isNull());
    assertFalse(nestedNull.isEmpty());

    JsonlEntryFieldIndex nestedEmpty = findByPath(rows, "header.nested.note");
    assertEquals("", nestedEmpty.getValueText());
    assertEquals("string", nestedEmpty.getValueType());
    assertFalse(nestedEmpty.isNull());
    assertTrue(nestedEmpty.isEmpty());

    JsonlEntryFieldIndex eventTime = findByPath(rows, "headers.eventTime");
    assertEquals("eventTime", eventTime.getFieldKey());
    assertEquals("2026-04-06T13:23:58Z", eventTime.getValueText());

    assertTrue(rows.stream().noneMatch(row -> "header.items".equals(row.getFieldPath())));
    assertTrue(rows.stream().noneMatch(row -> "header.meta".equals(row.getFieldPath())));
    assertTrue(rows.stream().noneMatch(row -> "header.nested.items".equals(row.getFieldPath())));
    assertTrue(rows.stream().noneMatch(row -> "header.nested.meta".equals(row.getFieldPath())));
    assertTrue(rows.stream().noneMatch(row -> "status".equals(row.getFieldPath())));
    assertTrue(rows.stream().noneMatch(row -> "body.headers.ignored".equals(row.getFieldPath())));
  }

  @Test
  void ignoresMissingOrNonObjectHeaderRoots() throws Exception {
    JsonNode parsed = objectMapper.readTree("""
        {
          "header": "not-an-object",
          "headers": [],
          "other": {
            "status": 200
          }
        }
        """);

    assertTrue(extractor.extract("source-a", 5L, parsed).isEmpty());
    assertTrue(extractor.extract("source-a", 5L, null).isEmpty());
  }

  private JsonlEntryFieldIndex findByPath(List<JsonlEntryFieldIndex> rows, String fieldPath) {
    return rows.stream()
        .filter(row -> fieldPath.equals(row.getFieldPath()))
        .findFirst()
        .orElseThrow();
  }
}
