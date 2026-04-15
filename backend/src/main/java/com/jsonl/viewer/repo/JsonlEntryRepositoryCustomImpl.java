package com.jsonl.viewer.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.repo.PreviewQueryBuilder.PreviewQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JsonlEntryRepositoryCustomImpl implements JsonlEntryRepositoryCustom {
  @PersistenceContext private EntityManager entityManager;

  private final ObjectMapper objectMapper;

  public JsonlEntryRepositoryCustomImpl(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public long countMatching(String filePath, FilterSql filterSql, Long statementTimeoutMs) {
    applyStatementTimeout(statementTimeoutMs);
    String sql = "SELECT COUNT(*) FROM (" + filterSql.candidateIdsSql() + ") candidate_ids";
    Query query = entityManager.createNativeQuery(sql);
    bindFilterQueryParameters(query, filePath, filterSql.params());
    return asLong(query.getSingleResult());
  }

  @Override
  @Transactional(readOnly = true)
  public List<JsonlEntryRow> preview(
      String filePath,
      FilterSql filterSql,
      String sortBy,
      String sortDir,
      PreviewCursor cursor,
      int limit,
      Long statementTimeoutMs
  ) {
    applyStatementTimeout(statementTimeoutMs);
    PreviewQuery previewQuery = PreviewQueryBuilder.build(filterSql, sortBy, sortDir, cursor, limit);
    Query query = entityManager.createNativeQuery(previewQuery.sql());
    bindFilterQueryParameters(query, filePath, filterSql.params());
    bindAdditionalQueryParameters(query, filterSql.params().size() + 2, previewQuery.params());

    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();

    List<JsonlEntryRow> result = new ArrayList<>(rows.size());
    for (Object[] row : rows) {
      result.add(new JsonlEntryRow(
          asLong(row[0]),
          asLong(row[1]),
          asInstant(row[2]),
          asJsonNode(row[3]),
          asJsonNode(row[4]),
          row[5] == null ? null : row[5].toString(),
          row[6] == null ? null : row[6].toString(),
          asBoolean(row[7])
      ));
    }
    return result;
  }

  @Override
  public Optional<JsonlEntryDetailRow> findEntryDetail(String filePath, long id) {
    Query query = entityManager.createNativeQuery(
        "SELECT id, line_no, ts, parsed, parse_error " +
            "FROM jsonl_entry WHERE file_path = ?1 AND id = ?2 LIMIT 1"
    );
    query.setParameter(1, filePath);
    query.setParameter(2, id);

    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    if (rows.isEmpty()) {
      return Optional.empty();
    }

    Object[] row = rows.get(0);
    return Optional.of(new JsonlEntryDetailRow(
        asLong(row[0]),
        asLong(row[1]),
        asInstant(row[2]),
        asJsonNode(row[3]),
        row[4] == null ? null : row[4].toString()
    ));
  }

  @Override
  public Optional<String> findRawLine(String filePath, long id) {
    Query query = entityManager.createNativeQuery(
        "SELECT raw_line FROM jsonl_entry WHERE file_path = ?1 AND id = ?2 LIMIT 1"
    );
    query.setParameter(1, filePath);
    query.setParameter(2, id);

    @SuppressWarnings("unchecked")
    List<Object> rows = query.getResultList();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    Object row = rows.get(0);
    return Optional.ofNullable(row == null ? null : row.toString());
  }

  private void applyStatementTimeout(Long statementTimeoutMs) {
    if (statementTimeoutMs == null || statementTimeoutMs <= 0) {
      return;
    }
    entityManager.createNativeQuery("SET LOCAL statement_timeout = " + statementTimeoutMs).executeUpdate();
  }

  private void bindFilterQueryParameters(Query query, String filePath, List<Object> filterParams) {
    query.setParameter(1, filePath);
    for (int i = 0; i < filterParams.size(); i++) {
      query.setParameter(i + 2, toNativeQueryValue(filterParams.get(i)));
    }
  }

  private void bindAdditionalQueryParameters(Query query, int startIndex, List<Object> additionalParams) {
    for (int i = 0; i < additionalParams.size(); i++) {
      query.setParameter(startIndex + i, toNativeQueryValue(additionalParams.get(i)));
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

  private Boolean asBoolean(Object value) {
    if (value == null) return null;
    if (value instanceof Boolean bool) return bool;
    return Boolean.parseBoolean(value.toString());
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
