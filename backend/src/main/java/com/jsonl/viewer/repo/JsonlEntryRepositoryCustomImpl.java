package com.jsonl.viewer.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Repository;

@Repository
public class JsonlEntryRepositoryCustomImpl implements JsonlEntryRepositoryCustom {
  @PersistenceContext private EntityManager entityManager;

  private final ObjectMapper objectMapper;

  public JsonlEntryRepositoryCustomImpl(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Counts getCounts(String filePath) {
    Query query = entityManager.createNativeQuery(
        "SELECT COUNT(*) AS total, " +
            "COUNT(*) FILTER (WHERE parsed IS NOT NULL) AS parsed_count, " +
            "COUNT(*) FILTER (WHERE parse_error IS NOT NULL) AS error_count " +
            "FROM jsonl_entry WHERE file_path = ?1"
    );
    query.setParameter(1, filePath);

    Object[] row = (Object[]) query.getSingleResult();
    return new Counts(asLong(row[0]), asLong(row[1]), asLong(row[2]));
  }

  @Override
  public long countMatching(String filePath, FilterSql filterSql) {
    String sql = "SELECT COUNT(*) FROM jsonl_entry " + filterSql.whereClause();
    Query query = entityManager.createNativeQuery(sql);
    bindFilterQueryParameters(query, filePath, filterSql.params());
    return asLong(query.getSingleResult());
  }

  @Override
  public List<JsonlEntryRow> preview(String filePath, FilterSql filterSql, long cursorId, int limit) {
    int cursorParamIndex = filterSql.params().size() + 2;
    int limitParamIndex = filterSql.params().size() + 3;

    String sql =
        "SELECT id, line_no, raw_line, parsed, parse_error, ts " +
            "FROM jsonl_entry " + filterSql.whereClause() + " AND id > ?" + cursorParamIndex + " " +
            "ORDER BY id ASC LIMIT ?" + limitParamIndex;

    Query query = entityManager.createNativeQuery(sql);
    bindFilterQueryParameters(query, filePath, filterSql.params());
    query.setParameter(cursorParamIndex, cursorId);
    query.setParameter(limitParamIndex, limit);

    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();

    List<JsonlEntryRow> result = new ArrayList<>(rows.size());
    for (Object[] row : rows) {
      result.add(new JsonlEntryRow(
          asLong(row[0]),
          asLong(row[1]),
          row[2] == null ? null : row[2].toString(),
          asJsonNode(row[3]),
          row[4] == null ? null : row[4].toString(),
          asInstant(row[5])
      ));
    }
    return result;
  }

  private void bindFilterQueryParameters(Query query, String filePath, List<Object> filterParams) {
    query.setParameter(1, filePath);
    for (int i = 0; i < filterParams.size(); i++) {
      query.setParameter(i + 2, toNativeQueryValue(filterParams.get(i)));
    }
  }

  private Object toNativeQueryValue(Object value) {
    if (value instanceof Instant instant) {
      return Timestamp.from(instant);
    }
    return value;
  }

  private long asLong(Object value) {
    if (value == null) return 0L;
    if (value instanceof Number number) return number.longValue();
    return Long.parseLong(value.toString());
  }

  private Instant asInstant(Object value) {
    if (value == null) return null;
    if (value instanceof Instant instant) return instant;
    if (value instanceof Timestamp timestamp) return timestamp.toInstant();
    if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
    return Instant.parse(value.toString());
  }

  private JsonNode asJsonNode(Object value) {
    if (value == null) return null;
    try {
      if (value instanceof JsonNode jsonNode) {
        return jsonNode;
      }
      String jsonText;
      if (value instanceof PGobject pgObject) {
        jsonText = pgObject.getValue();
      } else {
        jsonText = value.toString();
      }
      if (jsonText == null || jsonText.isBlank()) return null;
      return objectMapper.readTree(jsonText);
    } catch (Exception ignored) {
      return null;
    }
  }
}
