package com.jsonl.viewer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.api.dto.StatsResponse;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlControllerStatsProgressTest {

  @TempDir
  Path tempDir;

  @Test
  void statsIncludesIngestAndTargetBytesInFileMode() throws IOException {
    Path sourceFile = tempDir.resolve("source.jsonl");
    Files.writeString(sourceFile, "{\"a\":1}\n{\"b\":2}\n", StandardCharsets.UTF_8);

    AppProperties properties = fileProperties(sourceFile.toString());
    IngestState state = new IngestState(sourceFile.toString(), 8L, 2L, Instant.parse("2026-04-10T10:15:30.000Z"));
    state.setTotalCount(2L);
    state.setParsedCount(2L);
    state.setErrorCount(0L);

    JsonlController controller = controller(properties, Optional.of(state));

    StatsResponse response = controller.stats();

    assertEquals(8L, response.ingestedBytes());
    assertEquals(Files.size(sourceFile), response.targetBytes());
    assertFalse(response.exactCountAvailable());
  }

  @Test
  void statsReportsExactCountAvailableWhenBytesCatchUp() throws IOException {
    Path sourceFile = tempDir.resolve("source-complete.jsonl");
    Files.writeString(sourceFile, "{\"a\":1}\n{\"b\":2}\n", StandardCharsets.UTF_8);
    long sourceSize = Files.size(sourceFile);

    AppProperties properties = fileProperties(sourceFile.toString());
    IngestState state = new IngestState(sourceFile.toString(), sourceSize, 3L, Instant.parse("2026-04-10T10:15:30.000Z"));
    state.setTotalCount(2L);

    JsonlController controller = controller(properties, Optional.of(state));
    StatsResponse response = controller.stats();

    assertTrue(response.exactCountAvailable());
  }

  @Test
  void statsReportsExactCountAvailableWhenIngestionIsPaused() throws IOException {
    Path sourceFile = tempDir.resolve("source-paused.jsonl");
    Files.writeString(sourceFile, "{\"a\":1}\n{\"b\":2}\n", StandardCharsets.UTF_8);

    AppProperties properties = fileProperties(sourceFile.toString());
    IngestState state = new IngestState(sourceFile.toString(), 4L, 2L, Instant.parse("2026-04-10T10:15:30.000Z"));
    IngestPauseState pauseState = new IngestPauseState();
    pauseState.pause();

    JsonlController controller = controller(properties, Optional.of(state), pauseState);
    StatsResponse response = controller.stats();

    assertTrue(response.exactCountAvailable());
  }

  @Test
  void statsReturnsNullProgressWhenFileSizeIsUnavailable() {
    Path missingSource = tempDir.resolve("missing.jsonl");
    AppProperties properties = fileProperties(missingSource.toString());
    IngestState state = new IngestState(missingSource.toString(), 12L, 0L, null);

    JsonlController controller = controller(properties, Optional.of(state));
    StatsResponse response = controller.stats();

    assertNull(response.ingestedBytes());
    assertNull(response.targetBytes());
    assertTrue(response.exactCountAvailable());
  }

  @Test
  void statsReturnsNullProgressInKafkaMode() {
    AppProperties properties = new AppProperties();
    properties.setIngestMode("kafka");
    properties.setSourceId("kafka:events");

    IngestState state = new IngestState("kafka:events", 100L, 0L, null);
    JsonlController controller = controller(properties, Optional.of(state));
    StatsResponse response = controller.stats();

    assertNull(response.ingestedBytes());
    assertNull(response.targetBytes());
    assertTrue(response.exactCountAvailable());
  }

  private AppProperties fileProperties(String sourcePath) {
    AppProperties properties = new AppProperties();
    properties.setIngestMode("file");
    properties.setJsonlFilePath(sourcePath);
    return properties;
  }

  private JsonlController controller(AppProperties properties, Optional<IngestState> state) {
    return controller(properties, state, new IngestPauseState());
  }

  private JsonlController controller(
      AppProperties properties,
      Optional<IngestState> state,
      IngestPauseState pauseState
  ) {
    IngestSourceResolver sourceResolver = new IngestSourceResolver(properties);
    String sourceId = sourceResolver.getActiveSourceId();
    IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
    if (sourceId != null) {
      when(ingestStateRepository.findById(sourceId)).thenReturn(state);
    }

    JsonlEntryRepository jsonlEntryRepository = mock(JsonlEntryRepository.class);
    return new JsonlController(
        properties,
        sourceResolver,
        jsonlEntryRepository,
        ingestStateRepository,
        new FilterService(),
        new FilterRequestHasher(),
        mock(FilterCountCacheService.class),
        new NoopIngestAdminService(),
        pauseState,
        new PreviewCursorCodec(new ObjectMapper())
    );
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
