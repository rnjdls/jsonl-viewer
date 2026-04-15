package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntry;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

class JsonlIngestServiceTailSnapshotTest {

  @Test
  void pollFileDoesNotChaseGrowthPastSnapshot() throws Exception {
    Path jsonlFile = Files.createTempFile("ingest-tail-snapshot", ".jsonl");

    ExecutorService executor = Executors.newSingleThreadExecutor();
    CountDownLatch firstPersistReached = new CountDownLatch(1);
    CountDownLatch continuePersist = new CountDownLatch(1);

    try {
      int initialLines = 32;
      int appendedLines = 18;
      String initialContent = linesJson(0, initialLines);
      Files.writeString(jsonlFile, initialContent, StandardCharsets.UTF_8);
      long initialSize = Files.size(jsonlFile);

      AppProperties properties = new AppProperties();
      properties.setJsonlFilePath(jsonlFile.toString());
      properties.setIngestBatchSize(1);

      JsonlEntryRepository jsonlEntryRepository = mock(JsonlEntryRepository.class);
      IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
      JsonFieldIndexExtractor fieldIndexExtractor = mock(JsonFieldIndexExtractor.class);
      EntityManager entityManager = mock(EntityManager.class);

      when(ingestStateRepository.findById(jsonlFile.toString())).thenReturn(Optional.empty());
      when(ingestStateRepository.save(any(IngestState.class)))
          .thenAnswer((Answer<IngestState>) invocation -> invocation.getArgument(0));
      when(fieldIndexExtractor.extract(any(String.class), any(Long.class), any()))
          .thenReturn(List.of());

      AtomicInteger persistedCount = new AtomicInteger();
      org.mockito.Mockito.doAnswer(invocation -> {
        persistedCount.incrementAndGet();
        firstPersistReached.countDown();
        assertTrue(continuePersist.await(5, TimeUnit.SECONDS));
        return null;
      }).when(entityManager).persist(any(JsonlEntry.class));

      JsonlIngestService service = new JsonlIngestService(
          properties,
          new IngestSourceResolver(properties),
          jsonlEntryRepository,
          ingestStateRepository,
          new IngestPauseState(),
          new JsonlEntryParser(new ObjectMapper()),
          fieldIndexExtractor,
          entityManager
      );

      Future<?> ingestFuture = executor.submit(service::pollFile);
      assertTrue(firstPersistReached.await(5, TimeUnit.SECONDS));

      Files.writeString(
          jsonlFile,
          linesJson(initialLines, initialLines + appendedLines),
          StandardCharsets.UTF_8,
          StandardOpenOption.APPEND
      );

      continuePersist.countDown();
      ingestFuture.get(10, TimeUnit.SECONDS);

      ArgumentCaptor<IngestState> stateCaptor = ArgumentCaptor.forClass(IngestState.class);
      verify(ingestStateRepository).save(stateCaptor.capture());

      IngestState savedState = stateCaptor.getValue();
      assertEquals(initialSize, savedState.getByteOffset());
      assertEquals(initialLines, savedState.getLineNo());
      assertEquals(initialLines, persistedCount.get());
    } finally {
      continuePersist.countDown();
      executor.shutdownNow();
      Files.deleteIfExists(jsonlFile);
    }
  }

  private String linesJson(int start, int endExclusive) {
    return IntStream.range(start, endExclusive)
        .mapToObj(i -> "{\"seq\":" + i + "}\n")
        .collect(Collectors.joining());
  }
}
