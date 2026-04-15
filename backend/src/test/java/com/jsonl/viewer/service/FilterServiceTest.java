package com.jsonl.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jsonl.viewer.api.dto.FilterCountRequest;
import com.jsonl.viewer.api.dto.FilterSpec;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilterServiceTest {
  private final FilterService filterService = new FilterService();

  @Test
  void normalizeIncludesTextFilterWhenQueryProvided() {
    FilterSpec spec = new FilterSpec();
    spec.setType("text");
    spec.setQuery("error timeout");

    FilterCountRequest request = new FilterCountRequest();
    request.setFilters(List.of(spec));

    List<FilterCriteria> normalized = filterService.normalize(request);

    assertEquals(1, normalized.size());
    assertEquals("text", normalized.get(0).type());
    assertEquals("error timeout", normalized.get(0).query());
  }

  @Test
  void normalizeSkipsTextFilterWhenQueryBlank() {
    FilterSpec spec = new FilterSpec();
    spec.setType("text");
    spec.setQuery("   ");

    FilterCountRequest request = new FilterCountRequest();
    request.setFilters(List.of(spec));

    List<FilterCriteria> normalized = filterService.normalize(request);

    assertTrue(normalized.isEmpty());
  }

  @Test
  void normalizeDefaultsFieldOpToContainsWhenMissing() {
    FilterSpec spec = new FilterSpec();
    spec.setType("field");
    spec.setFieldPath("status");
    spec.setValueContains("500");

    FilterCountRequest request = new FilterCountRequest();
    request.setFilters(List.of(spec));

    List<FilterCriteria> normalized = filterService.normalize(request);

    assertEquals(1, normalized.size());
    assertEquals("field", normalized.get(0).type());
    assertEquals("contains", normalized.get(0).op());
  }

  @Test
  void normalizeNormalizesFieldOpAliases() {
    FilterSpec spec = new FilterSpec();
    spec.setType("field");
    spec.setFieldPath("status");
    spec.setOp("NOT NULL");

    FilterCountRequest request = new FilterCountRequest();
    request.setFilters(List.of(spec));

    List<FilterCriteria> normalized = filterService.normalize(request);

    assertEquals(1, normalized.size());
    assertEquals("not_null", normalized.get(0).op());
  }

  @Test
  void normalizeParsesTimestampWithFractionalSecondsAndZulu() {
    FilterCriteria timestamp = normalizeTimestampFilter(
        "2026-04-06T13:23:58.801145590Z",
        null
    );

    assertEquals(Instant.parse("2026-04-06T13:23:58.801145590Z"), timestamp.from());
    assertNull(timestamp.to());
  }

  @Test
  void normalizeParsesTimestampWithOffset() {
    FilterCriteria timestamp = normalizeTimestampFilter(
        "2026-04-06T13:23:58+08:00",
        null
    );

    assertEquals(Instant.parse("2026-04-06T05:23:58Z"), timestamp.from());
    assertNull(timestamp.to());
  }

  @Test
  void normalizeParsesNaiveTimestampAsUtc() {
    FilterCriteria timestamp = normalizeTimestampFilter(
        "2026-04-06T13:23:58",
        null
    );

    assertEquals(Instant.parse("2026-04-06T13:23:58Z"), timestamp.from());
    assertNull(timestamp.to());
  }

  @Test
  void normalizeParsesSpaceSeparatedNaiveTimestampAsUtc() {
    FilterCriteria timestamp = normalizeTimestampFilter(
        "2026-04-06 13:23:58",
        null
    );

    assertEquals(Instant.parse("2026-04-06T13:23:58Z"), timestamp.from());
    assertNull(timestamp.to());
  }

  @Test
  void normalizeParsesEpochSecondsAndMillis() {
    FilterCriteria timestamp = normalizeTimestampFilter(
        "1712560000",
        "1712560000000"
    );

    assertEquals(Instant.ofEpochSecond(1712560000L), timestamp.from());
    assertEquals(Instant.ofEpochMilli(1712560000000L), timestamp.to());
  }

  @Test
  void normalizeIgnoresMalformedTimestampBounds() {
    FilterCriteria withOnlyValidUpperBound = normalizeTimestampFilter(
        "not-a-timestamp",
        "2026-04-06T13:23:58.801145590Z"
    );
    assertNull(withOnlyValidUpperBound.from());
    assertEquals(Instant.parse("2026-04-06T13:23:58.801145590Z"), withOnlyValidUpperBound.to());

    FilterSpec malformed = new FilterSpec();
    malformed.setType("timestamp");
    malformed.setFrom("still bad");
    malformed.setTo("also bad");

    FilterCountRequest request = new FilterCountRequest();
    request.setFilters(List.of(malformed));

    List<FilterCriteria> normalized = filterService.normalize(request);
    assertTrue(normalized.isEmpty());
  }

  @Test
  void buildFilterSqlAddsFullTextCandidateQuery() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("text", null, null, null, "worker failed", null, null)
    ));

    assertEquals(1, sql.params().size());
    assertEquals("worker failed", sql.params().get(0));
    assertTrue(sql.candidateIdsSql().contains("SELECT id FROM jsonl_entry"));
    assertTrue(sql.candidateIdsSql().contains("parsed IS NOT NULL"));
    assertTrue(sql.candidateIdsSql().contains("plainto_tsquery('simple', ?2)"));
  }

  @Test
  void buildFilterSqlIgnoresBlankTextQuery() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("text", null, null, null, "   ", null, null)
    ));

    assertEquals("SELECT id FROM jsonl_entry WHERE file_path = ?1", sql.candidateIdsSql());
    assertTrue(sql.params().isEmpty());
  }

  @Test
  void buildFilterSqlFieldContainsUsesFieldIndexTable() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "status", "contains", "500", null, null, null)
    ));

    assertEquals(List.of("status", "%500%"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("FROM jsonl_entry_field_index"));
    assertTrue(sql.candidateIdsSql().contains("field_key = ?2"));
    assertTrue(sql.candidateIdsSql().contains("value_text ILIKE ?3"));
  }

  @Test
  void buildFilterSqlFieldNullUsesNullPredicate() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "status", "null", null, null, null, null)
    ));

    assertEquals(List.of("status"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("is_null = TRUE"));
  }

  @Test
  void buildFilterSqlFieldNotNullUsesNotNullPredicate() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "status", "not_null", null, null, null, null)
    ));

    assertEquals(List.of("status"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("is_null = FALSE"));
  }

  @Test
  void buildFilterSqlFieldEmptyUsesEmptyPredicate() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "details", "empty", null, null, null, null)
    ));

    assertEquals(List.of("details"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("is_empty = TRUE"));
  }

  @Test
  void buildFilterSqlFieldNotEmptyUsesNotEmptyPredicate() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "details", "not_empty", null, null, null, null)
    ));

    assertEquals(List.of("details"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("is_empty = FALSE"));
    assertTrue(sql.candidateIdsSql().contains("is_null = FALSE"));
  }

  @Test
  void buildFilterSqlOrModeUsesUnion() {
    Instant from = Instant.parse("2026-04-06T13:23:58Z");
    FilterSql sql = filterService.buildFilterSql(
        List.of(
            new FilterCriteria("text", null, null, null, "timeout", null, null),
            new FilterCriteria("timestamp", null, null, null, null, from, null),
            new FilterCriteria("field", "status", "null", null, null, null, null)
        ),
        "OR"
    );

    assertEquals(List.of("timeout", "timestamp", from, "status"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("plainto_tsquery('simple', ?2)"));
    assertTrue(sql.candidateIdsSql().contains("field_path = ?3"));
    assertTrue(sql.candidateIdsSql().contains("value_ts >= ?4"));
    assertTrue(sql.candidateIdsSql().contains("field_key = ?5"));
    assertTrue(sql.candidateIdsSql().contains(" UNION "));
  }

  @Test
  void buildFilterSqlAndModeUsesIntersect() {
    Instant from = Instant.parse("2026-04-06T13:23:58Z");
    FilterSql sql = filterService.buildFilterSql(
        List.of(
            new FilterCriteria("field", "status", "null", null, null, null, null),
            new FilterCriteria("text", null, null, null, "worker failed", null, null),
            new FilterCriteria("timestamp", null, null, null, null, from, null),
            new FilterCriteria("field", "details", "contains", "Auto-generated", null, null, null)
        ),
        "and"
    );

    assertEquals(List.of("status", "worker failed", "timestamp", from, "details", "%Auto-generated%"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("field_key = ?2"));
    assertTrue(sql.candidateIdsSql().contains("plainto_tsquery('simple', ?3)"));
    assertTrue(sql.candidateIdsSql().contains("field_path = ?4"));
    assertTrue(sql.candidateIdsSql().contains("value_ts >= ?5"));
    assertTrue(sql.candidateIdsSql().contains("field_key = ?6"));
    assertTrue(sql.candidateIdsSql().contains("value_text ILIKE ?7"));
    assertTrue(sql.candidateIdsSql().contains(" INTERSECT "));
  }

  @Test
  void buildFilterSqlTimestampUsesConfiguredFieldPath() {
    Instant from = Instant.parse("2026-04-06T13:23:58Z");
    Instant to = Instant.parse("2026-04-06T14:23:58Z");
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("timestamp", "headers.eventTime", null, null, null, from, to)
    ));

    assertEquals(List.of("headers.eventTime", from, to), sql.params());
    assertTrue(sql.candidateIdsSql().contains("FROM jsonl_entry_field_index"));
    assertTrue(sql.candidateIdsSql().contains("field_path = ?2"));
    assertTrue(sql.candidateIdsSql().contains("value_ts >= ?3"));
    assertTrue(sql.candidateIdsSql().contains("value_ts <= ?4"));
  }

  @Test
  void normalizeFiltersOpDefaultsToAndWhenBlankOrUnknown() {
    assertEquals("and", filterService.normalizeFiltersOp(null));
    assertEquals("and", filterService.normalizeFiltersOp(" "));
    assertEquals("and", filterService.normalizeFiltersOp("xor"));
  }

  @Test
  void normalizeFiltersOpAcceptsOrCaseInsensitively() {
    assertEquals("or", filterService.normalizeFiltersOp("or"));
    assertEquals("or", filterService.normalizeFiltersOp("OR"));
  }

  private FilterCriteria normalizeTimestampFilter(String from, String to) {
    FilterSpec spec = new FilterSpec();
    spec.setType("timestamp");
    spec.setFrom(from);
    spec.setTo(to);

    FilterCountRequest request = new FilterCountRequest();
    request.setFilters(List.of(spec));

    List<FilterCriteria> normalized = filterService.normalize(request);
    assertEquals(1, normalized.size());
    assertEquals("timestamp", normalized.get(0).type());
    return normalized.get(0);
  }
}
