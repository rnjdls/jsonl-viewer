package com.jsonl.viewer.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "jsonl_entry_field_index")
public class JsonlEntryFieldIndex {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "entry_id", nullable = false)
  private long entryId;

  @Column(name = "file_path", nullable = false, columnDefinition = "text")
  private String filePath;

  @Column(name = "field_key", nullable = false, columnDefinition = "text")
  private String fieldKey;

  @Column(name = "field_path", columnDefinition = "text")
  private String fieldPath;

  @Column(name = "value_text", columnDefinition = "text")
  private String valueText;

  @Column(name = "value_ts")
  private Instant valueTs;

  @Column(name = "value_type", nullable = false, columnDefinition = "text")
  private String valueType;

  @Column(name = "is_null", nullable = false)
  private boolean isNull;

  @Column(name = "is_empty", nullable = false)
  private boolean isEmpty;

  public JsonlEntryFieldIndex() {}

  public JsonlEntryFieldIndex(
      long entryId,
      String filePath,
      String fieldKey,
      String fieldPath,
      String valueText,
      Instant valueTs,
      String valueType,
      boolean isNull,
      boolean isEmpty
  ) {
    this.entryId = entryId;
    this.filePath = filePath;
    this.fieldKey = fieldKey;
    this.fieldPath = fieldPath;
    this.valueText = valueText;
    this.valueTs = valueTs;
    this.valueType = valueType;
    this.isNull = isNull;
    this.isEmpty = isEmpty;
  }

  public Long getId() {
    return id;
  }

  public long getEntryId() {
    return entryId;
  }

  public void setEntryId(long entryId) {
    this.entryId = entryId;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getFieldKey() {
    return fieldKey;
  }

  public void setFieldKey(String fieldKey) {
    this.fieldKey = fieldKey;
  }

  public String getFieldPath() {
    return fieldPath;
  }

  public void setFieldPath(String fieldPath) {
    this.fieldPath = fieldPath;
  }

  public String getValueText() {
    return valueText;
  }

  public void setValueText(String valueText) {
    this.valueText = valueText;
  }

  public Instant getValueTs() {
    return valueTs;
  }

  public void setValueTs(Instant valueTs) {
    this.valueTs = valueTs;
  }

  public String getValueType() {
    return valueType;
  }

  public void setValueType(String valueType) {
    this.valueType = valueType;
  }

  public boolean isNull() {
    return isNull;
  }

  public void setNull(boolean aNull) {
    isNull = aNull;
  }

  public boolean isEmpty() {
    return isEmpty;
  }

  public void setEmpty(boolean empty) {
    isEmpty = empty;
  }
}
