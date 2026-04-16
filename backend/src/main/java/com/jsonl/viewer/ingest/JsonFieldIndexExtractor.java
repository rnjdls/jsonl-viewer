package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.jsonl.viewer.repo.JsonlEntryFieldIndex;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonFieldIndexExtractor {
  private static final List<String> INDEX_ROOTS = List.of("header", "headers");

  public List<JsonlEntryFieldIndex> extract(String filePath, long entryId, JsonNode parsed) {
    if (parsed == null || parsed.isNull() || !parsed.isObject()) {
      return List.of();
    }

    List<JsonlEntryFieldIndex> rows = new ArrayList<>();
    for (String rootKey : INDEX_ROOTS) {
      JsonNode rootNode = parsed.get(rootKey);
      if (rootNode != null && rootNode.isObject()) {
        walkRootObject(rootNode, rootKey, filePath, entryId, rows);
      }
    }
    return rows;
  }

  private void walkRootObject(
      JsonNode objectNode,
      String parentPath,
      String filePath,
      long entryId,
      List<JsonlEntryFieldIndex> rows
  ) {
    Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      JsonNode value = field.getValue();
      String fieldPath = parentPath + "." + field.getKey();

      if (value != null && value.isObject()) {
        walkRootObject(value, fieldPath, filePath, entryId, rows);
        continue;
      }
      if (value != null && value.isArray()) {
        continue;
      }
      if (value == null) {
        continue;
      }

      rows.add(new JsonlEntryFieldIndex(
          entryId,
          filePath,
          field.getKey(),
          fieldPath,
          toValueText(value),
          valueType(value),
          value.isNull(),
          isEmptyValue(value)
      ));
    }
  }

  private String toValueText(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isTextual() || value.isNumber() || value.isBoolean()) {
      return value.asText();
    }
    return null;
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
    return "unknown";
  }

  private boolean isEmptyValue(JsonNode value) {
    if (value == null || value.isNull()) {
      return false;
    }
    if (value.isTextual()) {
      return value.asText().isEmpty();
    }
    return false;
  }
}
