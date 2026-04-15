package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.jsonl.viewer.repo.JsonlEntryFieldIndex;
import com.jsonl.viewer.service.TimestampParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonFieldIndexExtractor {
  public List<JsonlEntryFieldIndex> extract(String filePath, long entryId, JsonNode parsed) {
    if (parsed == null || parsed.isNull()) {
      return List.of();
    }

    List<JsonlEntryFieldIndex> rows = new ArrayList<>();
    walk(parsed, filePath, entryId, "", rows);
    return rows;
  }

  private void walk(
      JsonNode node,
      String filePath,
      long entryId,
      String parentPath,
      List<JsonlEntryFieldIndex> rows
  ) {
    if (node == null || node.isNull()) {
      return;
    }

    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        JsonNode value = field.getValue();
        String fieldPath = parentPath.isEmpty() ? field.getKey() : parentPath + "." + field.getKey();

        rows.add(new JsonlEntryFieldIndex(
            entryId,
            filePath,
            field.getKey(),
            fieldPath,
            toValueText(value),
            toValueTs(value),
            valueType(value),
            value != null && value.isNull(),
            isEmptyValue(value)
        ));

        walk(value, filePath, entryId, fieldPath, rows);
      }
      return;
    }

    if (node.isArray()) {
      for (JsonNode child : node) {
        walk(child, filePath, entryId, parentPath, rows);
      }
    }
  }

  private String toValueText(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isTextual()) {
      return value.asText();
    }
    if (value.isNumber() || value.isBoolean()) {
      return value.asText();
    }
    return value.toString();
  }

  private String valueType(JsonNode value) {
    if (value == null) {
      return "unknown";
    }
    if (value.isNull()) {
      return "null";
    }
    if (value.isTextual()) {
      return "string";
    }
    if (value.isBoolean()) {
      return "boolean";
    }
    if (value.isNumber()) {
      return "number";
    }
    if (value.isArray()) {
      return "array";
    }
    if (value.isObject()) {
      return "object";
    }
    return "unknown";
  }

  private boolean isEmptyValue(JsonNode value) {
    if (value == null || value.isNull()) {
      return false;
    }
    if (value.isTextual()) {
      return value.asText().isEmpty();
    }
    if (value.isArray() || value.isObject()) {
      return value.isEmpty();
    }
    return false;
  }

  private Instant toValueTs(JsonNode value) {
    return TimestampParser.parseJsonScalar(value);
  }
}
