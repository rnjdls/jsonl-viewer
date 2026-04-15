package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonlEntryParser {
  private final ObjectMapper objectMapper;

  public JsonlEntryParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public JsonlEntryParseResult parse(String rawLine) {
    try {
      JsonNode node = objectMapper.readTree(rawLine);
      Base64HeadersDecoder.decodeRootHeaders(node);
      JsonUnicodeSanitizer.sanitize(node);
      return new JsonlEntryParseResult(node, null, null);
    } catch (Exception e) {
      return new JsonlEntryParseResult(null, e.getMessage(), null);
    }
  }
}
