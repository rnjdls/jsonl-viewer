package com.jsonl.viewer.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record PreviewRow(
    long id,
    long lineNo,
    String raw,
    JsonNode parsed,
    String error,
    Instant ts
) {}
