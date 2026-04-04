package com.jsonl.viewer.service;

import java.time.Instant;

public record FilterCriteria(
    String type,
    String fieldPath,
    String valueContains,
    Instant from,
    Instant to
) {}
