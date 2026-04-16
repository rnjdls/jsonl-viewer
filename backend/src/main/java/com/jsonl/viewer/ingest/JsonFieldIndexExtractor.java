package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.jsonl.viewer.repo.JsonlEntryFieldIndex;
import com.jsonl.viewer.service.TimestampParser;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonFieldIndexExtractor {
  private static final Instant POSTGRES_TIMESTAMPTZ_MIN =
      LocalDateTime.of(-4712, 1, 1, 0, 0, 0).toInstant(ZoneOffset.UTC);
  private static final Instant POSTGRES_TIMESTAMPTZ_MAX =
      LocalDateTime.of(294276, 12, 31, 23, 59, 59, 999_999_000).toInstant(ZoneOffset.UTC);

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
            toValueTs(field.getKey(), value),
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

  private Instant toValueTs(String fieldKey, JsonNode value) {
    if (!isTimestampLikeField(fieldKey)) {
      return null;
    }
    Instant parsed;
    try {
      parsed = TimestampParser.parseJsonScalar(value);
    } catch (RuntimeException ignored) {
      return null;
    }
    if (parsed == null) {
      return null;
    }
    if (parsed.isBefore(POSTGRES_TIMESTAMPTZ_MIN) || parsed.isAfter(POSTGRES_TIMESTAMPTZ_MAX)) {
      return null;
    }
    return parsed;
  }

  private boolean isTimestampLikeField(String fieldKey) {
    if (fieldKey == null || fieldKey.isBlank()) {
      return false;
    }
    String normalized = fieldKey.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("timestamp")
        || normalized.equals("time")
        || normalized.equals("ts")
        || normalized.equals("date")) {
      return true;
    }
    if (normalized.endsWith("time")) {
      return true;
    }
    return fieldKey.trim().endsWith("At") || normalized.endsWith("_at");
  }
}
