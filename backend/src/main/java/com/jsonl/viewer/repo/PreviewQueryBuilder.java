package com.jsonl.viewer.repo;

import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import java.util.ArrayList;
import java.util.List;

final class PreviewQueryBuilder {
  static final String SORT_BY_TIMESTAMP = "timestamp";
  static final String SORT_BY_LINE_NO = "lineNo";
  static final String SORT_BY_ID = "id";
  static final String SORT_DIR_ASC = "asc";
  static final String SORT_DIR_DESC = "desc";
  private static final String SORT_TS_COLUMN = "ts_idx.value_ts";

  private PreviewQueryBuilder() {}

  static PreviewQuery build(
      FilterSql filterSql,
      String sortBy,
      String sortDir,
      String timestampFieldPath,
      PreviewCursor cursor,
      int limit
  ) {
    boolean sortByTimestamp = SORT_BY_TIMESTAMP.equals(sortBy);
    String selectTimestampColumn = sortByTimestamp ? SORT_TS_COLUMN : "e.ts";

    StringBuilder sql = new StringBuilder(
        "SELECT e.id, e.line_no, " + selectTimestampColumn + ", e.parsed->'key', e.parsed->'headers', e.parse_error, " +
            "CASE WHEN e.parse_error IS NOT NULL THEN LEFT(e.raw_line, 500) ELSE NULL END AS raw_snippet, " +
            "CASE WHEN e.parse_error IS NOT NULL THEN LENGTH(e.raw_line) > 500 ELSE NULL END AS raw_truncated " +
            "FROM jsonl_entry e " +
            "JOIN (" + filterSql.candidateIdsSql() + ") candidate_ids ON candidate_ids.id = e.id "
    );
    List<Object> queryParams = new ArrayList<>();
    int nextParamIndex = filterSql.params().size() + 2;

    if (sortByTimestamp) {
      if (timestampFieldPath == null || timestampFieldPath.isBlank()) {
        throw new IllegalArgumentException("timestampFieldPath is required for timestamp sorting");
      }
      sql.append("LEFT JOIN (" +
          "SELECT entry_id, MAX(value_ts) AS value_ts " +
          "FROM jsonl_entry_field_index " +
          "WHERE file_path = ?1 AND field_path = ?").append(nextParamIndex)
          .append(" GROUP BY entry_id" +
              ") ts_idx ON ts_idx.entry_id = e.id ");
      queryParams.add(timestampFieldPath);
      nextParamIndex++;
    }

    sql.append("WHERE e.file_path = ?1 ");

    if (cursor != null) {
      nextParamIndex = appendCursorPredicate(sql, queryParams, nextParamIndex, sortBy, sortDir, cursor);
    }

    sql.append("ORDER BY ").append(orderBy(sortBy, sortDir)).append(" ");
    sql.append("LIMIT ?").append(nextParamIndex);
    queryParams.add(limit);

    return new PreviewQuery(sql.toString(), queryParams);
  }

  private static int appendCursorPredicate(
      StringBuilder sql,
      List<Object> queryParams,
      int nextParamIndex,
      String sortBy,
      String sortDir,
      PreviewCursor cursor
  ) {
    switch (sortBy) {
      case SORT_BY_ID:
        sql.append("AND e.id ").append(isAsc(sortDir) ? ">" : "<").append(" ?").append(nextParamIndex).append(" ");
        queryParams.add(cursor.id());
        return nextParamIndex + 1;
      case SORT_BY_LINE_NO:
        return appendLineNoPredicate(sql, queryParams, nextParamIndex, sortDir, cursor);
      case SORT_BY_TIMESTAMP:
        return appendTimestampPredicate(sql, queryParams, nextParamIndex, sortDir, cursor);
      default:
        throw new IllegalArgumentException("Unsupported sortBy: " + sortBy);
    }
  }

  private static int appendLineNoPredicate(
      StringBuilder sql,
      List<Object> queryParams,
      int nextParamIndex,
      String sortDir,
      PreviewCursor cursor
  ) {
    String op = isAsc(sortDir) ? ">" : "<";
    sql.append("AND (e.line_no ").append(op).append(" ?").append(nextParamIndex)
        .append(" OR (e.line_no = ?").append(nextParamIndex + 1)
        .append(" AND e.id ").append(op).append(" ?").append(nextParamIndex + 2).append(")) ");
    queryParams.add(cursor.lineNo());
    queryParams.add(cursor.lineNo());
    queryParams.add(cursor.id());
    return nextParamIndex + 3;
  }

  private static int appendTimestampPredicate(
      StringBuilder sql,
      List<Object> queryParams,
      int nextParamIndex,
      String sortDir,
      PreviewCursor cursor
  ) {
    boolean asc = isAsc(sortDir);
    if (cursor.ts() != null) {
      String op = asc ? ">" : "<";
      sql.append("AND (").append(SORT_TS_COLUMN).append(" ").append(op).append(" ?").append(nextParamIndex)
          .append(" OR (").append(SORT_TS_COLUMN).append(" = ?").append(nextParamIndex + 1)
          .append(" AND e.id ").append(op).append(" ?").append(nextParamIndex + 2)
          .append(") OR ").append(SORT_TS_COLUMN).append(" IS NULL) ");
      queryParams.add(cursor.ts());
      queryParams.add(cursor.ts());
      queryParams.add(cursor.id());
      return nextParamIndex + 3;
    }

    sql.append("AND (").append(SORT_TS_COLUMN).append(" IS NULL AND e.id ").append(asc ? ">" : "<")
        .append(" ?").append(nextParamIndex).append(") ");
    queryParams.add(cursor.id());
    return nextParamIndex + 1;
  }

  private static String orderBy(String sortBy, String sortDir) {
    String direction = isAsc(sortDir) ? "ASC" : "DESC";
    return switch (sortBy) {
      case SORT_BY_ID -> "e.id " + direction;
      case SORT_BY_LINE_NO -> "e.line_no " + direction + ", e.id " + direction;
      case SORT_BY_TIMESTAMP -> SORT_TS_COLUMN + " " + direction + " NULLS LAST, e.id " + direction;
      default -> throw new IllegalArgumentException("Unsupported sortBy: " + sortBy);
    };
  }

  private static boolean isAsc(String sortDir) {
    return SORT_DIR_ASC.equals(sortDir);
  }

  record PreviewQuery(String sql, List<Object> params) {}
}
