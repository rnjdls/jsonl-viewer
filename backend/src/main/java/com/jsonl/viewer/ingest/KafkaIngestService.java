package com.jsonl.viewer.ingest;

import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntry;
import com.jsonl.viewer.repo.JsonlEntryFieldIndex;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.ingest-mode", havingValue = "kafka")
public class KafkaIngestService {
  public static final String LISTENER_ID = "jsonlKafkaIngestListener";
  private static final Logger log = LoggerFactory.getLogger(KafkaIngestService.class);
  private static final Duration ASSIGNMENT_POLL_TIMEOUT = Duration.ofMillis(200);
  private static final int ASSIGNMENT_POLL_ATTEMPTS = 30;

  private final AppProperties properties;
  private final IngestSourceResolver sourceResolver;
  private final JsonlEntryRepository jsonlEntryRepository;
  private final IngestStateRepository ingestStateRepository;
  private final JsonlEntryParser jsonlEntryParser;
  private final JsonFieldIndexExtractor jsonFieldIndexExtractor;
  private final EntityManager entityManager;
  private final ConsumerFactory<String, String> consumerFactory;
  private final KafkaListenerEndpointRegistry listenerRegistry;

  public KafkaIngestService(
      AppProperties properties,
      IngestSourceResolver sourceResolver,
      JsonlEntryRepository jsonlEntryRepository,
      IngestStateRepository ingestStateRepository,
      JsonlEntryParser jsonlEntryParser,
      JsonFieldIndexExtractor jsonFieldIndexExtractor,
      EntityManager entityManager,
      ConsumerFactory<String, String> consumerFactory,
      KafkaListenerEndpointRegistry listenerRegistry
  ) {
    this.properties = properties;
    this.sourceResolver = sourceResolver;
    this.jsonlEntryRepository = jsonlEntryRepository;
    this.ingestStateRepository = ingestStateRepository;
    this.jsonlEntryParser = jsonlEntryParser;
    this.jsonFieldIndexExtractor = jsonFieldIndexExtractor;
    this.entityManager = entityManager;
    this.consumerFactory = consumerFactory;
    this.listenerRegistry = listenerRegistry;
  }

  @Transactional
  @KafkaListener(
      id = LISTENER_ID,
      topics = "${app.kafka.topic}",
      concurrency = "${app.kafka.concurrency:1}"
  )
  public void ingestBatch(List<ConsumerRecord<String, String>> records) {
    String sourceId = sourceResolver.getActiveSourceId();
    if (sourceId == null || records == null || records.isEmpty()) {
      return;
    }

    IngestState state = ingestStateRepository.findById(sourceId)
        .orElse(new IngestState(sourceId, 0, 0, null));

    long lineNo = state.getLineNo();
    long totalCount = state.getTotalCount();
    long parsedCount = state.getParsedCount();
    long errorCount = state.getErrorCount();

    List<JsonlEntry> batch = new ArrayList<>();
    for (ConsumerRecord<String, String> record : records) {
      String rawLine = record.value();
      if (rawLine == null) {
        continue;
      }
      rawLine = rawLine.trim();
      if (rawLine.isEmpty()) {
        continue;
      }

      lineNo++;
      JsonlEntryParseResult parseResult = jsonlEntryParser.parse(rawLine);
      JsonlEntry entry = new JsonlEntry(
          sourceId,
          lineNo,
          rawLine,
          parseResult.parsed(),
          parseResult.parseError(),
          parseResult.ts()
      );
      batch.add(entry);
      totalCount++;
      if (entry.getParsed() != null) {
        parsedCount++;
      }
      if (entry.getParseError() != null) {
        errorCount++;
      }
    }

    if (!batch.isEmpty()) {
      persistBatch(batch);
      state.setByteOffset(0);
      state.setLineNo(lineNo);
      state.setLastIngestedAt(Instant.now());
      state.setTotalCount(totalCount);
      state.setParsedCount(parsedCount);
      state.setErrorCount(errorCount);
      long nextRevision = state.getSourceRevision() + 1;
      state.setSourceRevision(nextRevision);
      state.setIndexedRevision(nextRevision);
      state.setIngestStatus("ready");
      ingestStateRepository.save(state);
    }
  }

