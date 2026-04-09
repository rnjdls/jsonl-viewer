package com.jsonl.viewer.service;

import java.time.Instant;

public record FilterCriteria(
    String type,
    String fieldPath,
    String op,
    String valueContains,
    String query,
    Instant from,
    Instant to
) {}
