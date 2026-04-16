package com.jsonl.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jsonl.viewer.api.dto.FilterCountRequest;
import com.jsonl.viewer.api.dto.FilterSpec;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
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
  void normalizeIgnoresUnsupportedFilterTypes() {
    FilterSpec timestamp = new FilterSpec();
    timestamp.setType("timestamp");

    FilterSpec field = new FilterSpec();
    field.setType("field");
    field.setFieldPath("eventTime");
    field.setValueContains("2026");

    FilterCountRequest request = new FilterCountRequest();
    request.setFilters(List.of(timestamp, field));

    List<FilterCriteria> normalized = filterService.normalize(request);

    assertEquals(1, normalized.size());
    assertEquals("field", normalized.get(0).type());
  }

  @Test
  void normalizeSupportsLegacyFieldPayloadWhenNoFiltersArrayIsProvided() {
    FilterCountRequest request = new FilterCountRequest();
    request.setFieldPath("traceId");
    request.setValueContains("abc");

    List<FilterCriteria> normalized = filterService.normalize(request);

    assertEquals(1, normalized.size());
    assertEquals("field", normalized.get(0).type());
    assertEquals("traceId", normalized.get(0).fieldPath());
    assertEquals("contains", normalized.get(0).op());
    assertEquals("abc", normalized.get(0).valueContains());
  }

  @Test
  void buildFilterSqlAddsFullTextCandidateQuery() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("text", null, null, null, "worker failed")
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
        new FilterCriteria("text", null, null, null, "   ")
    ));

    assertEquals("SELECT id FROM jsonl_entry WHERE file_path = ?1", sql.candidateIdsSql());
    assertTrue(sql.params().isEmpty());
  }

  @Test
  void buildFilterSqlFieldContainsUsesFieldIndexTable() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "status", "contains", "500", null)
    ));

    assertEquals(List.of("status", "%500%"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("FROM jsonl_entry_field_index"));
    assertTrue(sql.candidateIdsSql().contains("field_key = ?2"));
    assertTrue(sql.candidateIdsSql().contains("value_text ILIKE ?3"));
  }

  @Test
  void buildFilterSqlFieldNullUsesNullPredicate() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "status", "null", null, null)
    ));

    assertEquals(List.of("status"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("is_null = TRUE"));
  }

  @Test
  void buildFilterSqlFieldNotNullUsesNotNullPredicate() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "status", "not_null", null, null)
    ));

    assertEquals(List.of("status"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("is_null = FALSE"));
  }

  @Test
  void buildFilterSqlFieldEmptyUsesEmptyPredicate() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "details", "empty", null, null)
    ));

    assertEquals(List.of("details"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("is_empty = TRUE"));
  }

  @Test
  void buildFilterSqlFieldNotEmptyUsesNotEmptyPredicate() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "details", "not_empty", null, null)
    ));

    assertEquals(List.of("details"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("is_empty = FALSE"));
    assertTrue(sql.candidateIdsSql().contains("is_null = FALSE"));
  }

  @Test
  void buildFilterSqlOrModeUsesUnion() {
    FilterSql sql = filterService.buildFilterSql(
        List.of(
            new FilterCriteria("text", null, null, null, "timeout"),
            new FilterCriteria("field", "status", "null", null, null)
        ),
        "OR"
    );

    assertEquals(List.of("timeout", "status"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("plainto_tsquery('simple', ?2)"));
    assertTrue(sql.candidateIdsSql().contains("field_key = ?3"));
    assertTrue(sql.candidateIdsSql().contains(" UNION "));
  }

  @Test
  void buildFilterSqlAndModeUsesIntersect() {
    FilterSql sql = filterService.buildFilterSql(
        List.of(
            new FilterCriteria("field", "status", "null", null, null),
            new FilterCriteria("text", null, null, null, "worker failed"),
            new FilterCriteria("field", "details", "contains", "Auto-generated", null)
        ),
        "and"
    );

    assertEquals(List.of("status", "worker failed", "details", "%Auto-generated%"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("field_key = ?2"));
    assertTrue(sql.candidateIdsSql().contains("plainto_tsquery('simple', ?3)"));
    assertTrue(sql.candidateIdsSql().contains("field_key = ?4"));
    assertTrue(sql.candidateIdsSql().contains("value_text ILIKE ?5"));
    assertTrue(sql.candidateIdsSql().contains(" INTERSECT "));
  }

  @Test
  void buildFilterSqlIgnoresUnknownTypeAndFallsBackWhenNoValidFiltersRemain() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("timestamp", "headers.eventTime", null, null, null)
    ));

    assertEquals("SELECT id FROM jsonl_entry WHERE file_path = ?1", sql.candidateIdsSql());
    assertTrue(sql.params().isEmpty());
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
}
