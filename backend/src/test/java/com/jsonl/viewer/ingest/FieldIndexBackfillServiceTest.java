package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntryFieldIndex;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;
import org.springframework.transaction.PlatformTransactionManager;

class FieldIndexBackfillServiceTest {

  @Test
  void backfillOneSourceRebuildsFieldIndexAndMarksSourceReady() throws Exception {
    IngestStateRepository ingestStateRepository = mock(IngestStateRepository.class);
    JsonFieldIndexExtractor fieldIndexExtractor = mock(JsonFieldIndexExtractor.class);
    EntityManager entityManager = mock(EntityManager.class);
    Query deleteQuery = mock(Query.class);
    Query selectQuery = mock(Query.class);

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

    when(ingestStateRepository.findPendingFieldIndexBuilds()).thenReturn(List.of(state));
    when(ingestStateRepository.findById("source-a")).thenReturn(Optional.of(state));
    when(ingestStateRepository.save(any(IngestState.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    when(entityManager.createNativeQuery(argThat(sql ->
        sql != null && sql.contains("DELETE FROM jsonl_entry_field_index")))).thenReturn(deleteQuery);
    when(entityManager.createNativeQuery(argThat(sql ->
        sql != null && sql.contains("SELECT id, parsed")))).thenReturn(selectQuery);

    when(deleteQuery.setParameter(anyInt(), any())).thenReturn(deleteQuery);
    when(deleteQuery.executeUpdate()).thenReturn(1);

    when(selectQuery.setParameter(anyInt(), any())).thenReturn(selectQuery);

    PGobject pgObject = new PGobject();
    pgObject.setType("jsonb");
    pgObject.setValue("{\"timestamp\":\"2026-04-06T13:23:58Z\"}");
    when(selectQuery.getResultList()).thenReturn(
        List.<Object[]>of(new Object[] {11L, pgObject}),
        List.of()
    );

    JsonlEntryFieldIndex indexRow = new JsonlEntryFieldIndex(
        11L,
        "source-a",
        "timestamp",
        "timestamp",
        "2026-04-06T13:23:58Z",
        Instant.parse("2026-04-06T13:23:58Z"),
        "string",
        false,
        false
    );
    when(fieldIndexExtractor.extract(eq("source-a"), eq(11L), any(JsonNode.class)))
        .thenReturn(List.of(indexRow));

    FieldIndexBackfillService service = new FieldIndexBackfillService(
        ingestStateRepository,
        fieldIndexExtractor,
        new ObjectMapper(),
        entityManager,
        mock(PlatformTransactionManager.class)
    );

    boolean processed = service.backfillOneSource();

    assertTrue(processed);
    verify(deleteQuery).executeUpdate();
    verify(entityManager).persist(indexRow);
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
    when(ingestStateRepository.findPendingFieldIndexBuilds()).thenReturn(List.of());

    FieldIndexBackfillService service = new FieldIndexBackfillService(
        ingestStateRepository,
        mock(JsonFieldIndexExtractor.class),
        new ObjectMapper(),
        mock(EntityManager.class),
        mock(PlatformTransactionManager.class)
    );

    assertFalse(service.backfillOneSource());
  }
}
