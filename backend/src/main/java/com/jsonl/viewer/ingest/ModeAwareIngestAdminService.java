package com.jsonl.viewer.ingest;

import com.jsonl.viewer.config.IngestSourceResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ModeAwareIngestAdminService implements IngestAdminService {
  private final IngestSourceResolver sourceResolver;
  private final JsonlIngestService fileIngestService;
  private final ObjectProvider<KafkaIngestService> kafkaIngestServiceProvider;

  public ModeAwareIngestAdminService(
      IngestSourceResolver sourceResolver,
      JsonlIngestService fileIngestService,
      ObjectProvider<KafkaIngestService> kafkaIngestServiceProvider
  ) {
    this.sourceResolver = sourceResolver;
    this.fileIngestService = fileIngestService;
    this.kafkaIngestServiceProvider = kafkaIngestServiceProvider;
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

  private KafkaIngestService requireKafkaIngestService() {
    KafkaIngestService service = kafkaIngestServiceProvider.getIfAvailable();
    if (service == null) {
      throw new IllegalStateException("Kafka ingest service is unavailable while app.ingest-mode=kafka");
    }
    return service;
  }
}
