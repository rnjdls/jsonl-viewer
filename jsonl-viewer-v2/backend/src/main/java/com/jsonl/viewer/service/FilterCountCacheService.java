package com.jsonl.viewer.service;

import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.repo.FilterCountCacheEntry;
import com.jsonl.viewer.repo.FilterCountCacheRepository;
import com.jsonl.viewer.repo.JsonlEntryRepository;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FilterCountCacheService {
  public static final String STATUS_PENDING = "pending";
  public static final String STATUS_READY = "ready";

  private static final Logger log = LoggerFactory.getLogger(FilterCountCacheService.class);

  private final FilterCountCacheRepository cacheRepository;
  private final JsonlEntryRepository jsonlEntryRepository;
  private final AppProperties appProperties;
  private final TransactionTemplate transactionTemplate;
  private final ExecutorService workerExecutor;
  private final ConcurrentMap<String, Boolean> inFlightJobs = new ConcurrentHashMap<>();

  public FilterCountCacheService(
      FilterCountCacheRepository cacheRepository,
      JsonlEntryRepository jsonlEntryRepository,
      AppProperties appProperties,
      PlatformTransactionManager transactionManager
  ) {
    this.cacheRepository = cacheRepository;
    this.jsonlEntryRepository = jsonlEntryRepository;
    this.appProperties = appProperties;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.workerExecutor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "filter-count-worker");
      t.setDaemon(true);
      return t;
    });
  }

  public Snapshot submitCountJob(String filePath, long sourceRevision, String requestHash, FilterSql filterSql) {
    Optional<FilterCountCacheEntry> existing = cacheRepository.findByFilePathAndRequestHash(filePath, requestHash);
    FilterCountCacheEntry entry = existing.orElseGet(() -> new FilterCountCacheEntry(filePath, requestHash));

    if (isReadyForRevision(entry, sourceRevision)) {
      return toSnapshot(entry, sourceRevision);
    }

    entry.setSourceRevision(sourceRevision);
    entry.setStatus(STATUS_PENDING);
    cacheRepository.save(entry);

    enqueueSingleFlight(filePath, sourceRevision, requestHash, filterSql);
    return toSnapshot(entry, sourceRevision);
  }

  public Snapshot getSnapshot(String filePath, long sourceRevision, String requestHash) {
    return cacheRepository.findByFilePathAndRequestHash(filePath, requestHash)
        .map(entry -> toSnapshot(entry, sourceRevision))
        .orElse(new Snapshot(STATUS_PENDING, null, null, null));
  }

  private void enqueueSingleFlight(String filePath, long sourceRevision, String requestHash, FilterSql filterSql) {
    String inFlightKey = filePath + "|" + requestHash + "|" + sourceRevision;
    if (inFlightJobs.putIfAbsent(inFlightKey, Boolean.TRUE) != null) {
      return;
    }

    workerExecutor.submit(() -> {
      try {
        executeCountJob(filePath, sourceRevision, requestHash, filterSql);
      } finally {
        inFlightJobs.remove(inFlightKey);
      }
    });
  }

  private void executeCountJob(String filePath, long sourceRevision, String requestHash, FilterSql filterSql) {
    try {
      long matchCount = transactionTemplate.execute(status ->
          jsonlEntryRepository.countMatching(filePath, filterSql, toMillis(appProperties.getCountJobStatementTimeout()))
      );

      transactionTemplate.executeWithoutResult(status -> {
        Optional<FilterCountCacheEntry> current = cacheRepository.findByFilePathAndRequestHash(filePath, requestHash);
        if (current.isEmpty()) {
          return;
        }

        FilterCountCacheEntry entry = current.get();
        if (entry.getSourceRevision() != sourceRevision) {
          return;
        }

        entry.setComputedRevision(sourceRevision);
        entry.setMatchCount(matchCount);
        entry.setStatus(STATUS_READY);
        entry.setLastComputedAt(Instant.now());
        cacheRepository.save(entry);
      });
    } catch (Exception ex) {
      log.warn("Count job failed for {}@{} ({}): {}", filePath, sourceRevision, requestHash, ex.getMessage());
    }
  }

  private boolean isReadyForRevision(FilterCountCacheEntry entry, long sourceRevision) {
    return STATUS_READY.equals(entry.getStatus())
        && entry.getComputedRevision() != null
        && entry.getComputedRevision() == sourceRevision;
  }

  private Snapshot toSnapshot(FilterCountCacheEntry entry, long sourceRevision) {
    boolean ready = isReadyForRevision(entry, sourceRevision);
    return new Snapshot(
        ready ? STATUS_READY : STATUS_PENDING,
        ready ? entry.getMatchCount() : null,
        entry.getComputedRevision(),
        entry.getLastComputedAt()
    );
  }

  private Long toMillis(Duration duration) {
    if (duration == null || duration.isNegative() || duration.isZero()) {
      return null;
    }
    return duration.toMillis();
  }

  @PreDestroy
  public void shutdownExecutor() {
    workerExecutor.shutdownNow();
  }

  public record Snapshot(String status, Long matchCount, Long computedRevision, Instant lastComputedAt) {}
}
