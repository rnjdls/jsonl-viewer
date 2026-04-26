package com.jsonl.viewer.repo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record JsonlEntryDetailRow(
    long id,
    long lineNo,
    Instant ts,
    JsonNode parsed,
    String error
) {}
