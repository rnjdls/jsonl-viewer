package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record JsonlEntryParseResult(JsonNode parsed, String parseError, Instant ts) {}
