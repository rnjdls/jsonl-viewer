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
  void buildFilterSqlAddsFullTextCondition() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("text", null, null, "worker failed", null, null)
    ));

    assertEquals(1, sql.params().size());
    assertEquals("worker failed", sql.params().get(0));
    assertTrue(sql.whereClause().contains("parsed IS NOT NULL"));
    assertTrue(sql.whereClause().contains("to_tsvector('simple', parsed::text) @@ plainto_tsquery('simple', ?2)"));
  }

  @Test
  void buildFilterSqlIgnoresBlankTextQuery() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("text", null, null, "   ", null, null)
    ));

    assertEquals("WHERE file_path = ?1", sql.whereClause());
    assertTrue(sql.params().isEmpty());
  }

  @Test
  void buildFilterSqlWithoutTextFilterKeepsExistingParsedWrapper() {
    FilterSql sql = filterService.buildFilterSql(List.of(
        new FilterCriteria("field", "status", "500", null, null, null)
    ));

    assertTrue(sql.whereClause().contains("parsed IS NULL OR (parsed IS NOT NULL AND"));
  }
}
