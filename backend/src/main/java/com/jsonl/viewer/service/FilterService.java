package com.jsonl.viewer.service;

import com.jsonl.viewer.api.dto.FilterCountRequest;
import com.jsonl.viewer.api.dto.FilterSpec;
import com.jsonl.viewer.api.dto.PreviewRequest;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.FilterSql;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class FilterService {
  private static final long EPOCH_MILLIS_THRESHOLD = 1_000_000_000_000L;
  public static final String FILTERS_OP_AND = "and";
  public static final String FILTERS_OP_OR = "or";
  private static final String FIELD_OP_CONTAINS = "contains";
  private static final String FIELD_OP_NULL = "null";
  private static final String FIELD_OP_NOT_NULL = "not_null";
  private static final String FIELD_OP_EMPTY = "empty";
  private static final String FIELD_OP_NOT_EMPTY = "not_empty";

  public List<FilterCriteria> normalize(FilterCountRequest request) {
    return normalizeInternal(request.getFilters(), request.getFieldPath(), request.getValueContains(),
        request.getTimestampFrom(), request.getTimestampTo());
  }

  public List<FilterCriteria> normalize(PreviewRequest request) {
    return normalizeInternal(request.getFilters(), request.getFieldPath(), request.getValueContains(),
        request.getTimestampFrom(), request.getTimestampTo());
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
                "WHERE file_path = ?1 AND parsed IS NOT NULL " +
                "AND to_tsvector('simple', parsed::text) @@ plainto_tsquery('simple', ?" + nextParamIndex + ")"
        );
        params.add(query);
        nextParamIndex++;
      } else if (filter.type().equalsIgnoreCase("timestamp")) {
        List<String> tsPredicates = new ArrayList<>();
        if (filter.from() != null) {
          tsPredicates.add("ts >= ?" + nextParamIndex);
          params.add(filter.from());
          nextParamIndex++;
        }
        if (filter.to() != null) {
          tsPredicates.add("ts <= ?" + nextParamIndex);
          params.add(filter.to());
          nextParamIndex++;
        }
        if (!tsPredicates.isEmpty()) {
          candidateIdQueries.add(
              "SELECT id FROM jsonl_entry WHERE file_path = ?1 AND "
                  + String.join(" AND ", tsPredicates)
          );
        }
      }
    }

    if (candidateIdQueries.isEmpty()) {
      return new FilterSql("SELECT id FROM jsonl_entry WHERE file_path = ?1", List.of());
    }

    String setOperator = useOrMode ? " UNION " : " INTERSECT ";
    String candidateIdsSql = String.join(setOperator, candidateIdQueries);
    return new FilterSql(candidateIdsSql, params);
  }

  public String normalizeFiltersOp(String rawFiltersOp) {
    String normalized = safeTrim(rawFiltersOp).toLowerCase(Locale.ROOT);
    return FILTERS_OP_OR.equals(normalized) ? FILTERS_OP_OR : FILTERS_OP_AND;
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
          result.add(new FilterCriteria(
              type,
              spec.getFieldPath(),
              normalizeFieldOp(spec.getOp()),
              spec.getValueContains(),
              null,
              null,
              null
          ));
        } else if (type.equalsIgnoreCase("text")) {
          String query = safeTrim(spec.getQuery());
          if (!query.isEmpty()) {
            result.add(new FilterCriteria(type, null, null, null, query, null, null));
          }
        } else if (type.equalsIgnoreCase("timestamp")) {
          Instant from = parseInstant(spec.getFrom());
          Instant to = parseInstant(spec.getTo());
          if (from != null || to != null) {
            result.add(new FilterCriteria(type, spec.getFieldPath(), null, null, null, from, to));
          }
        }
      }
      return result;
    }

    String trimmedField = safeTrim(fieldPath);
    if (!trimmedField.isEmpty()) {
      result.add(new FilterCriteria("field", trimmedField, FIELD_OP_CONTAINS, valueContains, null, null, null));
    }

    Instant from = parseInstant(timestampFrom);
    Instant to = parseInstant(timestampTo);
    if (from != null || to != null) {
      result.add(new FilterCriteria("timestamp", null, null, null, null, from, to));
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

  private Instant parseInstant(String raw) {
    String trimmed = safeTrim(raw);
    if (trimmed.isEmpty()) return null;

    Instant epochInstant = parseEpoch(trimmed);
    if (epochInstant != null) return epochInstant;

    Instant instant = parseAsInstant(trimmed);
    if (instant != null) return instant;

    Instant offsetDateTimeInstant = parseAsOffsetDateTime(trimmed);
    if (offsetDateTimeInstant != null) return offsetDateTimeInstant;

    Instant localDateTimeInstant = parseAsLocalDateTimeUtc(trimmed);
    if (localDateTimeInstant != null) return localDateTimeInstant;

    int firstSpace = trimmed.indexOf(' ');
    if (firstSpace > 0 && firstSpace == trimmed.lastIndexOf(' ')) {
      String withT = trimmed.substring(0, firstSpace) + "T" + trimmed.substring(firstSpace + 1);
      Instant retriedOffsetDateTime = parseAsOffsetDateTime(withT);
      if (retriedOffsetDateTime != null) return retriedOffsetDateTime;
      return parseAsLocalDateTimeUtc(withT);
    }

    return null;
  }

  private Instant parseEpoch(String raw) {
    if (!raw.matches("^[+-]?\\d+$")) return null;
    try {
      long epochValue = Long.parseLong(raw);
      return epochValue > EPOCH_MILLIS_THRESHOLD
          ? Instant.ofEpochMilli(epochValue)
          : Instant.ofEpochSecond(epochValue);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private Instant parseAsInstant(String raw) {
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private Instant parseAsOffsetDateTime(String raw) {
    try {
      return OffsetDateTime.parse(raw).toInstant();
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private Instant parseAsLocalDateTimeUtc(String raw) {
    try {
      return LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }
}
