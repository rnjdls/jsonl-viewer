package com.jsonl.generator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.generator.config.GeneratorProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MockJsonlGeneratorServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void generatesSingleJsonLineAndUpdatesExpectedFields() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    Path samplePath = Path.of("src/test/resources/sample-fixture.jsonl").toAbsolutePath();
    Path outputPath = tempDir.resolve("generated.jsonl");

    GeneratorProperties properties = new GeneratorProperties();
    properties.setSamplePath(samplePath.toString());
    properties.setOutputPath(outputPath.toString());
    properties.setBatchMin(1);
    properties.setBatchMax(1);
    properties.setIntervalMs(2000);
    properties.setTruncateOnStart(true);

    MockJsonlGeneratorService service = new MockJsonlGeneratorService(properties, mapper);
    service.initialize();
    service.generateBatch();

    List<String> lines = new ArrayList<>();
    for (String line : Files.readAllLines(outputPath, StandardCharsets.UTF_8)) {
      if (!line.isBlank()) {
        lines.add(line);
      }
    }

    assertEquals(1, lines.size());

    JsonNode generated = mapper.readTree(lines.get(0));
    assertTrue(generated.isObject());
    assertTrue(generated.size() >= 50);
    assertEquals(20, maxDepth(generated.path("deepPayload")));
    assertEquals(50, maxDepth(generated.path("deepPayloadDepth50A")));
    assertEquals(50, maxDepth(generated.path("deepPayloadDepth50B")));
    assertEquals(50, maxDepth(generated.path("deepPayloadDepth50C")));

    assertEquals(1L, generated.path("id").asLong());

    String timestamp = generated.path("timestamp").asText();
    assertNotNull(Instant.parse(timestamp));

    assertEquals("Auto-generated entry #1", generated.path("details").path("notes").asText());

    String expectedCorrelationId = Base64.getEncoder()
        .encodeToString("corr-0001".getBytes(StandardCharsets.UTF_8));
    assertEquals(expectedCorrelationId, generated.path("headers").path("correlationId").asText());

    assertFalse(generated.path("headers").path("eventTime").asText().isBlank());
    assertEquals(1, generated.path("deepPayload").path("depth").asInt());
  }

  private int maxDepth(JsonNode node) {
    if (node == null || node.isNull() || node.isValueNode()) {
      return 0;
    }

    int childDepth = 0;
    for (JsonNode child : node) {
      childDepth = Math.max(childDepth, maxDepth(child));
    }
    return 1 + childDepth;
  }
}
