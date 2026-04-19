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
  private static final String FIELD_OP_CONTAINS = "contains";
  private static final String FIELD_OP_NULL = "null";
  private static final String FIELD_OP_NOT_NULL = "not_null";
  private static final String FIELD_OP_EMPTY = "empty";
  private static final String FIELD_OP_NOT_EMPTY = "not_empty";

  public List<FilterCriteria> normalize(FilterCountRequest request) {
    return normalizeInternal(request.getFilters(), request.getFieldPath(), request.getValueContains());
  }

  public List<FilterCriteria> normalize(PreviewRequest request) {
    return normalizeInternal(request.getFilters(), request.getFieldPath(), request.getValueContains());
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
        String fieldKey = safeTrim(filter.fieldPath());
        if (fieldKey.isEmpty()) continue;
        String op = normalizeFieldOp(filter.op());
        String value = filter.valueContains() == null ? "" : filter.valueContains();

        StringBuilder query = new StringBuilder(
            "SELECT DISTINCT entry_id AS id FROM jsonl_entry_field_index " +
                "WHERE file_path = ?1 AND field_key = ?" + nextParamIndex
        );
        params.add(fieldKey);
        if (FIELD_OP_CONTAINS.equals(op)) {
          query.append(" AND value_text ILIKE ?").append(nextParamIndex + 1);
          params.add("%" + value + "%");
          nextParamIndex += 2;
        } else if (FIELD_OP_NULL.equals(op)) {
          query.append(" AND is_null = TRUE");
          nextParamIndex += 1;
        } else if (FIELD_OP_NOT_NULL.equals(op)) {
          query.append(" AND is_null = FALSE");
          nextParamIndex += 1;
        } else if (FIELD_OP_EMPTY.equals(op)) {
          query.append(" AND is_empty = TRUE");
          nextParamIndex += 1;
        } else {
          query.append(" AND is_empty = FALSE AND is_null = FALSE");
          nextParamIndex += 1;
        }
        candidateIdQueries.add(query.toString());
      } else if (filter.type().equalsIgnoreCase("text")) {
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

  private List<FilterCriteria> normalizeInternal(
      List<FilterSpec> filters,
      String fieldPath,
      String valueContains
  ) {
    List<FilterCriteria> result = new ArrayList<>();

    if (filters != null && !filters.isEmpty()) {
      for (FilterSpec spec : filters) {
        if (spec == null) continue;
        String type = safeTrim(spec.getType());
        if (type.isEmpty()) continue;
        if (type.equalsIgnoreCase("field")) {
          result.add(new FilterCriteria(
              type,
              spec.getFieldPath(),
              normalizeFieldOp(spec.getOp()),
              spec.getValueContains(),
              null
          ));
        } else if (type.equalsIgnoreCase("text")) {
          String query = safeTrim(spec.getQuery());
          if (!query.isEmpty()) {
            result.add(new FilterCriteria(type, null, null, null, query));
          }
        }
      }
      return result;
    }

    String trimmedField = safeTrim(fieldPath);
    if (!trimmedField.isEmpty()) {
      result.add(new FilterCriteria("field", trimmedField, FIELD_OP_CONTAINS, valueContains, null));
    }

    return result;
  }

  private String normalizeFieldOp(String rawOp) {
    String normalized = safeTrim(rawOp)
        .toLowerCase(Locale.ROOT)
        .replace('-', '_')
        .replace(' ', '_');
    return switch (normalized) {
      case FIELD_OP_NULL, FIELD_OP_NOT_NULL, FIELD_OP_EMPTY, FIELD_OP_NOT_EMPTY -> normalized;
      default -> FIELD_OP_CONTAINS;
    };
  }

  private String safeTrim(String value) {
    return value == null ? "" : value.trim();
  }
}
