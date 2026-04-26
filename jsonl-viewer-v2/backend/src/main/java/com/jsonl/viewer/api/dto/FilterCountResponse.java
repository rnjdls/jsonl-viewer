package com.jsonl.viewer.api.dto;

import java.time.Instant;

public record FilterCountResponse(
    long totalCount,
    Long matchCount,
    String status,
    String requestHash,
    long sourceRevision,
    Long computedRevision,
    Instant lastComputedAt
) {}
