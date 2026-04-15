package com.jsonl.viewer.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JsonlEntryRepositoryCustom {
  long countMatching(String filePath, FilterSql filterSql, Long statementTimeoutMs);

  List<JsonlEntryRow> preview(
      String filePath,
      FilterSql filterSql,
      String sortBy,
      String sortDir,
      String timestampFieldPath,
      PreviewCursor cursor,
      int limit,
      Long statementTimeoutMs
  );

  Optional<JsonlEntryDetailRow> findEntryDetail(String filePath, long id);

  Optional<String> findRawLine(String filePath, long id);

  record FilterSql(String candidateIdsSql, List<Object> params) {}

  record PreviewCursor(String sortBy, String sortDir, long id, Long lineNo, Instant ts, String tsFieldPath) {}
}
