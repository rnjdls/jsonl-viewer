package com.jsonl.viewer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record PreviewRow(
    long id,
    long lineNo,
    Instant ts,
    JsonNode key,
    JsonNode headers,
    String error,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String rawSnippet,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Boolean rawTruncated
) {}
