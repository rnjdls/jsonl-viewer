package com.jsonl.viewer.api;

import com.jsonl.viewer.api.dto.FilterCountRequest;
import com.jsonl.viewer.api.dto.FilterCountResponse;
import com.jsonl.viewer.api.dto.EntryDetailResponse;
import com.jsonl.viewer.api.dto.PreviewRequest;
import com.jsonl.viewer.api.dto.PreviewResponse;
import com.jsonl.viewer.api.dto.PreviewRow;
import com.jsonl.viewer.api.dto.StatsResponse;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import com.jsonl.viewer.ingest.IngestAdminService;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntryDetailRow;
import com.jsonl.viewer.repo.JsonlEntryRow;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.Counts;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import com.jsonl.viewer.service.FilterCriteria;
import com.jsonl.viewer.service.FilterService;
import com.jsonl.viewer.service.PreviewCursorCodec;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api")
public class JsonlController {
  private final AppProperties properties;
  private final IngestSourceResolver sourceResolver;
  private final JsonlEntryRepository jsonlEntryRepository;
  private final IngestStateRepository ingestStateRepository;
  private final FilterService filterService;
  private final IngestAdminService ingestAdminService;
  private final PreviewCursorCodec previewCursorCodec;

  public JsonlController(
      AppProperties properties,
      IngestSourceResolver sourceResolver,
      JsonlEntryRepository jsonlEntryRepository,
      IngestStateRepository ingestStateRepository,
      FilterService filterService,
      IngestAdminService ingestAdminService,
      PreviewCursorCodec previewCursorCodec
  ) {
    this.properties = properties;
    this.sourceResolver = sourceResolver;
    this.jsonlEntryRepository = jsonlEntryRepository;
    this.ingestStateRepository = ingestStateRepository;
    this.filterService = filterService;
    this.ingestAdminService = ingestAdminService;
    this.previewCursorCodec = previewCursorCodec;
  }

  @GetMapping("/stats")
  public StatsResponse stats() {
    String sourceId = sourceResolver.getActiveSourceId();
    if (sourceId == null) {
      return new StatsResponse(null, properties.getJsonlTimestampField(), 0, 0, 0, null);
    }
    Counts counts = jsonlEntryRepository.getCounts(sourceId);
    Instant lastIngest = ingestStateRepository.findById(sourceId)
        .map(IngestState::getLastIngestedAt)
        .orElse(null);

    return new StatsResponse(
        sourceId,
        properties.getJsonlTimestampField(),
        counts.total(),
        counts.parsed(),
        counts.errors(),
        lastIngest
    );
  }

  @PostMapping("/filters/count")
  public FilterCountResponse count(@RequestBody(required = false) FilterCountRequest request) {
    String sourceId = sourceResolver.getActiveSourceId();
    if (sourceId == null) {
      return new FilterCountResponse(0, 0);
    }
    FilterCountRequest safeRequest = request == null ? new FilterCountRequest() : request;
    List<FilterCriteria> filters = filterService.normalize(safeRequest);
    FilterSql filterSql = filterService.buildFilterSql(filters, safeRequest.getFiltersOp());

    Counts counts = jsonlEntryRepository.getCounts(sourceId);
    long matchCount = jsonlEntryRepository.countMatching(sourceId, filterSql);

    return new FilterCountResponse(counts.total(), matchCount);
  }

  @PostMapping("/filters/preview")
  public PreviewResponse preview(@RequestBody(required = false) PreviewRequest request) {
    String sourceId = sourceResolver.getActiveSourceId();
    if (sourceId == null) {
      return new PreviewResponse(List.of(), null);
    }
    PreviewRequest safeRequest = request == null ? new PreviewRequest() : request;
    List<FilterCriteria> filters = filterService.normalize(safeRequest);
    FilterSql filterSql = filterService.buildFilterSql(filters, safeRequest.getFiltersOp());

    String sortBy = normalizeSortBy(safeRequest.getSortBy());
    String sortDir = normalizeSortDir(safeRequest.getSortDir());
    PreviewCursor cursor = decodePreviewCursor(safeRequest.getCursor(), sortBy, sortDir);
    int limit = safeRequest.getLimit() == null ? 10 : Math.min(500, Math.max(1, safeRequest.getLimit()));

    List<JsonlEntryRow> rows = jsonlEntryRepository.preview(sourceId, filterSql, sortBy, sortDir, cursor, limit);
    List<PreviewRow> responseRows = rows.stream()
        .map(row -> new PreviewRow(
            row.id(),
            row.lineNo(),
            row.ts(),
            row.key(),
            row.headers(),
            row.error(),
            row.rawSnippet(),
            row.rawTruncated()
        ))
        .collect(Collectors.toList());

    String nextCursor = responseRows.size() == limit
        ? previewCursorCodec.encode(toPreviewCursor(rows.get(rows.size() - 1), sortBy, sortDir))
        : null;

    return new PreviewResponse(responseRows, nextCursor);
  }

  @GetMapping("/entries/{id}")
  public EntryDetailResponse entry(@PathVariable("id") long id) {
    String filePath = requireFilePath();
    JsonlEntryDetailRow row = jsonlEntryRepository.findEntryDetail(filePath, id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
    return new EntryDetailResponse(row.id(), row.lineNo(), row.ts(), row.parsed(), row.error());
  }

  @GetMapping(value = "/entries/{id}/raw", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> entryRaw(@PathVariable("id") long id) {
    String filePath = requireFilePath();
    String rawLine = jsonlEntryRepository.findRawLine(filePath, id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_PLAIN)
        .body(rawLine);
  }

  @PostMapping("/admin/reset")
  public Map<String, String> reset() {
    ingestAdminService.reset();
    return Map.of("status", "ok");
  }

  @PostMapping("/admin/reload")
  public Map<String, String> reload() {
    ingestAdminService.reload();
    return Map.of("status", "ok");
  }

  private String requireFilePath() {
    String sourceId = sourceResolver.getActiveSourceId();
    if (sourceId == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active source configured");
    }
    return sourceId;
  }

  private PreviewCursor decodePreviewCursor(String rawCursor, String sortBy, String sortDir) {
    try {
      return previewCursorCodec.decode(rawCursor, sortBy, sortDir);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  private String normalizeSortBy(String rawSortBy) {
    if (rawSortBy == null || rawSortBy.isBlank()) {
      return "timestamp";
    }
    if ("id".equalsIgnoreCase(rawSortBy)) {
      return "id";
    }
    if ("lineno".equalsIgnoreCase(rawSortBy)) {
      return "lineNo";
    }
    if ("timestamp".equalsIgnoreCase(rawSortBy)) {
      return "timestamp";
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sortBy: " + rawSortBy);
  }

  private String normalizeSortDir(String rawSortDir) {
    if (rawSortDir == null || rawSortDir.isBlank()) {
      return "desc";
    }
    if ("asc".equalsIgnoreCase(rawSortDir)) {
      return "asc";
    }
    if ("desc".equalsIgnoreCase(rawSortDir)) {
      return "desc";
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sortDir: " + rawSortDir);
  }

  private PreviewCursor toPreviewCursor(JsonlEntryRow row, String sortBy, String sortDir) {
    return new PreviewCursor(sortBy, sortDir, row.id(), row.lineNo(), row.ts());
  }
}
