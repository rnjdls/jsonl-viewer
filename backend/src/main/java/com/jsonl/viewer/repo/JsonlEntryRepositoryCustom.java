package com.jsonl.viewer.repo;

import java.util.List;

public interface JsonlEntryRepositoryCustom {
  Counts getCounts(String filePath);

  long countMatching(String filePath, FilterSql filterSql);

  List<JsonlEntryRow> preview(String filePath, FilterSql filterSql, long cursorId, int limit);

  record Counts(long total, long parsed, long errors) {}

  record FilterSql(String whereClause, List<Object> params) {}
}
