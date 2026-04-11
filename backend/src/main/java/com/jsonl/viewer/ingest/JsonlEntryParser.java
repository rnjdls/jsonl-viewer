package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.config.AppProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

@Component
public class JsonlEntryParser {
  private final ObjectMapper objectMapper;
  private final AppProperties properties;

  public JsonlEntryParser(ObjectMapper objectMapper, AppProperties properties) {
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  public JsonlEntryParseResult parse(String rawLine) {
    try {
      JsonNode node = objectMapper.readTree(rawLine);
      Base64HeadersDecoder.decodeRootHeaders(node);
      JsonUnicodeSanitizer.sanitize(node);
      Instant ts = extractTimestamp(node);
      return new JsonlEntryParseResult(node, null, ts);
    } catch (Exception e) {
      return new JsonlEntryParseResult(null, e.getMessage(), null);
    }
  }

  private Instant extractTimestamp(JsonNode node) {
    if (node == null || !node.isObject()) {
      return null;
    }

    String field = properties.getJsonlTimestampField();
    if (field == null || field.isBlank()) {
      return null;
    }

    JsonNode cursor = node;
    for (String part : field.split("\\.")) {
      if (cursor == null) {
        return null;
      }
      cursor = cursor.get(part);
    }
    if (cursor == null || cursor.isNull()) {
      return null;
    }

    if (cursor.isNumber()) {
      long value = cursor.asLong();
      if (value > 1_000_000_000_000L) {
        return Instant.ofEpochMilli(value);
      }
      return Instant.ofEpochSecond(value);
    }

    if (cursor.isTextual()) {
      String text = cursor.asText();
      try {
        return Instant.parse(text);
      } catch (DateTimeParseException ignored) {
        try {
          return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored2) {
          return null;
        }
      }
    }

    return null;
  }
}
