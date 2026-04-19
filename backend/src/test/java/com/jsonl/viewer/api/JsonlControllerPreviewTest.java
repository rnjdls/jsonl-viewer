package com.jsonl.viewer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.api.dto.PreviewRequest;
import com.jsonl.viewer.api.dto.PreviewResponse;
import com.jsonl.viewer.api.dto.FilterSpec;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import com.jsonl.viewer.ingest.IngestAdminService;
import com.jsonl.viewer.ingest.IngestPauseState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import com.jsonl.viewer.repo.JsonlEntryRow;
import com.jsonl.viewer.service.FilterCountCacheService;
import com.jsonl.viewer.service.FilterRequestHasher;
import com.jsonl.viewer.service.FilterService;
import com.jsonl.viewer.service.PreviewCursorCodec;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class JsonlControllerPreviewTest {

  @Test
  void previewReturnsBadRequestWhenCursorSortDirDoesNotMatchRequest() {
    AppProperties properties = appProperties();
    JsonlEntryRepository repository = org.mockito.Mockito.mock(JsonlEntryRepository.class);
    IngestStateRepository ingestStateRepository = org.mockito.Mockito.mock(IngestStateRepository.class);
    when(ingestStateRepository.findById(jsonlFilePath(properties))).thenReturn(Optional.empty());
    PreviewCursorCodec codec = new PreviewCursorCodec(new ObjectMapper());
    JsonlController controller = controller(properties, repository, ingestStateRepository, codec);

    PreviewRequest request = new PreviewRequest();
    request.setSortDir("asc");
    request.setCursor(codec.encode(new PreviewCursor("desc", 14L, 12L)));

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> controller.preview(request)
    );

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    verify(repository, never()).preview(any(), any(), any(), any(), anyInt(), nullable(Long.class));
  }

  @Test
  void previewDefaultsSortDirToDescWhenMissing() {
    AppProperties properties = appProperties();
    JsonlEntryRepository repository = org.mockito.Mockito.mock(JsonlEntryRepository.class);
    when(repository.preview(any(), any(), any(), any(), anyInt(), nullable(Long.class))).thenReturn(List.of());
    IngestStateRepository ingestStateRepository = org.mockito.Mockito.mock(IngestStateRepository.class);
    when(ingestStateRepository.findById(jsonlFilePath(properties))).thenReturn(Optional.empty());
    JsonlController controller = controller(
        properties,
        repository,
        ingestStateRepository,
        new PreviewCursorCodec(new ObjectMapper())
    );

    controller.preview(new PreviewRequest());

    verify(repository).preview(
        eq(jsonlFilePath(properties)),
        any(),
        eq("desc"),
        isNull(),
        eq(10),
        nullable(Long.class)
    );
  }

  @Test
  void previewKeepsRowResponseShape() throws Exception {
    AppProperties properties = appProperties();
    JsonlEntryRepository repository = org.mockito.Mockito.mock(JsonlEntryRepository.class);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode key = mapper.readTree("{\"id\":\"abc\"}");
    JsonNode headers = mapper.readTree("{\"event\":\"login\"}");
    Instant ts = Instant.parse("2026-04-10T10:15:30.000Z");
    when(repository.preview(any(), any(), any(), any(), anyInt(), nullable(Long.class))).thenReturn(List.of(
        new JsonlEntryRow(5L, 9L, ts, key, headers, "parse failed", "{\"raw\":1}", true)
    ));
    IngestStateRepository ingestStateRepository = org.mockito.Mockito.mock(IngestStateRepository.class);
    when(ingestStateRepository.findById(jsonlFilePath(properties))).thenReturn(Optional.empty());
    JsonlController controller = controller(properties, repository, ingestStateRepository, new PreviewCursorCodec(mapper));

    PreviewRequest request = new PreviewRequest();
    request.setLimit(1);
    PreviewResponse response = controller.preview(request);

    assertEquals(1, response.rows().size());
    assertEquals(5L, response.rows().get(0).id());
    assertEquals(9L, response.rows().get(0).lineNo());
    assertEquals(ts, response.rows().get(0).ts());
    assertEquals(key, response.rows().get(0).key());
    assertEquals(headers, response.rows().get(0).headers());
    assertEquals("parse failed", response.rows().get(0).error());
    assertEquals("{\"raw\":1}", response.rows().get(0).rawSnippet());
    assertEquals(true, response.rows().get(0).rawTruncated());
    assertNotNull(response.nextCursor());
  }

  @Test
  void previewUsesTextOnlyFastPathWhenOnlySingleTextFilterIsProvided() {
    AppProperties properties = appProperties();
    JsonlEntryRepository repository = org.mockito.Mockito.mock(JsonlEntryRepository.class);
    when(repository.previewTextOnly(any(), any(), any(), any(), anyInt(), nullable(Long.class)))
        .thenReturn(List.of());
    IngestStateRepository ingestStateRepository = org.mockito.Mockito.mock(IngestStateRepository.class);
    when(ingestStateRepository.findById(jsonlFilePath(properties))).thenReturn(Optional.empty());
    JsonlController controller = controller(
        properties,
        repository,
        ingestStateRepository,
        new PreviewCursorCodec(new ObjectMapper())
    );

    FilterSpec text = new FilterSpec();
    text.setType("text");
    text.setQuery("gateway timeout");
    PreviewRequest request = new PreviewRequest();
    request.setFilters(List.of(text));

    controller.preview(request);

    verify(repository).previewTextOnly(
        eq(jsonlFilePath(properties)),
        eq("gateway timeout"),
        eq("desc"),
        isNull(),
        eq(10),
        nullable(Long.class)
    );
    verify(repository, never()).preview(any(), any(), any(), any(), anyInt(), nullable(Long.class));
  }

  @Test
  void previewUsesGenericPathWhenTextAndFieldFiltersAreMixed() {
    AppProperties properties = appProperties();
    JsonlEntryRepository repository = org.mockito.Mockito.mock(JsonlEntryRepository.class);
    when(repository.preview(any(), any(), any(), any(), anyInt(), nullable(Long.class)))
        .thenReturn(List.of());
    IngestStateRepository ingestStateRepository = org.mockito.Mockito.mock(IngestStateRepository.class);
    when(ingestStateRepository.findById(jsonlFilePath(properties))).thenReturn(Optional.empty());
    JsonlController controller = controller(
        properties,
        repository,
        ingestStateRepository,
        new PreviewCursorCodec(new ObjectMapper())
    );

    FilterSpec text = new FilterSpec();
    text.setType("text");
    text.setQuery("gateway timeout");
    FilterSpec field = new FilterSpec();
    field.setType("field");
    field.setFieldPath("headers.status");
    field.setValueContains("500");
    PreviewRequest request = new PreviewRequest();
    request.setFilters(List.of(text, field));

    controller.preview(request);

    verify(repository).preview(
        eq(jsonlFilePath(properties)),
        any(),
        eq("desc"),
        isNull(),
        eq(10),
        nullable(Long.class)
    );
    verify(repository, never()).previewTextOnly(any(), any(), any(), any(), anyInt(), nullable(Long.class));
  }

  private AppProperties appProperties() {
    AppProperties properties = new AppProperties();
    properties.setJsonlFilePath("/tmp/sample.jsonl");
    return properties;
  }

  private JsonlController controller(
      AppProperties properties,
      JsonlEntryRepository repository,
      IngestStateRepository ingestStateRepository,
      PreviewCursorCodec codec
  ) {
    return new JsonlController(
        properties,
        new IngestSourceResolver(properties),
        repository,
        ingestStateRepository,
        new FilterService(),
        new FilterRequestHasher(),
        org.mockito.Mockito.mock(FilterCountCacheService.class),
        new NoopIngestAdminService(),
        new IngestPauseState(),
        codec
    );
  }

  private String jsonlFilePath(AppProperties properties) {
    return properties.getJsonlFilePath();
  }

  private static class NoopIngestAdminService implements IngestAdminService {
    @Override
    public void reset() {}

    @Override
    public void reload() {}

    @Override
    public void pause() {}

    @Override
    public void resume() {}
  }
}
