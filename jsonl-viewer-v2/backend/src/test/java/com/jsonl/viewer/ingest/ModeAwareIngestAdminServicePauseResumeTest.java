package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.config.IngestSourceResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ModeAwareIngestAdminServicePauseResumeTest {

  @Test
  void pauseAndResumeInFileModeOnlyChangePauseState() {
    AppProperties properties = new AppProperties();
    properties.setIngestMode("file");
    properties.setJsonlFilePath("/tmp/sample.jsonl");
    IngestPauseState pauseState = new IngestPauseState();
    ObjectProvider<KafkaIngestService> kafkaProvider = mockKafkaProvider();

    ModeAwareIngestAdminService service = new ModeAwareIngestAdminService(
        new IngestSourceResolver(properties),
        mock(JsonlIngestService.class),
        kafkaProvider,
        pauseState
    );

    service.pause();
    assertTrue(pauseState.isPaused());
    verify(kafkaProvider, never()).getIfAvailable();

    service.resume();
    assertFalse(pauseState.isPaused());
    verify(kafkaProvider, never()).getIfAvailable();
  }

  @Test
  void pauseAndResumeInKafkaModeControlListenerAndPauseState() {
    AppProperties properties = new AppProperties();
    properties.setIngestMode("kafka");
    AppProperties.Kafka kafka = new AppProperties.Kafka();
    kafka.setTopic("events");
    properties.setKafka(kafka);
    IngestPauseState pauseState = new IngestPauseState();
    KafkaIngestService kafkaIngestService = mock(KafkaIngestService.class);
    ObjectProvider<KafkaIngestService> kafkaProvider = mockKafkaProvider();
    when(kafkaProvider.getIfAvailable()).thenReturn(kafkaIngestService);

    ModeAwareIngestAdminService service = new ModeAwareIngestAdminService(
        new IngestSourceResolver(properties),
        mock(JsonlIngestService.class),
        kafkaProvider,
        pauseState
    );

    service.pause();
    assertTrue(pauseState.isPaused());
    verify(kafkaIngestService).pauseListener();

    service.resume();
    assertFalse(pauseState.isPaused());
    verify(kafkaIngestService).resumeListener();
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<KafkaIngestService> mockKafkaProvider() {
    return (ObjectProvider<KafkaIngestService>) mock(ObjectProvider.class);
  }
}
