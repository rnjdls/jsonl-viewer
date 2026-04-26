package com.jsonl.viewer.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record EntryDetailResponse(
    long id,
    long lineNo,
    Instant ts,
    JsonNode parsed,
    String error
) {}
