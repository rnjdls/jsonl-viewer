package com.jsonl.viewer.api.dto;

import java.time.Instant;

public record StatsResponse(
    String filePath,
    long totalCount,
    long parsedCount,
    long errorCount,
    Instant lastIngestedAt,
    long sourceRevision,
    String searchStatus,
    boolean ingestPaused
) {}
