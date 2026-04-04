package com.jsonl.viewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
  private String jsonlFilePath;
  private String jsonlTimestampField = "timestamp";
  private long ingestPollIntervalMs = 1000;
  private int ingestBatchSize = 500;

  public String getJsonlFilePath() {
    return jsonlFilePath;
  }

  public void setJsonlFilePath(String jsonlFilePath) {
    this.jsonlFilePath = jsonlFilePath;
  }

  public String getJsonlTimestampField() {
    return jsonlTimestampField;
  }

  public void setJsonlTimestampField(String jsonlTimestampField) {
    this.jsonlTimestampField = jsonlTimestampField;
  }

  public long getIngestPollIntervalMs() {
    return ingestPollIntervalMs;
  }

  public void setIngestPollIntervalMs(long ingestPollIntervalMs) {
    this.ingestPollIntervalMs = ingestPollIntervalMs;
  }

  public int getIngestBatchSize() {
    return ingestBatchSize;
  }

  public void setIngestBatchSize(int ingestBatchSize) {
    this.ingestBatchSize = ingestBatchSize;
  }
}
