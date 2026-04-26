package com.jsonl.viewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PreviewCursorCodec {
  private static final String SORT_DIR_ASC = "asc";
  private static final String SORT_DIR_DESC = "desc";

  private final ObjectMapper objectMapper;

  public PreviewCursorCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String encode(PreviewCursor cursor) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("sortDir", cursor.sortDir());
      payload.put("lineNo", cursor.lineNo());
      payload.put("id", cursor.id());
      byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to encode preview cursor", e);
    }
  }

  public PreviewCursor decode(
      String encodedCursor,
      String expectedSortDir
  ) {
    if (encodedCursor == null || encodedCursor.isBlank()) {
      return null;
    }
    try {
      byte[] payloadBytes = Base64.getUrlDecoder().decode(encodedCursor);
      JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
      String sortDir = normalizeSortDir(textValue(payload, "sortDir"));

      if (!sortDir.equals(expectedSortDir)) {
        throw new IllegalArgumentException("Cursor sortDir does not match request");
      }

      long id = requiredLong(payload, "id");
      long lineNo = requiredLong(payload, "lineNo");
      return new PreviewCursor(sortDir, lineNo, id);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid preview cursor");
    }
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
}
