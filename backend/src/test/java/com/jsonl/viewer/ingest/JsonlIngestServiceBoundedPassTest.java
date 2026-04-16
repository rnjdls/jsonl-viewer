package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JsonlIngestServiceBoundedPassTest {

  @Test
  void pollFileAppliesByteCapAtLineBoundaryAndResumesCorrectly() throws Exception {
    Path jsonlFile = Files.createTempFile("ingest-byte-cap", ".jsonl");
    try {
      List<String> lines = List.of(
          "{\"event\":\"aaaaaaaa\"}",
          "{\"event\":\"bbbbbbbb\"}",
          "{\"event\":\"cccccccc\"}",
          "{\"event\":\"dddddddd\"}"
      );
      Files.writeString(jsonlFile, joinLines(lines), StandardCharsets.UTF_8);

      long byteCap = bytesForPrefix(lines, 1) + 1;
      TestFixture fixture = createFixture(jsonlFile, properties -> {
        properties.setIngestMaxBytesPerPass(byteCap);
      });

      fixture.service.pollFile();
      assertEquals(List.of(1L, 2L), fixture.persistedLineNos);
      assertState(fixture.stateRef.get(), 2, bytesForPrefix(lines, 2));

      fixture.service.pollFile();
      assertEquals(List.of(1L, 2L, 3L, 4L), fixture.persistedLineNos);
      assertState(fixture.stateRef.get(), 4, bytesForPrefix(lines, 4));
    } finally {
      Files.deleteIfExists(jsonlFile);
    }
  }

  @Test
  void pollFileIngestsOversizedFirstLineSoPassCannotLivelock() throws Exception {
    Path jsonlFile = Files.createTempFile("ingest-oversized-line", ".jsonl");
    try {
      String oversized = "{\"payload\":\"" + "x".repeat(400) + "\"}";
      List<String> lines = List.of(oversized, "{\"seq\":2}");
      Files.writeString(jsonlFile, joinLines(lines), StandardCharsets.UTF_8);

      long byteCap = 64;
      assertTrue((lines.get(0) + "\n").getBytes(StandardCharsets.UTF_8).length > byteCap);

      TestFixture fixture = createFixture(jsonlFile, properties -> {
        properties.setIngestMaxBytesPerPass(byteCap);
      });

      fixture.service.pollFile();
      assertEquals(List.of(1L), fixture.persistedLineNos);
      assertState(fixture.stateRef.get(), 1, bytesForPrefix(lines, 1));

      fixture.service.pollFile();
      assertEquals(List.of(1L, 2L), fixture.persistedLineNos);
      assertState(fixture.stateRef.get(), 2, bytesForPrefix(lines, 2));
    } finally {
      Files.deleteIfExists(jsonlFile);
    }
  }

  private TestFixture createFixture(Path jsonlFile, java.util.function.Consumer<AppProperties> customizer) {
    AppProperties properties = new AppProperties();
    properties.setJsonlFilePath(jsonlFile.toString());
    properties.setIngestBatchSize(500);
    customizer.accept(properties);

    JsonlEntryRepository jsonlEntryRepository = mock(JsonlEntryRepository.class);
    IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
    JsonFieldIndexExtractor fieldIndexExtractor = mock(JsonFieldIndexExtractor.class);
    EntityManager entityManager = mock(EntityManager.class);

    AtomicReference<IngestState> stateRef = new AtomicReference<>();
    when(ingestStateRepository.findById(jsonlFile.toString()))
        .thenAnswer(invocation -> Optional.ofNullable(stateRef.get()));
    when(ingestStateRepository.save(any(IngestState.class)))
        .thenAnswer(invocation -> {
          IngestState state = invocation.getArgument(0);
          stateRef.set(copyState(state));
          return state;
        });
    when(fieldIndexExtractor.extract(any(String.class), any(Long.class), any())).thenReturn(List.of());

    List<Long> persistedLineNos = new ArrayList<>();
    doAnswer(invocation -> {
      JsonlEntry entry = invocation.getArgument(0);
      persistedLineNos.add(entry.getLineNo());
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

    return new TestFixture(service, stateRef, persistedLineNos);
  }

  private void assertState(IngestState state, long lineNo, long offset) {
    assertEquals(lineNo, state.getLineNo());
    assertEquals(offset, state.getByteOffset());
    assertEquals(lineNo, state.getTotalCount());
    assertEquals(lineNo, state.getParsedCount());
  }

  private long bytesForPrefix(List<String> lines, int count) {
    return lines.stream()
        .limit(count)
        .map(line -> line + "\n")
        .mapToLong(line -> line.getBytes(StandardCharsets.UTF_8).length)
        .sum();
  }

  private String joinLines(List<String> lines) {
    return String.join("\n", lines) + "\n";
  }

  private IngestState copyState(IngestState original) {
    return new IngestState(
        original.getFilePath(),
        original.getByteOffset(),
        original.getLineNo(),
        original.getLastIngestedAt(),
        original.getTotalCount(),
        original.getParsedCount(),
        original.getErrorCount(),
        original.getSourceRevision(),
        original.getIndexedRevision(),
        original.getIngestStatus()
    );
  }

  private record TestFixture(
      JsonlIngestService service,
      AtomicReference<IngestState> stateRef,
      List<Long> persistedLineNos
  ) {}
}
