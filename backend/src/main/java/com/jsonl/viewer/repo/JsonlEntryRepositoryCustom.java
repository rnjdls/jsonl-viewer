package com.jsonl.viewer.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JsonlEntryRepositoryCustom {
  Counts getCounts(String filePath);

  long countMatching(String filePath, FilterSql filterSql);

  List<JsonlEntryRow> preview(
      String filePath,
      FilterSql filterSql,
      String sortBy,
      String sortDir,
      PreviewCursor cursor,
      int limit
  );

  Optional<JsonlEntryDetailRow> findEntryDetail(String filePath, long id);

  Optional<String> findRawLine(String filePath, long id);

  record Counts(long total, long parsed, long errors) {}

  record FilterSql(String whereClause, List<Object> params) {}

  record PreviewCursor(String sortBy, String sortDir, long id, Long lineNo, Instant ts) {}
}
