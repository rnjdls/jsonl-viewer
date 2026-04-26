package com.jsonl.viewer.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

class IngestConfigurationValidatorTest {

  @Test
  void validateRejectsNonPositiveByteCap() {
    AppProperties properties = new AppProperties();
    properties.setIngestMaxBytesPerPass(0);

    IngestConfigurationValidator validator = new IngestConfigurationValidator(
        new IngestSourceResolver(properties),
        properties,
        new KafkaProperties()
    );

    assertThrows(IllegalStateException.class, validator::validate);
  }

  @Test
  void validateAllowsFileModeWithoutKafkaSettings() {
    AppProperties properties = new AppProperties();

    IngestConfigurationValidator validator = new IngestConfigurationValidator(
        new IngestSourceResolver(properties),
        properties,
        new KafkaProperties()
    );

    assertDoesNotThrow(validator::validate);
  }
}
