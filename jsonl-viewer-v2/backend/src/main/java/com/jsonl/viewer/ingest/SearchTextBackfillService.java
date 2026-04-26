package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SearchTextBackfillService {
  private static final Logger log = LoggerFactory.getLogger(SearchTextBackfillService.class);
  private static final int BACKFILL_BATCH_SIZE = 500;

  private final IngestStateRepository ingestStateRepository;
  private final JsonSearchDocumentExtractor searchDocumentExtractor;
  private final ObjectMapper objectMapper;
  private final EntityManager entityManager;
  private final TransactionTemplate transactionTemplate;
  private final ExecutorService workerExecutor;

  public SearchTextBackfillService(
      IngestStateRepository ingestStateRepository,
      JsonSearchDocumentExtractor searchDocumentExtractor,
      ObjectMapper objectMapper,
      EntityManager entityManager,
      PlatformTransactionManager transactionManager
  ) {
    this.ingestStateRepository = ingestStateRepository;
    this.searchDocumentExtractor = searchDocumentExtractor;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.workerExecutor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "search-text-backfill-worker");
      t.setDaemon(true);
      return t;
    });
  }

  @EventListener(ApplicationReadyEvent.class)
  public void triggerBackfill() {
    workerExecutor.submit(() -> {
      while (Boolean.TRUE.equals(transactionTemplate.execute(status -> backfillOneSource()))) {
        // Continue until no source has pending backfill.
      }
    });
  }

  @PreDestroy
  public void shutdownExecutor() {
    workerExecutor.shutdownNow();
  }

  boolean backfillOneSource() {
    List<IngestState> pendingSources = ingestStateRepository.findPendingSearchTextBuilds();
    if (pendingSources.isEmpty()) {
      return false;
    }

    IngestState state = pendingSources.get(0);
    String filePath = state.getFilePath();
    log.info("Starting search-text backfill for {}", filePath);

    state.setIngestStatus("building");
    ingestStateRepository.save(state);

    entityManager.createNativeQuery("UPDATE jsonl_entry SET search_text = NULL WHERE file_path = ?1")
        .setParameter(1, filePath)
        .executeUpdate();

    long lastSeenId = 0L;
    while (true) {
      Query query = entityManager.createNativeQuery(
          "SELECT id, parsed " +
              "FROM jsonl_entry " +
              "WHERE file_path = ?1 AND parsed IS NOT NULL AND id > ?2 " +
              "ORDER BY id ASC " +
              "LIMIT ?3"
      );
      query.setParameter(1, filePath);
      query.setParameter(2, lastSeenId);
      query.setParameter(3, BACKFILL_BATCH_SIZE);

      @SuppressWarnings("unchecked")
      List<Object[]> rows = query.getResultList();
      if (rows.isEmpty()) {
        break;
      }

      for (Object[] row : rows) {
        long entryId = asLong(row[0]);
        JsonNode parsed = asJsonNode(row[1]);
        lastSeenId = entryId;
        if (parsed == null) {
          continue;
        }
        entityManager.createNativeQuery("UPDATE jsonl_entry SET search_text = ?1 WHERE id = ?2")
            .setParameter(1, searchDocumentExtractor.extract(parsed))
            .setParameter(2, entryId)
            .executeUpdate();
      }
      entityManager.flush();
      entityManager.clear();
    }

    IngestState updatedState = ingestStateRepository.findById(filePath).orElse(state);
    updatedState.setIndexedRevision(updatedState.getSourceRevision());
    updatedState.setIngestStatus("ready");
    ingestStateRepository.save(updatedState);
    log.info("Completed search-text backfill for {}", filePath);

    return true;
  }

  private long asLong(Object value) {
    if (value == null) {
      return 0L;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(value.toString());
  }

  private JsonNode asJsonNode(Object value) {
    if (value == null) {
      return null;
    }
    try {
      if (value instanceof JsonNode jsonNode) {
        return jsonNode;
      }
      if (value instanceof PGobject pgObject) {
        return objectMapper.readTree(pgObject.getValue());
      }
      return objectMapper.readTree(value.toString());
    } catch (Exception e) {
      log.warn("Failed to parse jsonl_entry.parsed during backfill: {}", e.getMessage());
      return null;
    }
  }
}
