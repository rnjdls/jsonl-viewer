package com.jsonl.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jsonl.generator.config.GeneratorProperties;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MockJsonlGeneratorService {

  private static final Logger log = LoggerFactory.getLogger(MockJsonlGeneratorService.class);
  private static final List<String> FALLBACK_USERS = buildFallbackUsers();
  private static final List<String> FALLBACK_ACTIONS = List.of("access", "create", "update", "delete");
  private static final double FALLBACK_MIN_VALUE = 100.0d;
  private static final double FALLBACK_MAX_VALUE = 1000.0d;
  private static final String FALLBACK_SOURCE = "ui-simulator";
  private static final String FALLBACK_ENVIRONMENT = "staging";

  private final GeneratorProperties properties;
  private final ObjectMapper objectMapper;
  private final ReentrantLock tickLock = new ReentrantLock();
  private final AtomicLong nextId = new AtomicLong(1L);

  private List<ObjectNode> templates = List.of();
  private List<String> observedUsers = FALLBACK_USERS;
  private List<String> observedActions = FALLBACK_ACTIONS;
  private double observedMinValue = FALLBACK_MIN_VALUE;
  private double observedMaxValue = FALLBACK_MAX_VALUE;

  public MockJsonlGeneratorService(GeneratorProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  public void initialize() {
    validateProperties();
    loadTemplates();
    if (properties.isTruncateOnStart()) {
      truncateOutputFile();
    }
    log.info(
        "Generator initialized with {} template(s), output={}, batch=[{}, {}], interval={}ms",
        templates.size(),
        properties.getOutputPath(),
        properties.getBatchMin(),
        properties.getBatchMax(),
        properties.getIntervalMs());
  }

  @Scheduled(fixedRateString = "${generator.interval-ms:2000}")
  public void generateBatch() {
    if (!tickLock.tryLock()) {
      log.debug("Skipping batch generation because previous tick is still running");
      return;
    }

    try {
      int batchSize = randomBatchSize();
      StringBuilder payload = new StringBuilder(batchSize * 320);

      for (int i = 0; i < batchSize; i++) {
        long id = nextId.getAndIncrement();
        Instant now = Instant.now();
        ObjectNode entry = templates.get(ThreadLocalRandom.current().nextInt(templates.size())).deepCopy();
        applyRandomizedFields(entry, id, now);
        payload.append(objectMapper.writeValueAsString(entry)).append('\n');
      }

      appendPayload(payload);
      log.debug("Generated {} JSONL line(s)", batchSize);
    } catch (Exception e) {
      log.warn("Failed to generate batch: {}", e.getMessage());
    } finally {
      tickLock.unlock();
    }
  }

  private void validateProperties() {
    if (properties.getBatchMin() <= 0 || properties.getBatchMax() <= 0) {
      throw new IllegalStateException("generator.batch-min and generator.batch-max must be positive");
    }
    if (properties.getBatchMin() > properties.getBatchMax()) {
      throw new IllegalStateException("generator.batch-min cannot be greater than generator.batch-max");
    }
    if (properties.getSamplePath() == null || properties.getSamplePath().isBlank()) {
      throw new IllegalStateException("generator.sample-path is required");
    }
    if (properties.getOutputPath() == null || properties.getOutputPath().isBlank()) {
      throw new IllegalStateException("generator.output-path is required");
    }
  }

  private void loadTemplates() {
    Path samplePath = Path.of(properties.getSamplePath());
    if (!Files.exists(samplePath)) {
      throw new IllegalStateException("Sample JSONL file does not exist: " + samplePath);
    }

    List<ObjectNode> loadedTemplates = new ArrayList<>();
    Set<String> users = new LinkedHashSet<>();
    Set<String> actions = new LinkedHashSet<>();
    double minValue = Double.POSITIVE_INFINITY;
    double maxValue = Double.NEGATIVE_INFINITY;

    try (BufferedReader reader = Files.newBufferedReader(samplePath, StandardCharsets.UTF_8)) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          continue;
        }

        try {
          JsonNode node = objectMapper.readTree(trimmed);
          if (!node.isObject()) {
            log.warn("Skipping non-object line {} in {}", lineNumber, samplePath);
            continue;
          }

          ObjectNode template = (ObjectNode) node;
          loadedTemplates.add(template.deepCopy());

          JsonNode userNode = template.get("user");
          if (userNode != null && userNode.isTextual() && !userNode.asText().isBlank()) {
            users.add(userNode.asText());
          }

          JsonNode actionNode = template.get("action");
          if (actionNode != null && actionNode.isTextual() && !actionNode.asText().isBlank()) {
            actions.add(actionNode.asText());
          }

          JsonNode detailsValue = template.path("details").path("value");
          if (detailsValue.isNumber()) {
            double value = detailsValue.asDouble();
            minValue = Math.min(minValue, value);
            maxValue = Math.max(maxValue, value);
          }
        } catch (Exception e) {
          log.warn("Skipping invalid JSON line {} in {}: {}", lineNumber, samplePath, e.getMessage());
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read sample file " + samplePath + ": " + e.getMessage(), e);
    }

    if (loadedTemplates.isEmpty()) {
      throw new IllegalStateException("No valid JSON object templates loaded from " + samplePath);
    }

    this.templates = List.copyOf(loadedTemplates);
    this.observedUsers = users.isEmpty() ? FALLBACK_USERS : new ArrayList<>(users);
    this.observedActions = actions.isEmpty() ? FALLBACK_ACTIONS : new ArrayList<>(actions);

    if (Double.isInfinite(minValue) || Double.isInfinite(maxValue)) {
      this.observedMinValue = FALLBACK_MIN_VALUE;
      this.observedMaxValue = FALLBACK_MAX_VALUE;
    } else {
      this.observedMinValue = minValue;
      this.observedMaxValue = maxValue;
    }
  }

  private void truncateOutputFile() {
    Path outputPath = Path.of(properties.getOutputPath());
    try {
      Path parent = outputPath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      try (BufferedWriter ignored = Files.newBufferedWriter(
          outputPath,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE)) {
        // Startup truncation only.
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to truncate output file " + outputPath + ": " + e.getMessage(), e);
    }
  }

  private int randomBatchSize() {
    int min = properties.getBatchMin();
    int max = properties.getBatchMax();
    return ThreadLocalRandom.current().nextInt(min, max + 1);
  }

  private void applyRandomizedFields(ObjectNode entry, long id, Instant now) {
    String timestamp = now.toString();

    if (entry.has("id")) {
      entry.put("id", id);
    }
    if (entry.has("timestamp")) {
      entry.put("timestamp", timestamp);
    }
    if (entry.has("user")) {
      entry.put("user", randomFrom(observedUsers));
    }
    if (entry.has("action")) {
      entry.put("action", randomFrom(observedActions));
    }

    JsonNode detailsNode = entry.get("details");
    if (detailsNode instanceof ObjectNode details) {
      if (details.has("value")) {
        details.put("value", randomValue());
      }
      if (details.has("flag")) {
        details.put("flag", ThreadLocalRandom.current().nextBoolean());
      }
      if (details.has("notes")) {
        details.put("notes", "Auto-generated entry #" + id);
      }
    }

    JsonNode headersNode = entry.get("headers");
    if (headersNode instanceof ObjectNode headers) {
      if (headers.has("correlationId")) {
        headers.put("correlationId", toBase64(String.format("corr-%04d", id)));
      }
      if (headers.has("sessionId")) {
        int session = ThreadLocalRandom.current().nextInt(0, 20);
        headers.put("sessionId", toBase64("session-" + session));
      }
      if (headers.has("eventTime")) {
        headers.put("eventTime", toBase64(timestamp));
      }
      if (headers.has("source") && isBlankNode(headers.get("source"))) {
        headers.put("source", toBase64(FALLBACK_SOURCE));
      }
      if (headers.has("environment") && isBlankNode(headers.get("environment"))) {
        headers.put("environment", toBase64(FALLBACK_ENVIRONMENT));
      }
    }
  }

  private void appendPayload(CharSequence payload) throws IOException {
    Path outputPath = Path.of(properties.getOutputPath());
    Path parent = outputPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    try (BufferedWriter writer = Files.newBufferedWriter(
        outputPath,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
        StandardOpenOption.WRITE)) {
      writer.append(payload);
      writer.flush();
    }
  }

  private double randomValue() {
    if (Double.compare(observedMinValue, observedMaxValue) == 0) {
      return observedMinValue;
    }
    return ThreadLocalRandom.current().nextDouble(observedMinValue, observedMaxValue);
  }

  private String randomFrom(List<String> values) {
    int index = ThreadLocalRandom.current().nextInt(values.size());
    return values.get(index);
  }

  private boolean isBlankNode(JsonNode node) {
    if (node == null || node.isNull()) {
      return true;
    }
    return node.isTextual() && node.asText().isBlank();
  }

  private String toBase64(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static List<String> buildFallbackUsers() {
    List<String> users = new ArrayList<>(20);
    for (int i = 0; i < 20; i++) {
      users.add("user_" + i);
    }
    return List.copyOf(users);
  }
}
