package com.jsonl.viewer.ingest;

import com.jsonl.viewer.config.IngestSourceResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ModeAwareIngestAdminService implements IngestAdminService {
  private final IngestSourceResolver sourceResolver;
  private final JsonlIngestService fileIngestService;
  private final ObjectProvider<KafkaIngestService> kafkaIngestServiceProvider;
  private final IngestPauseState pauseState;

  public ModeAwareIngestAdminService(
      IngestSourceResolver sourceResolver,
      JsonlIngestService fileIngestService,
      ObjectProvider<KafkaIngestService> kafkaIngestServiceProvider,
      IngestPauseState pauseState
  ) {
    this.sourceResolver = sourceResolver;
    this.fileIngestService = fileIngestService;
    this.kafkaIngestServiceProvider = kafkaIngestServiceProvider;
    this.pauseState = pauseState;
  }

  @Override
  public void reset() {
    if (sourceResolver.isFileMode()) {
      fileIngestService.resetToFileEnd();
      return;
    }
    requireKafkaIngestService().resetToEnd();
  }

  @Override
  public void reload() {
    if (sourceResolver.isFileMode()) {
      fileIngestService.reloadFromStart();
      return;
    }
    requireKafkaIngestService().reloadFromBeginning();
  }

  @Override
  public void pause() {
    pauseState.pause();
    if (!sourceResolver.isFileMode()) {
      requireKafkaIngestService().pauseListener();
    }
  }

  @Override
  public void resume() {
    pauseState.resume();
    if (!sourceResolver.isFileMode()) {
      requireKafkaIngestService().resumeListener();
    }
  }

  private KafkaIngestService requireKafkaIngestService() {
    KafkaIngestService service = kafkaIngestServiceProvider.getIfAvailable();
    if (service == null) {
      throw new IllegalStateException("Kafka ingest service is unavailable while app.ingest-mode=kafka");
    }
    return service;
  }
}
