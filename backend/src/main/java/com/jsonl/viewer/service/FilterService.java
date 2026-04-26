package com.jsonl.viewer.service;

import com.jsonl.viewer.api.dto.FilterCountRequest;
import com.jsonl.viewer.api.dto.FilterSpec;
import com.jsonl.viewer.api.dto.PreviewRequest;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class FilterService {
  public static final String FILTERS_OP_AND = "and";
  public static final String FILTERS_OP_OR = "or";

  public List<FilterCriteria> normalize(FilterCountRequest request) {
    return normalizeInternal(request.getFilters());
  }

  public List<FilterCriteria> normalize(PreviewRequest request) {
    return normalizeInternal(request.getFilters());
  }

  public FilterSql buildFilterSql(List<FilterCriteria> filters) {
    return buildFilterSql(filters, FILTERS_OP_AND);
  }

  public FilterSql buildFilterSql(List<FilterCriteria> filters, String filtersOp) {
    if (filters == null || filters.isEmpty()) {
      return new FilterSql("SELECT id FROM jsonl_entry WHERE file_path = ?1", List.of());
    }

    List<String> candidateIdQueries = new ArrayList<>();
    List<Object> params = new ArrayList<>();
    int nextParamIndex = 2;
    boolean useOrMode = FILTERS_OP_OR.equals(normalizeFiltersOp(filtersOp));

    for (FilterCriteria filter : filters) {
      if (filter.type() == null) continue;
      if (filter.type().equalsIgnoreCase("field")) {
        throw new IllegalArgumentException("Field filters are no longer supported");
      }
      if (filter.type().equalsIgnoreCase("text")) {
        String query = safeTrim(filter.query());
        if (query.isEmpty()) continue;
        candidateIdQueries.add(
            "SELECT id FROM jsonl_entry " +
                "WHERE file_path = ?1 AND search_text IS NOT NULL " +
                "AND to_tsvector('simple', search_text) @@ " +
                "plainto_tsquery('simple', regexp_replace(?" + nextParamIndex + ", '[^[:alnum:]]+', ' ', 'g'))"
        );
        params.add(query);
        nextParamIndex++;
      } else {
        // Ignore unknown types to preserve compatibility with non-field legacy payloads.
      }
    }

    if (candidateIdQueries.isEmpty()) {
      return new FilterSql("SELECT id FROM jsonl_entry WHERE file_path = ?1", List.of());
    }

    String setOperator = useOrMode ? " UNION " : " INTERSECT ";
    String candidateIdsSql = String.join(setOperator, candidateIdQueries);
    return new FilterSql(candidateIdsSql, params);
  }

  public String extractSingleTextQuery(List<FilterCriteria> filters) {
    if (filters == null || filters.size() != 1) {
      return null;
    }
    FilterCriteria onlyFilter = filters.get(0);
    if (onlyFilter == null || onlyFilter.type() == null || !onlyFilter.type().equalsIgnoreCase("text")) {
      return null;
    }
    String query = safeTrim(onlyFilter.query());
    return query.isEmpty() ? null : query;
  }

  public String normalizeFiltersOp(String rawFiltersOp) {
    String normalized = safeTrim(rawFiltersOp).toLowerCase(Locale.ROOT);
    return FILTERS_OP_OR.equals(normalized) ? FILTERS_OP_OR : FILTERS_OP_AND;
  }

  private List<FilterCriteria> normalizeInternal(List<FilterSpec> filters) {
    List<FilterCriteria> result = new ArrayList<>();
    if (filters == null || filters.isEmpty()) {
      return result;
    }

    for (FilterSpec spec : filters) {
      if (spec == null) continue;
      String type = safeTrim(spec.getType());
      if (type.isEmpty()) continue;
      if (type.equalsIgnoreCase("field")) {
        throw new IllegalArgumentException("Field filters are no longer supported");
      }
      if (!type.equalsIgnoreCase("text")) {
        continue;
      }
      String query = safeTrim(spec.getQuery());
      if (!query.isEmpty()) {
        result.add(new FilterCriteria(type, query));
      }
    }
    return result;
  }

  private String safeTrim(String value) {
    return value == null ? "" : value.trim();
  }
}