  @Transactional
  public void resetToEnd() {
    resetKafkaSource(true);
  }

  @Transactional
  public void reloadFromBeginning() {
    resetKafkaSource(false);
  }

  private void resetKafkaSource(boolean seekToEnd) {
    String sourceId = sourceResolver.getActiveSourceId();
    AppProperties.Kafka kafka = properties.getKafka();
    String topic = kafka == null ? null : normalizeBlank(kafka.getTopic());
    if (sourceId == null || topic == null) {
      return;
    }

    MessageListenerContainer container = listenerRegistry.getListenerContainer(LISTENER_ID);
    boolean restartContainer = container != null && container.isRunning();
    stopContainer(container);
    try {
      seekAndCommit(topic, seekToEnd);
      IngestState state = ingestStateRepository.findById(sourceId)
          .orElse(new IngestState(sourceId, 0, 0, null));
      jsonlEntryRepository.deleteByFilePath(sourceId);
      state.setByteOffset(0);
      state.setLineNo(0);
      state.setLastIngestedAt(Instant.now());
      state.setTotalCount(0);
      state.setParsedCount(0);
      state.setErrorCount(0);
      long nextRevision = state.getSourceRevision() + 1;
      state.setSourceRevision(nextRevision);
      state.setIndexedRevision(nextRevision);
      state.setIngestStatus("ready");
      ingestStateRepository.save(state);
    } finally {
      if (restartContainer && container != null) {
        container.start();
      }
    }
  }

  private void seekAndCommit(String topic, boolean seekToEnd) {
    try (Consumer<String, String> consumer = consumerFactory.createConsumer()) {
      consumer.subscribe(List.of(topic));
      Set<TopicPartition> assignments = waitForAssignments(consumer);
      if (assignments.isEmpty()) {
        throw new IllegalStateException("Failed to get Kafka partition assignments for topic " + topic);
      }

      if (seekToEnd) {
        consumer.seekToEnd(assignments);
      } else {
        consumer.seekToBeginning(assignments);
      }

      Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
      for (TopicPartition assignment : assignments) {
        offsets.put(assignment, new OffsetAndMetadata(consumer.position(assignment)));
      }
      consumer.commitSync(offsets);
    }
  }

  private Set<TopicPartition> waitForAssignments(Consumer<String, String> consumer) {
    for (int i = 0; i < ASSIGNMENT_POLL_ATTEMPTS; i++) {
      consumer.poll(ASSIGNMENT_POLL_TIMEOUT);
      Set<TopicPartition> assignments = consumer.assignment();
      if (!assignments.isEmpty()) {
        return assignments;
      }
    }
    return Set.of();
  }

  private void stopContainer(MessageListenerContainer container) {
    if (container == null || !container.isRunning()) {
      return;
    }

    CountDownLatch latch = new CountDownLatch(1);
    container.stop(latch::countDown);
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        log.warn("Timed out waiting for Kafka listener container to stop");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while stopping Kafka listener container", ex);
    }
  }

  private void persistBatch(List<JsonlEntry> batch) {
    for (JsonlEntry entry : batch) {
      entityManager.persist(entry);
    }
    entityManager.flush();

    for (JsonlEntry entry : batch) {
      if (entry.getId() == null || entry.getParsed() == null) {
        continue;
      }
      List<JsonlEntryFieldIndex> indexRows = jsonFieldIndexExtractor.extract(
          entry.getFilePath(),
          entry.getId(),
          entry.getParsed()
      );
      for (JsonlEntryFieldIndex indexRow : indexRows) {
        entityManager.persist(indexRow);
      }
    }

    entityManager.flush();
    entityManager.clear();
  }

  private String normalizeBlank(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
