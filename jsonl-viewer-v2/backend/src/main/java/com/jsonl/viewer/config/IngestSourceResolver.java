package com.jsonl.viewer.config;

import org.springframework.stereotype.Component;

@Component
public class IngestSourceResolver {
  private static final String KAFKA_PREFIX = "kafka:";

  private final AppProperties properties;

  public IngestSourceResolver(AppProperties properties) {
    this.properties = properties;
  }

  public IngestMode getIngestMode() {
    return IngestMode.fromRaw(properties.getIngestMode());
  }

  public boolean isFileMode() {
    return getIngestMode() == IngestMode.FILE;
  }

  public boolean isKafkaMode() {
    return getIngestMode() == IngestMode.KAFKA;
  }

  public String getFileReadPath() {
    return normalizeBlank(properties.getJsonlFilePath());
  }

  public String getActiveSourceId() {
    if (isFileMode()) {
      return getFileReadPath();
    }

    String explicitSourceId = normalizeBlank(properties.getSourceId());
    if (explicitSourceId != null) {
      return explicitSourceId;
    }

    AppProperties.Kafka kafka = properties.getKafka();
    String topic = kafka == null ? null : normalizeBlank(kafka.getTopic());
    if (topic == null) {
      return null;
    }
    return KAFKA_PREFIX + topic;
  }

  private String normalizeBlank(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
