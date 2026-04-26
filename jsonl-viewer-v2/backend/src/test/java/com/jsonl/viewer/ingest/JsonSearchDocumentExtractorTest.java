package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonSearchDocumentExtractorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JsonSearchDocumentExtractor extractor = new JsonSearchDocumentExtractor();

  @Test
  void extractBuildsRecursiveSearchDocumentFromPathsAndScalars() throws Exception {
    JsonNode parsed = objectMapper.readTree("""
        {
          "headers": {
            "status": "500",
            "traceId": "abc"
          },
          "message": "worker failed to connect to gateway",
          "flags": [true, false],
          "numbers": [500, 404],
          "nested": {
            "items": [
              {"kind": "timeout"},
              {"kind": "retry"}
            ]
          }
        }
        """);

    String searchDocument = extractor.extract(parsed);
    List<String> tokens = Arrays.asList(searchDocument.split("\\s+"));

    assertTrue(tokens.contains("headers"));
    assertTrue(tokens.contains("status"));
    assertTrue(tokens.contains("500"));
    assertTrue(tokens.contains("traceId"));
    assertTrue(tokens.contains("trace"));
    assertTrue(tokens.contains("Id"));
    assertTrue(tokens.contains("abc"));
    assertTrue(tokens.contains("message"));
    assertTrue(tokens.contains("worker"));
    assertTrue(tokens.contains("failed"));
    assertTrue(tokens.contains("connect"));
    assertTrue(tokens.contains("gateway"));
    assertTrue(tokens.contains("flags"));
    assertTrue(tokens.contains("true"));
    assertTrue(tokens.contains("false"));
    assertTrue(tokens.contains("numbers"));
    assertTrue(tokens.contains("404"));
    assertTrue(tokens.contains("nested"));
    assertTrue(tokens.contains("items"));
    assertTrue(tokens.contains("kind"));
    assertTrue(tokens.contains("timeout"));
    assertTrue(tokens.contains("retry"));
  }

  @Test
  void extractSkipsNullAndEmptyAndDeduplicatesTokensPerRow() throws Exception {
    JsonNode parsed = objectMapper.readTree("""
        {
          "headers": {
            "status": "500",
            "statusCopy": "500",
            "empty": "",
            "missing": null
          },
          "values": ["500", "500", "status", "status", ""]
        }
        """);

    String searchDocument = extractor.extract(parsed);
    List<String> tokens = Arrays.asList(searchDocument.split("\\s+"));

    long count500 = tokens.stream().filter("500"::equals).count();
    long countStatus = tokens.stream().filter("status"::equals).count();
    assertEquals(1L, count500);
    assertEquals(1L, countStatus);
    assertTrue(tokens.contains("statusCopy"));
    assertTrue(tokens.contains("status"));
  }

  @Test
  void extractReturnsNullWhenNoSearchableScalarsExist() throws Exception {
    JsonNode parsed = objectMapper.readTree("""
        {
          "headers": {
            "empty": "",
            "missing": null
          },
          "arr": [null, ""]
        }
        """);

    assertNull(extractor.extract(parsed));
    assertNull(extractor.extract(null));
  }
}
