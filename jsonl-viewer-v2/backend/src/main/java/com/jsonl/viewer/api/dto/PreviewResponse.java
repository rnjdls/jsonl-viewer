package com.jsonl.viewer.api.dto;

import java.util.List;

public record PreviewResponse(List<PreviewRow> rows, String nextCursor) {}
