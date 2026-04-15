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
        null,
        new PreviewCursor("id", "asc", 100L, null, null, null),
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
        null,
        new PreviewCursor("id", "desc", 100L, null, null, null),
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
        null,
        new PreviewCursor("lineNo", "asc", 11L, 55L, null, null),
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
        null,
        new PreviewCursor("lineNo", "desc", 11L, 55L, null, null),
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
        "headers.eventTime",
        new PreviewCursor("timestamp", "asc", 11L, null, ts, "headers.eventTime"),
        200
    );

    assertTrue(query.sql().contains("field_path = ?2"));
    assertTrue(query.sql().contains("AND (ts_idx.value_ts > ?3 OR (ts_idx.value_ts = ?4 AND e.id > ?5) OR ts_idx.value_ts IS NULL)"));
    assertTrue(query.sql().contains("ORDER BY ts_idx.value_ts ASC NULLS LAST, e.id ASC"));
    assertEquals(List.of("headers.eventTime", ts, ts, 11L, 200), query.params());
  }

  @Test
  void buildsTimestampAscQueryWithNullTs() {
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "timestamp",
        "asc",
        "headers.eventTime",
        new PreviewCursor("timestamp", "asc", 11L, null, null, "headers.eventTime"),
        200
    );

    assertTrue(query.sql().contains("AND (ts_idx.value_ts IS NULL AND e.id > ?3)"));
    assertTrue(query.sql().contains("ORDER BY ts_idx.value_ts ASC NULLS LAST, e.id ASC"));
    assertEquals(List.of("headers.eventTime", 11L, 200), query.params());
  }

  @Test
  void buildsTimestampDescQueryWithNonNullTs() {
    Instant ts = Instant.parse("2026-04-06T13:23:58.807619673Z");
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "timestamp",
        "desc",
        "headers.eventTime",
        new PreviewCursor("timestamp", "desc", 11L, null, ts, "headers.eventTime"),
        200
    );

    assertTrue(query.sql().contains("AND (ts_idx.value_ts < ?3 OR (ts_idx.value_ts = ?4 AND e.id < ?5) OR ts_idx.value_ts IS NULL)"));
    assertTrue(query.sql().contains("ORDER BY ts_idx.value_ts DESC NULLS LAST, e.id DESC"));
    assertEquals(List.of("headers.eventTime", ts, ts, 11L, 200), query.params());
  }

  @Test
  void buildsTimestampDescQueryWithNullTs() {
    PreviewQuery query = PreviewQueryBuilder.build(
        BASE_FILTER,
        "timestamp",
        "desc",
        "headers.eventTime",
        new PreviewCursor("timestamp", "desc", 11L, null, null, "headers.eventTime"),
        200
    );

    assertTrue(query.sql().contains("AND (ts_idx.value_ts IS NULL AND e.id < ?3)"));
    assertTrue(query.sql().contains("ORDER BY ts_idx.value_ts DESC NULLS LAST, e.id DESC"));
    assertEquals(List.of("headers.eventTime", 11L, 200), query.params());
  }
}
