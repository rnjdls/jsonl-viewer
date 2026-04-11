package com.jsonl.viewer.config;

public enum IngestMode {
  FILE,
  KAFKA;

  public static IngestMode fromRaw(String value) {
    if (value == null || value.isBlank()) {
      return FILE;
    }

    if ("kafka".equalsIgnoreCase(value)) {
      return KAFKA;
    }

    return FILE;
  }
}
