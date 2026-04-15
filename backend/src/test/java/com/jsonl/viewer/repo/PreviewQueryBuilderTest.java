package com.jsonl.viewer.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import com.jsonl.viewer.repo.PreviewQueryBuilder.PreviewQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewQueryBuilderTest {
  private static final FilterSql BASE_FILTER = new FilterSql(
      "SELECT id FROM jsonl_entry WHERE file_path = ?1",
      List.of()
  );

  @Test
  void buildsIdAscQuery() {
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "id",
        "asc",
        new PreviewCursor("id", "asc", 100L, null, null),
        200
    );

    assertTrue(query.sql().contains("AND e.id > ?2"));
    assertTrue(query.sql().contains("ORDER BY e.id ASC"));
    assertEquals(List.of(100L, 200), query.params());
  }

  @Test
  void buildsIdDescQuery() {
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "id",
        "desc",
        new PreviewCursor("id", "desc", 100L, null, null),
        200
    );

    assertTrue(query.sql().contains("AND e.id < ?2"));
    assertTrue(query.sql().contains("ORDER BY e.id DESC"));
    assertEquals(List.of(100L, 200), query.params());
  }

  @Test
  void buildsLineNoAscQuery() {
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "lineNo",
        "asc",
        new PreviewCursor("lineNo", "asc", 11L, 55L, null),
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
        "lineNo",
        "desc",
        new PreviewCursor("lineNo", "desc", 11L, 55L, null),
        200
    );

    assertTrue(query.sql().contains("AND (e.line_no < ?2 OR (e.line_no = ?3 AND e.id < ?4))"));
    assertTrue(query.sql().contains("ORDER BY e.line_no DESC, e.id DESC"));
    assertEquals(List.of(55L, 55L, 11L, 200), query.params());
  }

  @Test
  void buildsTimestampAscQueryWithNonNullTs() {
    Instant ts = Instant.parse("2026-04-06T13:23:58.807619673Z");
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "timestamp",
        "asc",
        new PreviewCursor("timestamp", "asc", 11L, null, ts),
        200
    );

    assertTrue(query.sql().contains("AND (e.ts > ?2 OR (e.ts = ?3 AND e.id > ?4) OR e.ts IS NULL)"));
    assertTrue(query.sql().contains("ORDER BY e.ts ASC NULLS LAST, e.id ASC"));
    assertEquals(List.of(ts, ts, 11L, 200), query.params());
  }

  @Test
  void buildsTimestampAscQueryWithNullTs() {
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "timestamp",
        "asc",
        new PreviewCursor("timestamp", "asc", 11L, null, null),
        200
    );

    assertTrue(query.sql().contains("AND (e.ts IS NULL AND e.id > ?2)"));
    assertTrue(query.sql().contains("ORDER BY e.ts ASC NULLS LAST, e.id ASC"));
    assertEquals(List.of(11L, 200), query.params());
  }

  @Test
  void buildsTimestampDescQueryWithNonNullTs() {
    Instant ts = Instant.parse("2026-04-06T13:23:58.807619673Z");
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "timestamp",
        "desc",
        new PreviewCursor("timestamp", "desc", 11L, null, ts),
        200
    );

    assertTrue(query.sql().contains("AND (e.ts < ?2 OR (e.ts = ?3 AND e.id < ?4) OR e.ts IS NULL)"));
    assertTrue(query.sql().contains("ORDER BY e.ts DESC NULLS LAST, e.id DESC"));
    assertEquals(List.of(ts, ts, 11L, 200), query.params());
  }

  @Test
  void buildsTimestampDescQueryWithNullTs() {
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "timestamp",
        "desc",
        new PreviewCursor("timestamp", "desc", 11L, null, null),
        200
    );

    assertTrue(query.sql().contains("AND (e.ts IS NULL AND e.id < ?2)"));
    assertTrue(query.sql().contains("ORDER BY e.ts DESC NULLS LAST, e.id DESC"));
    assertEquals(List.of(11L, 200), query.params());
  }
}
