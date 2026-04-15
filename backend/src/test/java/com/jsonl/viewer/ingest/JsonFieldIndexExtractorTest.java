package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.repo.JsonlEntryFieldIndex;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonFieldIndexExtractorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JsonFieldIndexExtractor extractor = new JsonFieldIndexExtractor();

  @Test
  void extractsNestedFieldsAndValueMetadata() throws Exception {
    JsonNode parsed = objectMapper.readTree("""
        {
          "status": 200,
          "timestamp": "2026-04-06T13:23:58Z",
          "headers": {
            "eventTime": "2026-04-06 13:23:58",
            "status": null,
            "items": [],
            "obj": {}
          },
          "array": [
            {"status": "ok"},
            {"flag": true, "ts": 1712560000}
          ],
          "emptyString": ""
        }
        """);

    List<JsonlEntryFieldIndex> rows = extractor.extract("source-a", 42L, parsed);

    assertEquals(12, rows.size());
    assertTrue(rows.stream().allMatch(row -> row.getEntryId() == 42L));
    assertTrue(rows.stream().allMatch(row -> "source-a".equals(row.getFilePath())));

    long statusCount = rows.stream().filter(row -> "status".equals(row.getFieldKey())).count();
    assertEquals(3, statusCount);

    JsonlEntryFieldIndex numberStatus = rows.stream()
        .filter(row -> "status".equals(row.getFieldKey()) && "status".equals(row.getFieldPath()))
        .findFirst()
        .orElseThrow();
    assertEquals("200", numberStatus.getValueText());
    assertTrue(!numberStatus.isNull());
    assertTrue(!numberStatus.isEmpty());
    assertEquals(Instant.ofEpochSecond(200L), numberStatus.getValueTs());

    JsonlEntryFieldIndex nullStatus = rows.stream()
        .filter(row -> "headers.status".equals(row.getFieldPath()) && row.isNull())
        .findFirst()
        .orElseThrow();
    assertEquals("null", nullStatus.getValueType());

    JsonlEntryFieldIndex emptyString = rows.stream()
        .filter(row -> "emptyString".equals(row.getFieldPath()))
        .findFirst()
        .orElseThrow();
    assertEquals("string", emptyString.getValueType());
    assertTrue(emptyString.isEmpty());

    JsonlEntryFieldIndex emptyArray = rows.stream()
        .filter(row -> "headers.items".equals(row.getFieldPath()))
        .findFirst()
        .orElseThrow();
    assertEquals("array", emptyArray.getValueType());
    assertTrue(emptyArray.isEmpty());

    JsonlEntryFieldIndex booleanFlag = rows.stream()
        .filter(row -> "array.flag".equals(row.getFieldPath()))
        .findFirst()
        .orElseThrow();
    assertEquals("boolean", booleanFlag.getValueType());
    assertEquals("true", booleanFlag.getValueText());
    assertNull(booleanFlag.getValueTs());

    JsonlEntryFieldIndex isoTimestamp = rows.stream()
        .filter(row -> "timestamp".equals(row.getFieldPath()))
        .findFirst()
        .orElseThrow();
    assertEquals(Instant.parse("2026-04-06T13:23:58Z"), isoTimestamp.getValueTs());

    JsonlEntryFieldIndex naiveTimestamp = rows.stream()
        .filter(row -> "headers.eventTime".equals(row.getFieldPath()))
        .findFirst()
        .orElseThrow();
    assertEquals(Instant.parse("2026-04-06T13:23:58Z"), naiveTimestamp.getValueTs());

    JsonlEntryFieldIndex epochTimestamp = rows.stream()
        .filter(row -> "array.ts".equals(row.getFieldPath()))
        .findFirst()
        .orElseThrow();
    assertEquals(Instant.ofEpochSecond(1712560000L), epochTimestamp.getValueTs());
  }
}
