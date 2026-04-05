package com.jsonl.viewer.service;

import com.jsonl.viewer.api.dto.FilterCountRequest;
import com.jsonl.viewer.api.dto.FilterSpec;
import com.jsonl.viewer.api.dto.PreviewRequest;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FilterService {

  public List<FilterCriteria> normalize(FilterCountRequest request) {
    return normalizeInternal(request.getFilters(), request.getFieldPath(), request.getValueContains(),
        request.getTimestampFrom(), request.getTimestampTo());
  }

  public List<FilterCriteria> normalize(PreviewRequest request) {
    return normalizeInternal(request.getFilters(), request.getFieldPath(), request.getValueContains(),
        request.getTimestampFrom(), request.getTimestampTo());
  }

  public FilterSql buildFilterSql(List<FilterCriteria> filters) {
    if (filters == null || filters.isEmpty()) {
      return new FilterSql("WHERE file_path = ?1", List.of());
    }

    List<String> conditions = new ArrayList<>();
    List<Object> params = new ArrayList<>();
    int nextParamIndex = 2;

    for (FilterCriteria filter : filters) {
      if (filter.type() == null) continue;
      if (filter.type().equalsIgnoreCase("field")) {
        String fieldKey = safeTrim(filter.fieldPath());
        if (fieldKey.isEmpty()) continue;
        String value = filter.valueContains() == null ? "" : filter.valueContains();
        conditions.add(
            "EXISTS (" +
                "SELECT 1 FROM (" +
                "  SELECT parsed AS value " +
                "  UNION ALL " +
                "  SELECT node.value FROM jsonb_path_query(parsed, '$.**') AS node(value)" +
                ") AS node " +
                "WHERE jsonb_typeof(node.value) = 'object' " +
                "AND jsonb_exists(node.value, ?" + nextParamIndex + ") " +
                "AND jsonb_extract_path(node.value, ?" + (nextParamIndex + 1) + ")::text ILIKE ?" + (nextParamIndex + 2) +
                ")"
        );
        params.add(fieldKey);
        params.add(fieldKey);
        params.add("%" + value + "%");
        nextParamIndex += 3;
      } else if (filter.type().equalsIgnoreCase("timestamp")) {
        if (filter.from() != null) {
          conditions.add("ts >= ?" + nextParamIndex);
          params.add(filter.from());
          nextParamIndex++;
        }
        if (filter.to() != null) {
          conditions.add("ts <= ?" + nextParamIndex);
          params.add(filter.to());
          nextParamIndex++;
        }
      }
    }

    if (conditions.isEmpty()) {
      return new FilterSql("WHERE file_path = ?1", List.of());
    }

    String where = "WHERE file_path = ?1 AND (parsed IS NULL OR (parsed IS NOT NULL AND "
        + String.join(" AND ", conditions) + "))";

    return new FilterSql(where, params);
  }

  private List<FilterCriteria> normalizeInternal(
      List<FilterSpec> filters,
      String fieldPath,
      String valueContains,
      String timestampFrom,
      String timestampTo
  ) {
    List<FilterCriteria> result = new ArrayList<>();

    if (filters != null && !filters.isEmpty()) {
      for (FilterSpec spec : filters) {
        if (spec == null) continue;
        String type = safeTrim(spec.getType());
        if (type.isEmpty()) continue;
        if (type.equalsIgnoreCase("field")) {
          result.add(new FilterCriteria(type, spec.getFieldPath(), spec.getValueContains(), null, null));
        } else if (type.equalsIgnoreCase("timestamp")) {
          Instant from = parseInstant(spec.getFrom());
          Instant to = parseInstant(spec.getTo());
          if (from != null || to != null) {
            result.add(new FilterCriteria(type, spec.getFieldPath(), null, from, to));
          }
        }
      }
      return result;
    }

    String trimmedField = safeTrim(fieldPath);
    if (!trimmedField.isEmpty()) {
      result.add(new FilterCriteria("field", trimmedField, valueContains, null, null));
    }

    Instant from = parseInstant(timestampFrom);
    Instant to = parseInstant(timestampTo);
    if (from != null || to != null) {
      result.add(new FilterCriteria("timestamp", null, null, from, to));
    }

    return result;
  }

  private String safeTrim(String value) {
    return value == null ? "" : value.trim();
  }

  private Instant parseInstant(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }
}
