package com.jsonl.viewer.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.stereotype.Component;

@Component
public class IngestConfigurationValidator {
  private final IngestSourceResolver sourceResolver;
  private final AppProperties properties;
  private final KafkaProperties kafkaProperties;

  public IngestConfigurationValidator(
      IngestSourceResolver sourceResolver,
      AppProperties properties,
      KafkaProperties kafkaProperties
  ) {
    this.sourceResolver = sourceResolver;
    this.properties = properties;
    this.kafkaProperties = kafkaProperties;
  }

  @PostConstruct
  void validate() {
    if (!sourceResolver.isKafkaMode()) {
      return;
    }

    AppProperties.Kafka kafka = properties.getKafka();
    String topic = kafka == null ? null : normalizeBlank(kafka.getTopic());
    if (topic == null) {
      throw new IllegalStateException("app.kafka.topic is required when app.ingest-mode=kafka");
    }

    List<String> bootstrapServers = kafkaProperties.getBootstrapServers();
    boolean hasBootstrapServers = bootstrapServers != null
        && bootstrapServers.stream().anyMatch(value -> normalizeBlank(value) != null);
    if (!hasBootstrapServers) {
      throw new IllegalStateException(
          "spring.kafka.bootstrap-servers is required when app.ingest-mode=kafka"
      );
    }
  }

  private String normalizeBlank(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
