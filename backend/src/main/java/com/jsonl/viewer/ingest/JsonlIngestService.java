package com.jsonl.viewer.ingest;

import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import com.jsonl.viewer.repo.IngestState;
import com.jsonl.viewer.repo.IngestStateRepository;
import com.jsonl.viewer.repo.JsonlEntry;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import jakarta.persistence.EntityManager;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
  private static final int INGEST_READ_BUFFER_SIZE = 16 * 1024;

  private final AppProperties properties;
  private final IngestSourceResolver sourceResolver;
  private final JsonlEntryRepository jsonlEntryRepository;
  private final IngestStateRepository ingestStateRepository;
  private final JsonlEntryParser jsonlEntryParser;
  private final EntityManager entityManager;
  private final ReentrantLock ingestLock = new ReentrantLock();

  public JsonlIngestService(
      AppProperties properties,
      IngestSourceResolver sourceResolver,
      JsonlEntryRepository jsonlEntryRepository,
      IngestStateRepository ingestStateRepository,
      JsonlEntryParser jsonlEntryParser,
      EntityManager entityManager
  ) {
    this.properties = properties;
    this.sourceResolver = sourceResolver;
    this.jsonlEntryRepository = jsonlEntryRepository;
    this.ingestStateRepository = ingestStateRepository;
    this.jsonlEntryParser = jsonlEntryParser;
    this.entityManager = entityManager;
  }

  @Transactional
  @Scheduled(fixedDelayString = "${app.ingest-poll-interval-ms:1000}")
  public void pollFile() {
    if (!sourceResolver.isFileMode()) {
      return;
    }
    ingest(false, false, false);
  }

  @Transactional
  public void reloadFromStart() {
    if (!sourceResolver.isFileMode()) {
      return;
    }
    ingest(true, true, true);
  }

  @Transactional
  public void resetToFileEnd() {
    if (!sourceResolver.isFileMode()) {
      return;
    }
    ingestLock.lock();
    try {
      String filePath = sourceResolver.getFileReadPath();
      String sourceId = sourceResolver.getActiveSourceId();
      if (filePath == null || sourceId == null) return;

      Path path = Path.of(filePath);
      if (!Files.exists(path)) return;

      long size = Files.size(path);
      jsonlEntryRepository.deleteByFilePath(sourceId);
      saveIngestState(sourceId, size, 0, Instant.now());
    } catch (IOException e) {
      log.warn("Failed to reset ingest state for {}: {}", sourceResolver.getFileReadPath(), e.getMessage());
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
      if (!sourceResolver.isFileMode()) return;

      String filePath = sourceResolver.getFileReadPath();
      String sourceId = sourceResolver.getActiveSourceId();
      if (filePath == null || sourceId == null) return;

      Path path = Path.of(filePath);
      if (!Files.exists(path)) {
        log.warn("JSONL file path does not exist: {}", filePath);
        return;
      }

      IngestState state = ingestStateRepository.findById(sourceId)
          .orElse(new IngestState(sourceId, 0, 0, null));

      long offset = state.getByteOffset();
      long lineNo = state.getLineNo();
      long startLineNo = lineNo;

      long targetSize = Files.size(path);
      if (forceReset) {
        jsonlEntryRepository.deleteByFilePath(sourceId);
        offset = 0;
        lineNo = 0;
        startLineNo = 0;
      } else if (targetSize < offset) {
        log.info("File size shrank; resetting ingest state for {}", filePath);
        jsonlEntryRepository.deleteByFilePath(sourceId);
        offset = 0;
        lineNo = 0;
        startLineNo = 0;
      }

      if (!ingestNow && targetSize == offset) {
        return;
      }

      long ingestStartNanos = System.nanoTime();
      List<JsonlEntry> batch = new ArrayList<>();
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      long cursor = offset;
      long newOffset = offset;
      long remaining = Math.max(0, targetSize - offset);
      byte[] readBuffer = new byte[INGEST_READ_BUFFER_SIZE];

      try (InputStream in = new BufferedInputStream(Files.newInputStream(path), INGEST_READ_BUFFER_SIZE)) {
        skipFully(in, offset);
        while (remaining > 0) {
          int bytesToRead = (int) Math.min(readBuffer.length, remaining);
          int bytesRead = in.read(readBuffer, 0, bytesToRead);
          if (bytesRead == -1) break;
          if (bytesRead == 0) continue;

          long chunkStartCursor = cursor;
          cursor += bytesRead;
          remaining -= bytesRead;

          int segmentStart = 0;
          for (int i = 0; i < bytesRead; i++) {
            if (readBuffer[i] != '\n') continue;

            int segmentLength = i - segmentStart;
            if (segmentLength > 0) {
              buffer.write(readBuffer, segmentStart, segmentLength);
            }

            byte[] lineBytes = buffer.toByteArray();
            buffer.reset();

            if (lineBytes.length > 0 && lineBytes[lineBytes.length - 1] == '\r') {
              lineBytes = java.util.Arrays.copyOf(lineBytes, lineBytes.length - 1);
            }

            String rawLine = new String(lineBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!rawLine.isEmpty()) {
              lineNo++;
              batch.add(parseLine(sourceId, lineNo, rawLine));
              if (batch.size() >= properties.getIngestBatchSize()) {
                persistBatch(batch);
                batch.clear();
              }
            }
            newOffset = chunkStartCursor + i + 1;
            segmentStart = i + 1;
          }

          if (segmentStart < bytesRead) {
            buffer.write(readBuffer, segmentStart, bytesRead - segmentStart);
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
        saveIngestState(sourceId, newOffset, lineNo, Instant.now());
        if (log.isDebugEnabled()) {
          long elapsedMs = (System.nanoTime() - ingestStartNanos) / 1_000_000L;
          long bytesIngested = newOffset - offset;
          long linesIngested = lineNo - startLineNo;
          log.debug(
              "Ingest pass advanced state for {}: +{} bytes, +{} lines in {} ms ({} -> {})",
              sourceId,
              bytesIngested,
              linesIngested,
              elapsedMs,
              offset,
              newOffset
          );
        }
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

  private JsonlEntry parseLine(String sourceId, long lineNo, String rawLine) {
    JsonlEntryParseResult parseResult = jsonlEntryParser.parse(rawLine);
    return new JsonlEntry(
        sourceId,
        lineNo,
        rawLine,
        parseResult.parsed(),
        parseResult.parseError(),
        parseResult.ts()
    );
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
