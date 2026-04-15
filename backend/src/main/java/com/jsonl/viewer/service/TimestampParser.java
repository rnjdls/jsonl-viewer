package com.jsonl.viewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public final class TimestampParser {
  private static final long EPOCH_MILLIS_THRESHOLD = 1_000_000_000_000L;

  private TimestampParser() {}

  public static Instant parse(String rawValue) {
    String trimmed = safeTrim(rawValue);
    if (trimmed.isEmpty()) return null;

    Instant epochInstant = parseEpoch(trimmed);
    if (epochInstant != null) return epochInstant;

    Instant instant = parseAsInstant(trimmed);
    if (instant != null) return instant;

    Instant offsetDateTimeInstant = parseAsOffsetDateTime(trimmed);
    if (offsetDateTimeInstant != null) return offsetDateTimeInstant;

    Instant localDateTimeInstant = parseAsLocalDateTimeUtc(trimmed);
    if (localDateTimeInstant != null) return localDateTimeInstant;

    int firstSpace = trimmed.indexOf(' ');
    if (firstSpace > 0 && firstSpace == trimmed.lastIndexOf(' ')) {
      String withT = trimmed.substring(0, firstSpace) + "T" + trimmed.substring(firstSpace + 1);
      Instant retriedOffsetDateTime = parseAsOffsetDateTime(withT);
      if (retriedOffsetDateTime != null) return retriedOffsetDateTime;
      return parseAsLocalDateTimeUtc(withT);
    }

    return null;
  }

  public static Instant parseJsonScalar(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isNumber() || node.isTextual()) {
      return parse(node.asText());
    }
    return null;
  }

  private static String safeTrim(String value) {
    return value == null ? "" : value.trim();
  }

  private static Instant parseEpoch(String raw) {
    if (!raw.matches("^[+-]?\\d+$")) return null;
    try {
      long epochValue = Long.parseLong(raw);
      return epochValue > EPOCH_MILLIS_THRESHOLD
          ? Instant.ofEpochMilli(epochValue)
          : Instant.ofEpochSecond(epochValue);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static Instant parseAsInstant(String raw) {
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private static Instant parseAsOffsetDateTime(String raw) {
    try {
      return OffsetDateTime.parse(raw).toInstant();
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private static Instant parseAsLocalDateTimeUtc(String raw) {
    try {
      return LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }
}
