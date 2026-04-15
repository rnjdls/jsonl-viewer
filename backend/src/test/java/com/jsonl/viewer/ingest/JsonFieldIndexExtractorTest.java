package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void extractsNestedFieldsAndValueMetadata() throws Exception {
    JsonNode parsed = objectMapper.readTree("""
        {
          "status": 200,
          "nested": {
            "status": null,
            "items": [],
            "obj": {}
          },
          "array": [
            {"status": "ok"},
            {"flag": true}
          ],
          "emptyString": ""
        }
        """);

    List<JsonlEntryFieldIndex> rows = extractor.extract("source-a", 42L, parsed);

    assertEquals(9, rows.size());
    assertTrue(rows.stream().allMatch(row -> row.getEntryId() == 42L));
    assertTrue(rows.stream().allMatch(row -> "source-a".equals(row.getFilePath())));

    long statusCount = rows.stream().filter(row -> "status".equals(row.getFieldKey())).count();
    assertEquals(3, statusCount);

    JsonlEntryFieldIndex numberStatus = rows.stream()
        .filter(row -> "status".equals(row.getFieldKey()) && "number".equals(row.getValueType()))
        .findFirst()
        .orElseThrow();
    assertEquals("200", numberStatus.getValueText());
    assertTrue(!numberStatus.isNull());
    assertTrue(!numberStatus.isEmpty());

    JsonlEntryFieldIndex nullStatus = rows.stream()
        .filter(row -> "status".equals(row.getFieldKey()) && row.isNull())
        .findFirst()
        .orElseThrow();
    assertEquals("null", nullStatus.getValueType());

    JsonlEntryFieldIndex emptyString = rows.stream()
        .filter(row -> "emptyString".equals(row.getFieldKey()))
        .findFirst()
        .orElseThrow();
    assertEquals("string", emptyString.getValueType());
    assertTrue(emptyString.isEmpty());

    JsonlEntryFieldIndex emptyArray = rows.stream()
        .filter(row -> "items".equals(row.getFieldKey()))
        .findFirst()
        .orElseThrow();
    assertEquals("array", emptyArray.getValueType());
    assertTrue(emptyArray.isEmpty());

    JsonlEntryFieldIndex booleanFlag = rows.stream()
        .filter(row -> "flag".equals(row.getFieldKey()))
        .findFirst()
        .orElseThrow();
    assertEquals("boolean", booleanFlag.getValueType());
    assertEquals("true", booleanFlag.getValueText());
  }
}
