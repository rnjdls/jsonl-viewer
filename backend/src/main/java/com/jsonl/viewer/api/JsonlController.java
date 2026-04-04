package com.jsonl.viewer.api;

import com.jsonl.viewer.api.dto.FilterCountRequest;
import com.jsonl.viewer.api.dto.FilterCountResponse;
import com.jsonl.viewer.api.dto.PreviewRequest;
import com.jsonl.viewer.api.dto.PreviewResponse;
import com.jsonl.viewer.api.dto.PreviewRow;
import com.jsonl.viewer.api.dto.StatsResponse;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.ingest.JsonlIngestService;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntryRow;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.Counts;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import com.jsonl.viewer.service.FilterCriteria;
import com.jsonl.viewer.service.FilterService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class JsonlController {
  private final AppProperties properties;
  private final JsonlEntryRepository jsonlEntryRepository;
  private final IngestStateRepository ingestStateRepository;
  private final FilterService filterService;
  private final JsonlIngestService ingestService;

  public JsonlController(
      AppProperties properties,
      JsonlEntryRepository jsonlEntryRepository,
      IngestStateRepository ingestStateRepository,
      FilterService filterService,
      JsonlIngestService ingestService
  ) {
    this.properties = properties;
    this.jsonlEntryRepository = jsonlEntryRepository;
    this.ingestStateRepository = ingestStateRepository;
    this.filterService = filterService;
    this.ingestService = ingestService;
  }

  @GetMapping("/stats")
  public StatsResponse stats() {
    String filePath = properties.getJsonlFilePath();
    if (filePath == null || filePath.isBlank()) {
      return new StatsResponse(null, properties.getJsonlTimestampField(), 0, 0, 0, null);
    }
    Counts counts = jsonlEntryRepository.getCounts(filePath);
    Instant lastIngest = ingestStateRepository.findById(filePath)
        .map(IngestState::getLastIngestedAt)
        .orElse(null);

    return new StatsResponse(
        filePath,
        properties.getJsonlTimestampField(),
        counts.total(),
        counts.parsed(),
        counts.errors(),
        lastIngest
    );
  }

  @PostMapping("/filters/count")
  public FilterCountResponse count(@RequestBody(required = false) FilterCountRequest request) {
    String filePath = properties.getJsonlFilePath();
    if (filePath == null || filePath.isBlank()) {
      return new FilterCountResponse(0, 0);
    }
    FilterCountRequest safeRequest = request == null ? new FilterCountRequest() : request;
    List<FilterCriteria> filters = filterService.normalize(safeRequest);
    FilterSql filterSql = filterService.buildFilterSql(filters);

    Counts counts = jsonlEntryRepository.getCounts(filePath);
    long matchCount = jsonlEntryRepository.countMatching(filePath, filterSql);

    return new FilterCountResponse(counts.total(), matchCount);
  }

  @PostMapping("/filters/preview")
  public PreviewResponse preview(@RequestBody(required = false) PreviewRequest request) {
    String filePath = properties.getJsonlFilePath();
    if (filePath == null || filePath.isBlank()) {
      return new PreviewResponse(List.of(), null);
    }
    PreviewRequest safeRequest = request == null ? new PreviewRequest() : request;
    List<FilterCriteria> filters = filterService.normalize(safeRequest);
    FilterSql filterSql = filterService.buildFilterSql(filters);

    long cursor = safeRequest.getCursorId() == null ? 0 : Math.max(0, safeRequest.getCursorId());
    int limit = safeRequest.getLimit() == null ? 200 : Math.min(500, Math.max(1, safeRequest.getLimit()));

    List<JsonlEntryRow> rows = jsonlEntryRepository.preview(filePath, filterSql, cursor, limit);
    List<PreviewRow> responseRows = rows.stream()
        .map(row -> new PreviewRow(
            row.id(),
            row.lineNo(),
            row.raw(),
            row.parsed(),
            row.error(),
            row.ts()
        ))
        .collect(Collectors.toList());

    Long nextCursor = responseRows.size() == limit
        ? responseRows.get(responseRows.size() - 1).id()
        : null;

    return new PreviewResponse(responseRows, nextCursor);
  }

  @PostMapping("/admin/reset")
  public Map<String, String> reset() {
    ingestService.resetToFileEnd();
    return Map.of("status", "ok");
  }

  @PostMapping("/admin/reload")
  public Map<String, String> reload() {
    ingestService.reloadFromStart();
    return Map.of("status", "ok");
  }
}
