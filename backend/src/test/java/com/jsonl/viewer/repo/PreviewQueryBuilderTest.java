package com.jsonl.viewer.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import com.jsonl.viewer.repo.PreviewQueryBuilder.PreviewQuery;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewQueryBuilderTest {
  private static final FilterSql BASE_FILTER = new FilterSql(
      "SELECT id FROM jsonl_entry WHERE file_path = ?1",
      List.of()
  );

  @Test
  void buildsLineNoAscQuery() {
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "asc",
        new PreviewCursor("asc", 55L, 11L),
        200
    );

    assertTrue(query.sql().contains("AND (e.line_no > ?2 OR (e.line_no = ?3 AND e.id > ?4))"));
    assertTrue(query.sql().contains("ORDER BY e.line_no ASC, e.id ASC"));
    assertEquals(List.of(55L, 55L, 11L, 200), query.params());
  }

  @Test
  void buildsLineNoDescQuery() {
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "desc",
        new PreviewCursor("desc", 55L, 11L),
        200
    );

    assertTrue(query.sql().contains("AND (e.line_no < ?2 OR (e.line_no = ?3 AND e.id < ?4))"));
    assertTrue(query.sql().contains("ORDER BY e.line_no DESC, e.id DESC"));
    assertEquals(List.of(55L, 55L, 11L, 200), query.params());
  }

  @Test
  void buildsFirstPageQueryWithoutCursor() {
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "asc",
        null,
        200
    );

    assertTrue(query.sql().contains("WHERE e.file_path = ?1"));
    assertTrue(query.sql().contains("ORDER BY e.line_no ASC, e.id ASC"));
    assertEquals(List.of(200), query.params());
  }

  @Test
  void buildsTextOnlyFastPathWithoutCursor() {
    PreviewQuery query = PreviewQueryBuilder.buildTextOnly(
        "asc",
        null,
        100
    );

    assertTrue(query.sql().contains("WHERE e.file_path = ?1"));
    assertTrue(query.sql().contains("e.search_text IS NOT NULL"));
    assertTrue(query.sql().contains("plainto_tsquery('simple', regexp_replace(?2, '[^[:alnum:]]+', ' ', 'g'))"));
    assertTrue(query.sql().contains("ORDER BY e.line_no ASC, e.id ASC"));
    assertEquals(List.of(100), query.params());
  }

  @Test
  void buildsTextOnlyFastPathWithCursor() {
    PreviewQuery query = PreviewQueryBuilder.buildTextOnly(
        "desc",
        new PreviewCursor("desc", 77L, 15L),
        100
    );

    assertTrue(query.sql().contains("AND (e.line_no < ?3 OR (e.line_no = ?4 AND e.id < ?5))"));
    assertTrue(query.sql().contains("ORDER BY e.line_no DESC, e.id DESC"));
    assertEquals(List.of(77L, 77L, 15L, 100), query.params());
  }
}
