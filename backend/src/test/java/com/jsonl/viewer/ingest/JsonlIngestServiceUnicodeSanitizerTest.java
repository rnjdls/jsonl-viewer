package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.config.AppProperties;
import com.jsonl.viewer.repo.JsonlEntry;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonlIngestServiceUnicodeSanitizerTest {
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
  void sanitizesNullAndUnpairedSurrogateInParsedJsonWhileKeepingRawLine() throws Exception {
    String rawLine = """
        {
          "withNull": "prefix\\u0000suffix",
          "withUnpairedSurrogate": "prefix\\uD800suffix"
        }
        """;

    JsonlEntry entry = parse(rawLine);

    assertNull(entry.getParseError());
    assertEquals(rawLine, entry.getRawLine());
    assertNotNull(entry.getParsed());

    String withNull = entry.getParsed().get("withNull").asText();
    String withUnpairedSurrogate = entry.getParsed().get("withUnpairedSurrogate").asText();

    assertFalse(entry.getParsed().toString().contains("\\u0000"));
    assertFalse(entry.getParsed().toString().contains("\\uD800"));
    assertFalse(withNull.indexOf('\u0000') >= 0);
    assertFalse(withUnpairedSurrogate.indexOf('\u0000') >= 0);
    assertFalse(hasSurrogateCodeUnits(withNull));
    assertFalse(hasSurrogateCodeUnits(withUnpairedSurrogate));
  }

  private JsonlEntry parse(String rawLine) throws Exception {
    return (JsonlEntry) parseLineMethod.invoke(service, "/tmp/logs.jsonl", 1L, rawLine);
  }

  private boolean hasSurrogateCodeUnits(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (Character.isSurrogate(value.charAt(i))) {
        return true;
      }
    }
    return false;
  }
}
