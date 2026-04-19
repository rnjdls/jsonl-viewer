package com.jsonl.viewer.ingest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

class JsonlIngestServicePauseStateTest {

  @Test
  void pollFileReturnsImmediatelyWhenPaused() {
    AppProperties properties = new AppProperties();
    properties.setJsonlFilePath("/tmp/paused.jsonl");

    JsonlEntryRepository jsonlEntryRepository = mock(JsonlEntryRepository.class);
    IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
    JsonFieldIndexExtractor fieldIndexExtractor = mock(JsonFieldIndexExtractor.class);
    JsonSearchDocumentExtractor searchDocumentExtractor = mock(JsonSearchDocumentExtractor.class);
    EntityManager entityManager = mock(EntityManager.class);
    IngestPauseState pauseState = new IngestPauseState();
    pauseState.pause();

    JsonlIngestService service = new JsonlIngestService(
        properties,
        new IngestSourceResolver(properties),
        jsonlEntryRepository,
        ingestStateRepository,
        pauseState,
        new JsonlEntryParser(new ObjectMapper()),
        fieldIndexExtractor,
        searchDocumentExtractor,
        entityManager
    );

    service.pollFile();

    verifyNoInteractions(jsonlEntryRepository, ingestStateRepository, entityManager);
  }
}
