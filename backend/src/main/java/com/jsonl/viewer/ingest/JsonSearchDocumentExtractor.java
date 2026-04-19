package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class JsonSearchDocumentExtractor {
  private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
  private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]+");
  private static final Pattern SINGLE_TOKEN = Pattern.compile("^[A-Za-z0-9_]+$");

  public String extract(JsonNode parsed) {
    if (parsed == null || parsed.isNull()) {
      return null;
    }

    Map<String, String> dedupedTokens = new LinkedHashMap<>();
    walk(parsed, new ArrayList<>(), dedupedTokens);
    if (dedupedTokens.isEmpty()) {
      return null;
    }
    return String.join(" ", dedupedTokens.values());
  }

  private void walk(JsonNode node, List<String> pathSegments, Map<String, String> dedupedTokens) {
    if (node == null || node.isNull()) {
      return;
    }

    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        pathSegments.add(field.getKey());
        walk(field.getValue(), pathSegments, dedupedTokens);
        pathSegments.remove(pathSegments.size() - 1);
      }
      return;
    }

    if (node.isArray()) {
      for (JsonNode item : node) {
        walk(item, pathSegments, dedupedTokens);
      }
      return;
    }

    String scalarValue = toScalarValue(node);
    if (scalarValue == null || scalarValue.isBlank()) {
      return;
    }

    for (String pathSegment : pathSegments) {
      addLexeme(pathSegment, dedupedTokens);
    }
    addLexeme(scalarValue, dedupedTokens);
  }

  private void addLexeme(String raw, Map<String, String> dedupedTokens) {
    if (raw == null) {
      return;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return;
    }

    if (SINGLE_TOKEN.matcher(trimmed).matches()) {
      addToken(trimmed, dedupedTokens);
    }

    String splitReady = CAMEL_BOUNDARY.matcher(trimmed).replaceAll(" ");
    String[] pieces = NON_ALNUM.split(splitReady);
    for (String piece : pieces) {
      addToken(piece, dedupedTokens);
    }
  }

  private void addToken(String rawToken, Map<String, String> dedupedTokens) {
    if (rawToken == null) {
      return;
    }
    String token = rawToken.trim();
    if (token.isEmpty()) {
      return;
    }
    String dedupeKey = token.toLowerCase(Locale.ROOT);
    dedupedTokens.putIfAbsent(dedupeKey, token);
  }

  private String toScalarValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isTextual() || node.isNumber() || node.isBoolean()) {
      return node.asText();
    }
    return null;
  }
}
