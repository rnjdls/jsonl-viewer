package com.jsonl.viewer.ingest;

public interface IngestAdminService {
  void reset();

  void reload();

  void pause();

  void resume();
}
