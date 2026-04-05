package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.repo.JsonlEntry;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonlIngestServiceBase64HeadersTest {
  private JsonlIngestService service;
  private Method parseLineMethod;

  @BeforeEach
  void setUp() throws Exception {
    AppProperties properties = new AppProperties();
    properties.setJsonlTimestampField("timestamp");
    service = new JsonlIngestService(properties, null, null, new ObjectMapper(), null);
    parseLineMethod = JsonlIngestService.class.getDeclaredMethod(
        "parseLine",
        String.class,
        long.class,
        String.class
    );
    parseLineMethod.setAccessible(true);
  }

  @Test
  void decodesRootHeadersStringAndKeepsRawLineOriginal() throws Exception {
    String rawLine = "{\"headers\":\"SGVsbG8gV29ybGQh\"}";

    JsonlEntry entry = parse(rawLine);

    assertNull(entry.getParseError());
    assertEquals(rawLine, entry.getRawLine());
    assertNotNull(entry.getParsed());
    assertEquals("Hello World!", entry.getParsed().get("headers").asText());
  }

  @Test
  void decodesDecodableValuesInRootHeadersObject() throws Exception {
    String rawLine = """
        {
          "headers": {
            "authorization": "QmVhcmVyIHRva2Vu",
            "contentType": "YXBwbGljYXRpb24vanNvbg==",
            "status": 200,
            "binary": "AAECAwQ="
          }
        }
        """;

    JsonlEntry entry = parse(rawLine);

    assertNull(entry.getParseError());
    assertEquals("Bearer token", entry.getParsed().get("headers").get("authorization").asText());
    assertEquals("application/json", entry.getParsed().get("headers").get("contentType").asText());
    assertEquals(200, entry.getParsed().get("headers").get("status").asInt());
    assertEquals("AAECAwQ=", entry.getParsed().get("headers").get("binary").asText());
  }

  @Test
  void leavesInvalidBase64ValuesUnchanged() throws Exception {
    String rawLine = "{\"headers\":\"not_base64!!\"}";

    JsonlEntry entry = parse(rawLine);

    assertNull(entry.getParseError());
    assertEquals("not_base64!!", entry.getParsed().get("headers").asText());
  }

  @Test
  void decodesOnlyRootHeadersField() throws Exception {
    String rawLine = """
        {
          "headers": "SGk=",
          "body": {
            "headers": "SGVsbG8="
          }
        }
        """;

    JsonlEntry entry = parse(rawLine);

    assertNull(entry.getParseError());
    assertEquals("Hi", entry.getParsed().get("headers").asText());
    assertEquals("SGVsbG8=", entry.getParsed().get("body").get("headers").asText());
  }

  private JsonlEntry parse(String rawLine) throws Exception {
    return (JsonlEntry) parseLineMethod.invoke(service, "/tmp/logs.jsonl", 1L, rawLine);
  }
}
