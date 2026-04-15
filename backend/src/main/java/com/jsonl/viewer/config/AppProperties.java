package com.jsonl.viewer.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
  private String ingestMode = "file";
  private String sourceId;
  private String jsonlFilePath;
  private String jsonlTimestampField = "timestamp";
  private long ingestPollIntervalMs = 1000;
  private int ingestBatchSize = 500;
  private Duration previewStatementTimeout = Duration.ofSeconds(20);
  private Duration countJobStatementTimeout = Duration.ofMinutes(10);
  private Kafka kafka = new Kafka();

  public String getIngestMode() {
    return ingestMode;
  }

  public void setIngestMode(String ingestMode) {
    this.ingestMode = ingestMode;
  }

  public String getSourceId() {
    return sourceId;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

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

  public Duration getPreviewStatementTimeout() {
    return previewStatementTimeout;
  }

  public void setPreviewStatementTimeout(Duration previewStatementTimeout) {
    this.previewStatementTimeout = previewStatementTimeout;
  }

  public Duration getCountJobStatementTimeout() {
    return countJobStatementTimeout;
  }

  public void setCountJobStatementTimeout(Duration countJobStatementTimeout) {
    this.countJobStatementTimeout = countJobStatementTimeout;
  }

  public Kafka getKafka() {
    return kafka;
  }

  public void setKafka(Kafka kafka) {
    this.kafka = kafka;
  }

  public static class Kafka {
    private String topic;
    private int concurrency = 1;

    public String getTopic() {
      return topic;
    }

    public void setTopic(String topic) {
      this.topic = topic;
    }

    public int getConcurrency() {
      return concurrency;
    }

    public void setConcurrency(int concurrency) {
      this.concurrency = concurrency;
    }
  }
}
