package com.jsonl.viewer.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ingest_state")
public class IngestState {
  @Id
  @Column(name = "file_path", nullable = false)
  private String filePath;

  @Column(name = "byte_offset", nullable = false)
  private long byteOffset;

  @Column(name = "line_no", nullable = false)
  private long lineNo;

  @Column(name = "last_ingested_at")
  private Instant lastIngestedAt;

  @Column(name = "total_count", nullable = false)
  private long totalCount = 0L;

  @Column(name = "parsed_count", nullable = false)
  private long parsedCount = 0L;

  @Column(name = "error_count", nullable = false)
  private long errorCount = 0L;

  @Column(name = "source_revision", nullable = false)
  private long sourceRevision = 0L;

  @Column(name = "indexed_revision", nullable = false)
  private long indexedRevision = 0L;

  @Column(name = "ingest_status", nullable = false)
  private String ingestStatus = "ready";

  public IngestState() {}

  public IngestState(String filePath, long byteOffset, long lineNo, Instant lastIngestedAt) {
    this(filePath, byteOffset, lineNo, lastIngestedAt, 0, 0, 0, 0, 0, "ready");
  }

  public IngestState(
      String filePath,
      long byteOffset,
      long lineNo,
      Instant lastIngestedAt,
      long totalCount,
      long parsedCount,
      long errorCount,
      long sourceRevision,
      long indexedRevision,
      String ingestStatus
  ) {
    this.filePath = filePath;
    this.byteOffset = byteOffset;
    this.lineNo = lineNo;
    this.lastIngestedAt = lastIngestedAt;
    this.totalCount = totalCount;
    this.parsedCount = parsedCount;
    this.errorCount = errorCount;
    this.sourceRevision = sourceRevision;
    this.indexedRevision = indexedRevision;
    this.ingestStatus = ingestStatus;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public long getByteOffset() {
    return byteOffset;
  }

  public void setByteOffset(long byteOffset) {
    this.byteOffset = byteOffset;
  }

  public long getLineNo() {
    return lineNo;
  }

  public void setLineNo(long lineNo) {
    this.lineNo = lineNo;
  }

  public Instant getLastIngestedAt() {
    return lastIngestedAt;
  }

  public void setLastIngestedAt(Instant lastIngestedAt) {
    this.lastIngestedAt = lastIngestedAt;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  public long getParsedCount() {
    return parsedCount;
  }

  public void setParsedCount(long parsedCount) {
    this.parsedCount = parsedCount;
  }

  public long getErrorCount() {
    return errorCount;
  }

  public void setErrorCount(long errorCount) {
    this.errorCount = errorCount;
  }

  public long getSourceRevision() {
    return sourceRevision;
  }

  public void setSourceRevision(long sourceRevision) {
    this.sourceRevision = sourceRevision;
  }

  public long getIndexedRevision() {
    return indexedRevision;
  }

  public void setIndexedRevision(long indexedRevision) {
    this.indexedRevision = indexedRevision;
  }

  public String getIngestStatus() {
    return ingestStatus;
  }

  public void setIngestStatus(String ingestStatus) {
    this.ingestStatus = ingestStatus;
  }
}
