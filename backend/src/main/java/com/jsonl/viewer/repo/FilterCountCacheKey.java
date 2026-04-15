package com.jsonl.viewer.repo;

import java.io.Serializable;
import java.util.Objects;

public class FilterCountCacheKey implements Serializable {
  private String filePath;
  private String requestHash;

  public FilterCountCacheKey() {}

  public FilterCountCacheKey(String filePath, String requestHash) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FilterCountCacheKey that)) {
      return false;
    }
    return Objects.equals(filePath, that.filePath) && Objects.equals(requestHash, that.requestHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(filePath, requestHash);
  }
}
