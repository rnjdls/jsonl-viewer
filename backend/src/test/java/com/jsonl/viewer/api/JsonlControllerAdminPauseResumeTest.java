package com.jsonl.viewer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import com.jsonl.viewer.ingest.IngestAdminService;
import com.jsonl.viewer.ingest.IngestPauseState;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import com.jsonl.viewer.service.FilterCountCacheService;
import com.jsonl.viewer.service.FilterRequestHasher;
import com.jsonl.viewer.service.FilterService;
import com.jsonl.viewer.service.PreviewCursorCodec;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsonlControllerAdminPauseResumeTest {

  @Test
  void pauseEndpointDelegatesToAdminService() {
    ControllerFixture fixture = newController(new IngestPauseState());

    Map<String, String> response = fixture.controller().pause();

    assertEquals("ok", response.get("status"));
    verify(fixture.ingestAdminService()).pause();
  }

  @Test
  void resumeEndpointDelegatesToAdminService() {
    ControllerFixture fixture = newController(new IngestPauseState());

    Map<String, String> response = fixture.controller().resume();

    assertEquals("ok", response.get("status"));
    verify(fixture.ingestAdminService()).resume();
  }

  @Test
  void statsIncludesPauseState() {
    IngestPauseState pauseState = new IngestPauseState();
    pauseState.pause();
    ControllerFixture fixture = newController(pauseState);

    assertEquals(true, fixture.controller().stats().ingestPaused());
    assertEquals(true, fixture.controller().stats().exactCountAvailable());
    pauseState.resume();
    assertEquals(false, fixture.controller().stats().ingestPaused());
  }

  private ControllerFixture newController(IngestPauseState pauseState) {
    AppProperties properties = new AppProperties();
    properties.setJsonlFilePath("/tmp/sample.jsonl");

    JsonlEntryRepository jsonlEntryRepository = mock(JsonlEntryRepository.class);
    IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
    when(ingestStateRepository.findById(properties.getJsonlFilePath()))
        .thenReturn(Optional.of(new IngestState(properties.getJsonlFilePath(), 10, 2, Instant.now())));
    FilterCountCacheService filterCountCacheService = mock(FilterCountCacheService.class);
    IngestAdminService ingestAdminService = mock(IngestAdminService.class);

    return new ControllerFixture(
        new JsonlController(
            properties,
            new IngestSourceResolver(properties),
            jsonlEntryRepository,
            ingestStateRepository,
            new FilterService(),
            new FilterRequestHasher(),
            filterCountCacheService,
            ingestAdminService,
            pauseState,
            new PreviewCursorCodec(new com.fasterxml.jackson.databind.ObjectMapper())
        ),
        ingestAdminService
    );
  }

  private record ControllerFixture(
      JsonlController controller,
      IngestAdminService ingestAdminService
  ) {}
}
