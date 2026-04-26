package com.jsonl.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
  void normalizeRejectsFieldFilterType() {
    FilterSpec spec = new FilterSpec();
    spec.setType("field");

    FilterCountRequest request = new FilterCountRequest();
    request.setFilters(List.of(spec));

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> filterService.normalize(request)
    );

    assertEquals("Field filters are no longer supported", exception.getMessage());
  }

  @Test
  void normalizeIgnoresUnsupportedFilterTypes() {
    FilterSpec timestamp = new FilterSpec();
    timestamp.setType("timestamp");

    FilterCountRequest request = new FilterCountRequest();
    request.setFilters(List.of(timestamp));

    List<FilterCriteria> normalized = filterService.normalize(request);

    assertTrue(normalized.isEmpty());
  }

  @Test
  void buildFilterSqlAddsFullTextCandidateQuery() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("text", "worker failed")
    ));

    assertEquals(1, sql.params().size());
    assertEquals("worker failed", sql.params().get(0));
    assertTrue(sql.candidateIdsSql().contains("SELECT id FROM jsonl_entry"));
    assertTrue(sql.candidateIdsSql().contains("search_text IS NOT NULL"));
    assertTrue(sql.candidateIdsSql().contains("plainto_tsquery('simple', regexp_replace(?2, '[^[:alnum:]]+', ' ', 'g'))"));
  }

  @Test
  void buildFilterSqlKeepsHyphenatedTextQueryAsSingleInputParam() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("text", "Auto-generated")
    ));

    assertEquals(List.of("Auto-generated"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("regexp_replace(?2, '[^[:alnum:]]+', ' ', 'g')"));
  }

  @Test
  void buildFilterSqlIgnoresBlankTextQuery() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("text", "   ")
    ));

    assertEquals("SELECT id FROM jsonl_entry WHERE file_path = ?1", sql.candidateIdsSql());
    assertTrue(sql.params().isEmpty());
  }

  @Test
  void buildFilterSqlOrModeUsesUnion() {
    FilterSql sql = filterService.buildFilterSql(
        List.of(
            new FilterCriteria("text", "timeout"),
            new FilterCriteria("text", "worker failed")
        ),
        "OR"
    );

    assertEquals(List.of("timeout", "worker failed"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("regexp_replace(?2, '[^[:alnum:]]+', ' ', 'g')"));
    assertTrue(sql.candidateIdsSql().contains("regexp_replace(?3, '[^[:alnum:]]+', ' ', 'g')"));
    assertTrue(sql.candidateIdsSql().contains(" UNION "));
  }

  @Test
  void buildFilterSqlAndModeUsesIntersect() {
    FilterSql sql = filterService.buildFilterSql(
        List.of(
            new FilterCriteria("text", "worker failed"),
            new FilterCriteria("text", "Auto-generated")
        ),
        "and"
    );

    assertEquals(List.of("worker failed", "Auto-generated"), sql.params());
    assertTrue(sql.candidateIdsSql().contains("regexp_replace(?2, '[^[:alnum:]]+', ' ', 'g')"));
    assertTrue(sql.candidateIdsSql().contains("regexp_replace(?3, '[^[:alnum:]]+', ' ', 'g')"));
    assertTrue(sql.candidateIdsSql().contains(" INTERSECT "));
  }

  @Test
  void buildFilterSqlRejectsFieldFilterCriteria() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> filterService.buildFilterSql(List.of(
            new FilterCriteria("field", "status")
        ))
    );

    assertEquals("Field filters are no longer supported", exception.getMessage());
  }

  @Test
  void extractSingleTextQueryReturnsQueryWhenOnlyOneTextFilterExists() {
    String query = filterService.extractSingleTextQuery(List.of(
        new FilterCriteria("text", "gateway timeout")
    ));

    assertEquals("gateway timeout", query);
  }

  @Test
  void extractSingleTextQueryReturnsNullWhenFilterSetIsNotTextOnly() {
    assertNull(filterService.extractSingleTextQuery(List.of(
        new FilterCriteria("timestamp", "2026")
    )));
    assertNull(filterService.extractSingleTextQuery(List.of(
        new FilterCriteria("text", "timeout"),
        new FilterCriteria("text", "worker")
    )));
    assertNull(filterService.extractSingleTextQuery(List.of(
        new FilterCriteria("text", "   ")
    )));
  }

  @Test
  void buildFilterSqlIgnoresUnknownTypeAndFallsBackWhenNoValidFiltersRemain() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("timestamp", "2026-04-10")
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
