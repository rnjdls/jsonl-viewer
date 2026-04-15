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

  private PreviewQueryBuilder() {}

  static PreviewQuery build(FilterSql filterSql, String sortBy, String sortDir, PreviewCursor cursor, int limit) {
    StringBuilder sql = new StringBuilder(
        "SELECT e.id, e.line_no, e.ts, e.parsed->'key', e.parsed->'headers', e.parse_error, " +
            "CASE WHEN e.parse_error IS NOT NULL THEN LEFT(e.raw_line, 500) ELSE NULL END AS raw_snippet, " +
            "CASE WHEN e.parse_error IS NOT NULL THEN LENGTH(e.raw_line) > 500 ELSE NULL END AS raw_truncated " +
            "FROM jsonl_entry e " +
            "JOIN (" + filterSql.candidateIdsSql() + ") candidate_ids ON candidate_ids.id = e.id " +
            "WHERE e.file_path = ?1 "
    );
    List<Object> queryParams = new ArrayList<>();
    int nextParamIndex = filterSql.params().size() + 2;

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
      sql.append("AND (e.ts ").append(op).append(" ?").append(nextParamIndex)
          .append(" OR (e.ts = ?").append(nextParamIndex + 1)
          .append(" AND e.id ").append(op).append(" ?").append(nextParamIndex + 2)
          .append(") OR e.ts IS NULL) ");
      queryParams.add(cursor.ts());
      queryParams.add(cursor.ts());
      queryParams.add(cursor.id());
      return nextParamIndex + 3;
    }

    sql.append("AND (e.ts IS NULL AND e.id ").append(asc ? ">" : "<")
        .append(" ?").append(nextParamIndex).append(") ");
    queryParams.add(cursor.id());
    return nextParamIndex + 1;
  }

  private static String orderBy(String sortBy, String sortDir) {
    String direction = isAsc(sortDir) ? "ASC" : "DESC";
    return switch (sortBy) {
      case SORT_BY_ID -> "e.id " + direction;
      case SORT_BY_LINE_NO -> "e.line_no " + direction + ", e.id " + direction;
      case SORT_BY_TIMESTAMP -> "e.ts " + direction + " NULLS LAST, e.id " + direction;
      default -> throw new IllegalArgumentException("Unsupported sortBy: " + sortBy);
    };
  }

  private static boolean isAsc(String sortDir) {
    return SORT_DIR_ASC.equals(sortDir);
  }

  record PreviewQuery(String sql, List<Object> params) {}
}
