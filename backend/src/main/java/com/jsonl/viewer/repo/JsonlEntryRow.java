package com.jsonl.viewer.repo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record JsonlEntryRow(
    long id,
    long lineNo,
    String raw,
    JsonNode parsed,
    String error,
    Instant ts
) {}
