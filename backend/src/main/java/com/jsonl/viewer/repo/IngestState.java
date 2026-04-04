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

  public IngestState() {}

  public IngestState(String filePath, long byteOffset, long lineNo, Instant lastIngestedAt) {
    this.filePath = filePath;
    this.byteOffset = byteOffset;
    this.lineNo = lineNo;
    this.lastIngestedAt = lastIngestedAt;
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
}
