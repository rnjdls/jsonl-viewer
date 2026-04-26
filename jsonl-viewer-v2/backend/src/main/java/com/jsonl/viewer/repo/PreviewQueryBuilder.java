package com.jsonl.viewer.repo;

import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import java.util.ArrayList;
import java.util.List;

final class PreviewQueryBuilder {
  static final String SORT_DIR_ASC = "asc";
  static final String SORT_DIR_DESC = "desc";
  private static final String BASE_SELECT =
      "SELECT e.id, e.line_no, e.ts, e.parsed->'key', e.parsed->'headers', e.parse_error, " +
          "CASE WHEN e.parse_error IS NOT NULL THEN LEFT(e.raw_line, 500) ELSE NULL END AS raw_snippet, " +
          "CASE WHEN e.parse_error IS NOT NULL THEN LENGTH(e.raw_line) > 500 ELSE NULL END AS raw_truncated " +
          "FROM jsonl_entry e ";

  private PreviewQueryBuilder() {}

  static PreviewQuery build(
      FilterSql filterSql,
      String sortDir,
      PreviewCursor cursor,
      int limit
  ) {
    StringBuilder sql = new StringBuilder(BASE_SELECT)
        .append("JOIN (")
        .append(filterSql.candidateIdsSql())
        .append(") candidate_ids ON candidate_ids.id = e.id ")
        .append("WHERE e.file_path = ?1 ");
    List<Object> queryParams = new ArrayList<>();
    int nextParamIndex = filterSql.params().size() + 2;

    if (cursor != null) {
      nextParamIndex = appendLineNoPredicate(sql, queryParams, nextParamIndex, sortDir, cursor);
    }

    sql.append("ORDER BY ").append(orderBy(sortDir)).append(" ");
    sql.append("LIMIT ?").append(nextParamIndex);
    queryParams.add(limit);

    return new PreviewQuery(sql.toString(), queryParams);
  }

  static PreviewQuery buildTextOnly(
      String sortDir,
      PreviewCursor cursor,
      int limit
  ) {
    StringBuilder sql = new StringBuilder(BASE_SELECT)
        .append("WHERE e.file_path = ?1 ")
        .append("AND e.search_text IS NOT NULL ")
        .append("AND to_tsvector('simple', e.search_text) @@ ")
        .append("plainto_tsquery('simple', regexp_replace(?2, '[^[:alnum:]]+', ' ', 'g')) ");
    List<Object> queryParams = new ArrayList<>();
    int nextParamIndex = 3;

    if (cursor != null) {
      nextParamIndex = appendLineNoPredicate(sql, queryParams, nextParamIndex, sortDir, cursor);
    }

    sql.append("ORDER BY ").append(orderBy(sortDir)).append(" ");
    sql.append("LIMIT ?").append(nextParamIndex);
    queryParams.add(limit);

    return new PreviewQuery(sql.toString(), queryParams);
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

  private static String orderBy(String sortDir) {
    String direction = isAsc(sortDir) ? "ASC" : "DESC";
    return "e.line_no " + direction + ", e.id " + direction;
  }

  private static boolean isAsc(String sortDir) {
    return SORT_DIR_ASC.equals(sortDir);
  }

  record PreviewQuery(String sql, List<Object> params) {}
}
