package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Iterator;
import java.util.Map;

final class JsonUnicodeSanitizer {
  private static final char NUL = '\u0000';
  private static final char REPLACEMENT = '\uFFFD';

  private JsonUnicodeSanitizer() {}

  static void sanitize(JsonNode node) {
    if (node instanceof ObjectNode objectNode) {
      sanitizeObject(objectNode);
      return;
    }

    if (node instanceof ArrayNode arrayNode) {
      sanitizeArray(arrayNode);
    }
  }

  private static void sanitizeObject(ObjectNode objectNode) {
    Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode value = entry.getValue();

      if (value == null) continue;

      if (value.isTextual()) {
        String sanitized = sanitizeText(value.asText());
        if (!sanitized.equals(value.asText())) {
          objectNode.put(entry.getKey(), sanitized);
        }
        continue;
      }

      sanitize(value);
    }
  }

  private static void sanitizeArray(ArrayNode arrayNode) {
    for (int i = 0; i < arrayNode.size(); i++) {
      JsonNode value = arrayNode.get(i);
      if (value == null) continue;

      if (value.isTextual()) {
        String sanitized = sanitizeText(value.asText());
        if (!sanitized.equals(value.asText())) {
          arrayNode.set(i, TextNode.valueOf(sanitized));
        }
        continue;
      }

      sanitize(value);
    }
  }

  private static String sanitizeText(String input) {
    if (input == null || input.isEmpty()) return input;

    StringBuilder output = null;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == NUL || Character.isSurrogate(c)) {
        if (output == null) {
          output = new StringBuilder(input.length());
          output.append(input, 0, i);
        }
        output.append(REPLACEMENT);
      } else if (output != null) {
        output.append(c);
      }
    }

    return output == null ? input : output.toString();
  }
}
