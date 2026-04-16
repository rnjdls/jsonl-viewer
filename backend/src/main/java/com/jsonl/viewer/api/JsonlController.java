package com.jsonl.viewer.api;

import com.jsonl.viewer.api.dto.EntryDetailResponse;
import com.jsonl.viewer.api.dto.FilterCountRequest;
import com.jsonl.viewer.api.dto.FilterCountResponse;
import com.jsonl.viewer.api.dto.PreviewRequest;
import com.jsonl.viewer.api.dto.PreviewResponse;
import com.jsonl.viewer.api.dto.PreviewRow;
import com.jsonl.viewer.api.dto.StatsResponse;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import com.jsonl.viewer.ingest.IngestAdminService;
import com.jsonl.viewer.ingest.IngestPauseState;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntryDetailRow;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import com.jsonl.viewer.repo.JsonlEntryRow;
import com.jsonl.viewer.service.FilterCountCacheService;
import com.jsonl.viewer.service.FilterCountCacheService.Snapshot;
import com.jsonl.viewer.service.FilterCriteria;
import com.jsonl.viewer.service.FilterRequestHasher;
import com.jsonl.viewer.service.FilterService;
import com.jsonl.viewer.service.PreviewCursorCodec;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class JsonlController {
  private static final String SORT_DIR_ASC = "asc";
  private static final String SORT_DIR_DESC = "desc";

  private final AppProperties properties;
  private final IngestSourceResolver sourceResolver;
  private final JsonlEntryRepository jsonlEntryRepository;
  private final IngestStateRepository ingestStateRepository;
  private final FilterService filterService;
  private final FilterRequestHasher filterRequestHasher;
  private final FilterCountCacheService filterCountCacheService;
  private final IngestAdminService ingestAdminService;
  private final IngestPauseState ingestPauseState;
  private final PreviewCursorCodec previewCursorCodec;

  public JsonlController(
      AppProperties properties,
      IngestSourceResolver sourceResolver,
      JsonlEntryRepository jsonlEntryRepository,
      IngestStateRepository ingestStateRepository,
      FilterService filterService,
      FilterRequestHasher filterRequestHasher,
      FilterCountCacheService filterCountCacheService,
      IngestAdminService ingestAdminService,
      IngestPauseState ingestPauseState,
      PreviewCursorCodec previewCursorCodec
  ) {
    this.properties = properties;
    this.sourceResolver = sourceResolver;
    this.jsonlEntryRepository = jsonlEntryRepository;
    this.ingestStateRepository = ingestStateRepository;
    this.filterService = filterService;
    this.filterRequestHasher = filterRequestHasher;
    this.filterCountCacheService = filterCountCacheService;
    this.ingestAdminService = ingestAdminService;
    this.ingestPauseState = ingestPauseState;
    this.previewCursorCodec = previewCursorCodec;
  }

  @GetMapping("/stats")
  public StatsResponse stats() {
    String sourceId = sourceResolver.getActiveSourceId();
    if (sourceId == null) {
      return new StatsResponse(
          null,
          0,
          0,
          0,
          null,
          0,
          "ready",
          ingestPauseState.isPaused()
      );
    }

    IngestState state = ingestStateRepository.findById(sourceId)
        .orElse(new IngestState(sourceId, 0, 0, null));

    return new StatsResponse(
        sourceId,
        state.getTotalCount(),
        state.getParsedCount(),
        state.getErrorCount(),
        state.getLastIngestedAt(),
        state.getSourceRevision(),
        computeSearchStatus(state),
        ingestPauseState.isPaused()
    );
  }

  @PostMapping("/filters/count")
  public FilterCountResponse count(@RequestBody(required = false) FilterCountRequest request) {
    String sourceId = sourceResolver.getActiveSourceId();
    if (sourceId == null) {
      return new FilterCountResponse(
          0,
          0L,
          FilterCountCacheService.STATUS_READY,
          filterRequestHasher.hash(FilterService.FILTERS_OP_AND, List.of()),
          0,
          0L,
          Instant.now()
      );
    }

    IngestState state = ingestStateRepository.findById(sourceId)
        .orElse(new IngestState(sourceId, 0, 0, null));
    FilterCountRequest safeRequest = request == null ? new FilterCountRequest() : request;
    List<FilterCriteria> filters = filterService.normalize(safeRequest);
    String normalizedFiltersOp = filterService.normalizeFiltersOp(safeRequest.getFiltersOp());
    String requestHash = filterRequestHasher.hash(normalizedFiltersOp, filters);
    long sourceRevision = state.getSourceRevision();
    long totalCount = state.getTotalCount();

    if (filters.isEmpty()) {
      return new FilterCountResponse(
          totalCount,
          totalCount,
          FilterCountCacheService.STATUS_READY,
          requestHash,
          sourceRevision,
          sourceRevision,
          Instant.now()
      );
    }

    FilterSql filterSql = filterService.buildFilterSql(filters, normalizedFiltersOp);
    Snapshot snapshot = filterCountCacheService.submitCountJob(
        sourceId,
        sourceRevision,
        requestHash,
        filterSql
    );

    return new FilterCountResponse(
        totalCount,
        snapshot.matchCount(),
        snapshot.status(),
        requestHash,
        sourceRevision,
        snapshot.computedRevision(),
        snapshot.lastComputedAt()
    );
  }

  @GetMapping("/filters/count/{requestHash}")
  public FilterCountResponse countStatus(@PathVariable("requestHash") String requestHash) {
    String sourceId = sourceResolver.getActiveSourceId();
    if (sourceId == null) {
      return new FilterCountResponse(
          0,
          null,
          FilterCountCacheService.STATUS_PENDING,
          requestHash,
          0,
          null,
          null
      );
    }

    IngestState state = ingestStateRepository.findById(sourceId)
        .orElse(new IngestState(sourceId, 0, 0, null));
    Snapshot snapshot = filterCountCacheService.getSnapshot(sourceId, state.getSourceRevision(), requestHash);
    return new FilterCountResponse(
        state.getTotalCount(),
        snapshot.matchCount(),
        snapshot.status(),
        requestHash,
        state.getSourceRevision(),
        snapshot.computedRevision(),
        snapshot.lastComputedAt()
    );
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

    String sortDir = normalizeSortDir(safeRequest.getSortDir());
    PreviewCursor cursor = decodePreviewCursor(
        safeRequest.getCursor(),
        sortDir
    );
    int limit = safeRequest.getLimit() == null ? 10 : Math.min(500, Math.max(1, safeRequest.getLimit()));

    List<JsonlEntryRow> rows = jsonlEntryRepository.preview(
        sourceId,
        filterSql,
        sortDir,
        cursor,
        limit,
        toMillis(properties.getPreviewStatementTimeout())
    );
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
        ? previewCursorCodec.encode(
            toPreviewCursor(rows.get(rows.size() - 1), sortDir)
        )
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

  @PostMapping("/admin/pause")
  public Map<String, String> pause() {
    ingestAdminService.pause();
    return Map.of("status", "ok");
  }

  @PostMapping("/admin/resume")
  public Map<String, String> resume() {
    ingestAdminService.resume();
    return Map.of("status", "ok");
  }

  private String requireFilePath() {
    String sourceId = sourceResolver.getActiveSourceId();
    if (sourceId == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active source configured");
    }
    return sourceId;
  }

  private String computeSearchStatus(IngestState state) {
    if (state.getIndexedRevision() < state.getSourceRevision()) {
      return "building";
    }
    return "ready";
  }

  private PreviewCursor decodePreviewCursor(
      String rawCursor,
      String sortDir
  ) {
    try {
      return previewCursorCodec.decode(
          rawCursor,
          sortDir
      );
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  private String normalizeSortDir(String rawSortDir) {
    if (rawSortDir == null || rawSortDir.isBlank()) {
      return SORT_DIR_DESC;
    }
    if (SORT_DIR_ASC.equalsIgnoreCase(rawSortDir)) {
      return SORT_DIR_ASC;
    }
    if (SORT_DIR_DESC.equalsIgnoreCase(rawSortDir)) {
      return SORT_DIR_DESC;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sortDir: " + rawSortDir);
  }

  private PreviewCursor toPreviewCursor(
      JsonlEntryRow row,
      String sortDir
  ) {
    return new PreviewCursor(sortDir, row.lineNo(), row.id());
  }

  private Long toMillis(Duration duration) {
    if (duration == null || duration.isNegative() || duration.isZero()) {
      return null;
    }
    return duration.toMillis();
  }
}
