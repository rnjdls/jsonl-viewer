package com.jsonl.viewer.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "filter_count_cache")
@IdClass(FilterCountCacheKey.class)
public class FilterCountCacheEntry {
  @Id
  @Column(name = "file_path", nullable = false, columnDefinition = "text")
  private String filePath;

  @Id
  @Column(name = "request_hash", nullable = false, columnDefinition = "text")
  private String requestHash;

  @Column(name = "source_revision", nullable = false)
  private long sourceRevision;

  @Column(name = "computed_revision")
  private Long computedRevision;

  @Column(name = "match_count")
  private Long matchCount;

  @Column(name = "status", nullable = false, columnDefinition = "text")
  private String status = "pending";

  @Column(name = "last_computed_at")
  private Instant lastComputedAt;

  public FilterCountCacheEntry() {}

  public FilterCountCacheEntry(String filePath, String requestHash) {
    this.filePath = filePath;
    this.requestHash = requestHash;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public void setRequestHash(String requestHash) {
    this.requestHash = requestHash;
  }

  public long getSourceRevision() {
    return sourceRevision;
  }

  public void setSourceRevision(long sourceRevision) {
    this.sourceRevision = sourceRevision;
  }

  public Long getComputedRevision() {
    return computedRevision;
  }

  public void setComputedRevision(Long computedRevision) {
    this.computedRevision = computedRevision;
  }

  public Long getMatchCount() {
    return matchCount;
  }

  public void setMatchCount(Long matchCount) {
    this.matchCount = matchCount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getLastComputedAt() {
    return lastComputedAt;
  }

  public void setLastComputedAt(Instant lastComputedAt) {
    this.lastComputedAt = lastComputedAt;
  }
}
