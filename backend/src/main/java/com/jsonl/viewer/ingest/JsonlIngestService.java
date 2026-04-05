package com.jsonl.viewer.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntry;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JsonlIngestService {
  private static final Logger log = LoggerFactory.getLogger(JsonlIngestService.class);

  private final AppProperties properties;
  private final JsonlEntryRepository jsonlEntryRepository;
  private final IngestStateRepository ingestStateRepository;
  private final ObjectMapper objectMapper;
  private final EntityManager entityManager;
  private final ReentrantLock ingestLock = new ReentrantLock();

  public JsonlIngestService(
      AppProperties properties,
      JsonlEntryRepository jsonlEntryRepository,
      IngestStateRepository ingestStateRepository,
      ObjectMapper objectMapper,
      EntityManager entityManager
  ) {
    this.properties = properties;
    this.jsonlEntryRepository = jsonlEntryRepository;
    this.ingestStateRepository = ingestStateRepository;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
  }

  @Transactional
  @Scheduled(fixedDelayString = "${app.ingest-poll-interval-ms:1000}")
  public void pollFile() {
    ingest(false, false, false);
  }

  @Transactional
  public void reloadFromStart() {
    ingest(true, true, true);
  }

  @Transactional
  public void resetToFileEnd() {
    ingestLock.lock();
    try {
      String filePath = properties.getJsonlFilePath();
      if (filePath == null || filePath.isBlank()) return;

      Path path = Path.of(filePath);
      if (!Files.exists(path)) return;

      long size = Files.size(path);
      jsonlEntryRepository.deleteByFilePath(filePath);
      saveIngestState(filePath, size, 0, Instant.now());
    } catch (IOException e) {
      log.warn("Failed to reset ingest state for {}: {}", properties.getJsonlFilePath(), e.getMessage());
    } finally {
      ingestLock.unlock();
    }
  }

  private void ingest(boolean forceReset, boolean ingestNow, boolean waitForLock) {
    if (waitForLock) {
      ingestLock.lock();
    } else if (!ingestLock.tryLock()) {
      return;
    }

    try {
      String filePath = properties.getJsonlFilePath();
      if (filePath == null || filePath.isBlank()) return;

      Path path = Path.of(filePath);
      if (!Files.exists(path)) {
        log.warn("JSONL file path does not exist: {}", filePath);
        return;
      }

      IngestState state = ingestStateRepository.findById(filePath)
          .orElse(new IngestState(filePath, 0, 0, null));

      long offset = state.getByteOffset();
      long lineNo = state.getLineNo();

      long fileSize = Files.size(path);
      if (forceReset) {
        jsonlEntryRepository.deleteByFilePath(filePath);
        offset = 0;
        lineNo = 0;
      } else if (fileSize < offset) {
        log.info("File size shrank; resetting ingest state for {}", filePath);
        jsonlEntryRepository.deleteByFilePath(filePath);
        offset = 0;
        lineNo = 0;
      }

      if (!ingestNow && fileSize == offset) {
        return;
      }

      List<JsonlEntry> batch = new ArrayList<>();
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      long cursor = offset;
      long newOffset = offset;

      try (InputStream in = Files.newInputStream(path)) {
        skipFully(in, offset);
        int b;
        while ((b = in.read()) != -1) {
          cursor++;
          if (b == '\n') {
            byte[] lineBytes = buffer.toByteArray();
            buffer.reset();

            if (lineBytes.length > 0 && lineBytes[lineBytes.length - 1] == '\r') {
              lineBytes = java.util.Arrays.copyOf(lineBytes, lineBytes.length - 1);
            }

            String rawLine = new String(lineBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!rawLine.isEmpty()) {
              lineNo++;
              batch.add(parseLine(filePath, lineNo, rawLine));
              if (batch.size() >= properties.getIngestBatchSize()) {
                persistBatch(batch);
                batch.clear();
              }
            }
            newOffset = cursor;
          } else {
            buffer.write(b);
          }
        }
      }

      if (!batch.isEmpty()) {
        persistBatch(batch);
        batch.clear();
      }

      if (buffer.size() > 0) {
        // partial line: roll back to the start of the incomplete line
        newOffset = cursor - buffer.size();
      } else {
        newOffset = cursor;
      }

      if (newOffset != offset) {
        saveIngestState(filePath, newOffset, lineNo, Instant.now());
      }
    } catch (Exception e) {
      log.warn("Ingest failed: {}", e.getMessage());
    } finally {
      ingestLock.unlock();
    }
  }

  private void persistBatch(List<JsonlEntry> batch) {
    if (batch.isEmpty()) return;

    for (JsonlEntry entry : batch) {
      entityManager.persist(entry);
    }

    entityManager.flush();
    entityManager.clear();
  }

  private void saveIngestState(String filePath, long byteOffset, long lineNo, Instant lastIngestedAt) {
    IngestState state = ingestStateRepository.findById(filePath)
        .orElse(new IngestState(filePath, 0, 0, null));
    state.setByteOffset(byteOffset);
    state.setLineNo(lineNo);
    state.setLastIngestedAt(lastIngestedAt);
    ingestStateRepository.save(state);
  }

  private JsonlEntry parseLine(String filePath, long lineNo, String rawLine) {
    try {
      JsonNode node = objectMapper.readTree(rawLine);
      Base64HeadersDecoder.decodeRootHeaders(node);
      Instant ts = extractTimestamp(node);
      return new JsonlEntry(filePath, lineNo, rawLine, node, null, ts);
    } catch (Exception e) {
      return new JsonlEntry(filePath, lineNo, rawLine, null, e.getMessage(), null);
    }
  }

  private Instant extractTimestamp(JsonNode node) {
    if (node == null || !node.isObject()) return null;

    String field = properties.getJsonlTimestampField();
    if (field == null || field.isBlank()) return null;

    JsonNode cursor = node;
    for (String part : field.split("\\.")) {
      if (cursor == null) return null;
      cursor = cursor.get(part);
    }
    if (cursor == null || cursor.isNull()) return null;

    if (cursor.isNumber()) {
      long value = cursor.asLong();
      if (value > 1_000_000_000_000L) {
        return Instant.ofEpochMilli(value);
      }
      return Instant.ofEpochSecond(value);
    }

    if (cursor.isTextual()) {
      String text = cursor.asText();
      try {
        return Instant.parse(text);
      } catch (DateTimeParseException ignored) {
        try {
          return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored2) {
          return null;
        }
      }
    }

    return null;
  }

  private void skipFully(InputStream in, long bytes) throws IOException {
    long remaining = bytes;
    while (remaining > 0) {
      long skipped = in.skip(remaining);
      if (skipped <= 0) {
        if (in.read() == -1) break;
        skipped = 1;
      }
      remaining -= skipped;
    }
  }
}
