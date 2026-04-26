package com.jsonl.viewer.repo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record JsonlEntryRow(
    long id,
    long lineNo,
    Instant ts,
    JsonNode key,
    JsonNode headers,
    String error,
    String rawSnippet,
    Boolean rawTruncated
) {}
