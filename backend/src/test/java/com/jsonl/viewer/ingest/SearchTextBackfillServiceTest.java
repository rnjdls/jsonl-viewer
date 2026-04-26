package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;
import org.springframework.transaction.PlatformTransactionManager;

class SearchTextBackfillServiceTest {

  @Test
  void backfillOneSourceRebuildsSearchTextAndMarksSourceReady() throws Exception {
    IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
    JsonSearchDocumentExtractor searchDocumentExtractor = mock(JsonSearchDocumentExtractor.class);
    EntityManager entityManager = mock(EntityManager.class);
    Query resetSearchTextQuery = mock(Query.class);
    Query selectQuery = mock(Query.class);
    Query updateSearchTextQuery = mock(Query.class);

    IngestState state = new IngestState(
        "source-a",
        0,
        0,
        Instant.parse("2026-04-16T12:00:00Z"),
        10,
        10,
        0,
        5,
        1,
        "building"
    );

    when(ingestStateRepository.findPendingSearchTextBuilds()).thenReturn(List.of(state));
    when(ingestStateRepository.findById("source-a")).thenReturn(Optional.of(state));
    when(ingestStateRepository.save(any(IngestState.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    when(entityManager.createNativeQuery(argThat(sql ->
        sql != null && sql.contains("UPDATE jsonl_entry SET search_text = NULL")))).thenReturn(resetSearchTextQuery);
    when(entityManager.createNativeQuery(argThat(sql ->
        sql != null && sql.contains("SELECT id, parsed")))).thenReturn(selectQuery);
    when(entityManager.createNativeQuery(argThat(sql ->
        sql != null && sql.contains("UPDATE jsonl_entry SET search_text = ?1 WHERE id = ?2"))))
        .thenReturn(updateSearchTextQuery);

    when(resetSearchTextQuery.setParameter(anyInt(), any())).thenReturn(resetSearchTextQuery);
    when(resetSearchTextQuery.executeUpdate()).thenReturn(1);

    when(selectQuery.setParameter(anyInt(), any())).thenReturn(selectQuery);
    when(updateSearchTextQuery.setParameter(anyInt(), any())).thenReturn(updateSearchTextQuery);
    when(updateSearchTextQuery.executeUpdate()).thenReturn(1);

    PGobject pgObject = new PGobject();
    pgObject.setType("jsonb");
    pgObject.setValue("{\"headers\":{\"status\":\"ok\"}}");
    when(selectQuery.getResultList()).thenReturn(
        List.<Object[]>of(new Object[] {11L, pgObject}),
        List.of()
    );

    when(searchDocumentExtractor.extract(any(JsonNode.class))).thenReturn("headers status ok");

    SearchTextBackfillService service = new SearchTextBackfillService(
        ingestStateRepository,
        searchDocumentExtractor,
        new ObjectMapper(),
        entityManager,
        mock(PlatformTransactionManager.class)
    );

    boolean processed = service.backfillOneSource();

    assertTrue(processed);
    verify(resetSearchTextQuery).executeUpdate();
    verify(updateSearchTextQuery).executeUpdate();
    verify(entityManager, never()).persist(any());
    verify(entityManager).flush();
    verify(entityManager).clear();

    ArgumentCaptor<IngestState> saveCaptor = ArgumentCaptor.forClass(IngestState.class);
    verify(ingestStateRepository, times(2)).save(saveCaptor.capture());

    IngestState finalState = saveCaptor.getAllValues().get(1);
    assertEquals(5L, finalState.getIndexedRevision());
    assertEquals("ready", finalState.getIngestStatus());
  }

  @Test
  void backfillOneSourceReturnsFalseWhenNoPendingSources() {
    IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
    when(ingestStateRepository.findPendingSearchTextBuilds()).thenReturn(List.of());

    SearchTextBackfillService service = new SearchTextBackfillService(
        ingestStateRepository,
        mock(JsonSearchDocumentExtractor.class),
        new ObjectMapper(),
        mock(EntityManager.class),
        mock(PlatformTransactionManager.class)
    );

    assertFalse(service.backfillOneSource());
  }
}
