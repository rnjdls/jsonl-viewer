package com.jsonl.viewer.service;

public record FilterCriteria(
    String type,
    String fieldPath,
    String op,
    String valueContains,
    String query
) {}
