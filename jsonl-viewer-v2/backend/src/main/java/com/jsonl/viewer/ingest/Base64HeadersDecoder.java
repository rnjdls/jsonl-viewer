package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

final class Base64HeadersDecoder {
  private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");
  private static final double PRINTABLE_THRESHOLD = 0.8;

  private Base64HeadersDecoder() {}

  static void decodeRootHeaders(JsonNode root) {
    if (!(root instanceof ObjectNode objectNode)) return;

    JsonNode headersNode = objectNode.get("headers");
    if (headersNode == null) return;

    if (headersNode.isTextual()) {
      String decoded = tryBase64Decode(headersNode.asText());
      if (decoded != null) {
        objectNode.put("headers", decoded);
      }
      return;
    }

    if (!headersNode.isObject()) return;

    ObjectNode headersObject = (ObjectNode) headersNode;
    Iterator<Map.Entry<String, JsonNode>> fields = headersObject.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      JsonNode value = field.getValue();
      if (!value.isTextual()) continue;

      String decoded = tryBase64Decode(value.asText());
      if (decoded != null) {
        headersObject.put(field.getKey(), decoded);
      }
    }
  }

  static String tryBase64Decode(String input) {
    if (input == null || input.length() < 4) return null;

    String clean = input.replaceAll("\\s", "");
    if (!BASE64_PATTERN.matcher(clean).matches()) return null;

    byte[] decodedBytes;
    try {
      decodedBytes = Base64.getDecoder().decode(clean);
    } catch (IllegalArgumentException ex) {
      return null;
    }

    if (decodedBytes.length == 0) return null;

    int printableCount = 0;
    for (byte b : decodedBytes) {
      int code = b & 0xFF;
      if (code >= 32 || code == 9 || code == 10 || code == 13) {
        printableCount++;
      }
    }

    if ((double) printableCount / decodedBytes.length < PRINTABLE_THRESHOLD) return null;

    return new String(decodedBytes, StandardCharsets.UTF_8);
  }
}
