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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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

    JsonNode template = mapper.readTree(Files.readAllLines(samplePath, StandardCharsets.UTF_8).get(0));
    assertEquals(fieldNames(template), fieldNames(generated));

    assertEquals(1L, generated.path("id").asLong());

    String timestamp = generated.path("timestamp").asText();
    assertNotNull(Instant.parse(timestamp));

    assertEquals("Auto-generated entry #1", generated.path("details").path("notes").asText());

    String expectedCorrelationId = Base64.getEncoder()
        .encodeToString("corr-0001".getBytes(StandardCharsets.UTF_8));
    assertEquals(expectedCorrelationId, generated.path("headers").path("correlationId").asText());

    assertFalse(generated.path("headers").path("eventTime").asText().isBlank());
  }

  private Set<String> fieldNames(JsonNode node) {
    Set<String> names = new HashSet<>();
    Iterator<String> it = node.fieldNames();
    while (it.hasNext()) {
      names.add(it.next());
    }
    return names;
  }
}
