package com.jsonl.viewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PreviewCursorCodec {
  private static final String SORT_BY_ID = "id";
  private static final String SORT_BY_LINE_NO = "lineNo";
  private static final String SORT_BY_TIMESTAMP = "timestamp";
  private static final String SORT_DIR_ASC = "asc";
  private static final String SORT_DIR_DESC = "desc";

  private final ObjectMapper objectMapper;

  public PreviewCursorCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String encode(PreviewCursor cursor) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("sortBy", cursor.sortBy());
      payload.put("sortDir", cursor.sortDir());
      switch (cursor.sortBy()) {
        case SORT_BY_ID:
          payload.put("id", cursor.id());
          break;
        case SORT_BY_LINE_NO:
          payload.put("lineNo", cursor.lineNo());
          payload.put("id", cursor.id());
          break;
        case SORT_BY_TIMESTAMP:
          payload.put("ts", cursor.ts() == null ? null : cursor.ts().toString());
          payload.put("id", cursor.id());
          break;
        default:
          throw new IllegalArgumentException("Unsupported sortBy in cursor: " + cursor.sortBy());
      }
      byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to encode preview cursor", e);
    }
  }

  public PreviewCursor decode(String encodedCursor, String expectedSortBy, String expectedSortDir) {
    if (encodedCursor == null || encodedCursor.isBlank()) {
      return null;
    }
    try {
      byte[] payloadBytes = Base64.getUrlDecoder().decode(encodedCursor);
      JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
      String sortBy = normalizeSortBy(textValue(payload, "sortBy"));
      String sortDir = normalizeSortDir(textValue(payload, "sortDir"));

      if (!sortBy.equals(expectedSortBy) || !sortDir.equals(expectedSortDir)) {
        throw new IllegalArgumentException("Cursor sortBy/sortDir do not match request");
      }

      long id = requiredLong(payload, "id");
      Long lineNo = null;
      Instant ts = null;

      if (SORT_BY_LINE_NO.equals(sortBy)) {
        lineNo = requiredLong(payload, "lineNo");
      } else if (SORT_BY_TIMESTAMP.equals(sortBy)) {
        if (!payload.has("ts")) {
          throw new IllegalArgumentException("Cursor field 'ts' is required for timestamp sort");
        }
        if (!payload.get("ts").isNull()) {
          ts = parseInstant(payload.get("ts").asText());
        }
      }

      return new PreviewCursor(sortBy, sortDir, id, lineNo, ts);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid preview cursor");
    }
  }

  private String normalizeSortBy(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Cursor field 'sortBy' is required");
    }
    if (SORT_BY_ID.equals(raw)) {
      return SORT_BY_ID;
    }
    if ("lineno".equalsIgnoreCase(raw)) {
      return SORT_BY_LINE_NO;
    }
    if (SORT_BY_TIMESTAMP.equalsIgnoreCase(raw)) {
      return SORT_BY_TIMESTAMP;
    }
    throw new IllegalArgumentException("Unsupported sortBy in cursor: " + raw);
  }

  private String normalizeSortDir(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Cursor field 'sortDir' is required");
    }
    if (SORT_DIR_ASC.equalsIgnoreCase(raw)) {
      return SORT_DIR_ASC;
    }
    if (SORT_DIR_DESC.equalsIgnoreCase(raw)) {
      return SORT_DIR_DESC;
    }
    throw new IllegalArgumentException("Unsupported sortDir in cursor: " + raw);
  }

  private String textValue(JsonNode node, String fieldName) {
    JsonNode value = node == null ? null : node.get(fieldName);
    if (value == null || value.isNull()) {
      return null;
    }
    return value.asText();
  }

  private long requiredLong(JsonNode payload, String fieldName) {
    JsonNode value = payload.get(fieldName);
    if (value == null || value.isNull() || !value.isNumber()) {
      throw new IllegalArgumentException("Cursor field '" + fieldName + "' must be numeric");
    }
    return value.asLong();
  }

  private Instant parseInstant(String rawValue) {
    try {
      return Instant.parse(rawValue);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Cursor field 'ts' is not a valid RFC3339 timestamp");
    }
  }
}
