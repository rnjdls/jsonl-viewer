package com.jsonl.viewer.repo;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "jsonl_entry",
    indexes = {
        @Index(name = "jsonl_entry_file_id_idx", columnList = "file_path,id"),
        @Index(name = "jsonl_entry_file_ts_idx", columnList = "file_path,ts")
    }
)
public class JsonlEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "file_path", nullable = false)
  private String filePath;

  @Column(name = "line_no", nullable = false)
  private long lineNo;

  @Column(name = "raw_line", nullable = false)
  private String rawLine;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "parsed", columnDefinition = "jsonb")
  private JsonNode parsed;

  @Column(name = "parse_error")
  private String parseError;

  @Column(name = "ts")
  private Instant ts;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public JsonlEntry() {}

  public JsonlEntry(
      String filePath,
      long lineNo,
      String rawLine,
      JsonNode parsed,
      String parseError,
      Instant ts
  ) {
    this.filePath = filePath;
    this.lineNo = lineNo;
    this.rawLine = rawLine;
    this.parsed = parsed;
    this.parseError = parseError;
    this.ts = ts;
  }

  public Long getId() {
    return id;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public long getLineNo() {
    return lineNo;
  }

  public void setLineNo(long lineNo) {
    this.lineNo = lineNo;
  }

  public String getRawLine() {
    return rawLine;
  }

  public void setRawLine(String rawLine) {
    this.rawLine = rawLine;
  }

  public JsonNode getParsed() {
    return parsed;
  }

  public void setParsed(JsonNode parsed) {
    this.parsed = parsed;
  }

  public String getParseError() {
    return parseError;
  }

  public void setParseError(String parseError) {
    this.parseError = parseError;
  }

  public Instant getTs() {
    return ts;
  }

  public void setTs(Instant ts) {
    this.ts = ts;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
