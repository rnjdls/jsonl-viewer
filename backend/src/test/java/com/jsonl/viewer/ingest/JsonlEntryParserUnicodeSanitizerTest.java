package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonlEntryParserUnicodeSanitizerTest {
  private JsonlEntryParser parser;

  @BeforeEach
  void setUp() {
    parser = new JsonlEntryParser(new ObjectMapper());
  }

  @Test
  void sanitizesNullAndUnpairedSurrogateInParsedJson() {
    JsonlEntryParseResult parsed = parser.parse("""
        {
          "withNull": "prefix\\u0000suffix",
          "withUnpairedSurrogate": "prefix\\uD800suffix"
        }
        """);

    assertNull(parsed.parseError());
    assertNotNull(parsed.parsed());

    String withNull = parsed.parsed().get("withNull").asText();
    String withUnpairedSurrogate = parsed.parsed().get("withUnpairedSurrogate").asText();

    assertFalse(parsed.parsed().toString().contains("\\u0000"));
    assertFalse(parsed.parsed().toString().contains("\\uD800"));
    assertFalse(withNull.indexOf('\u0000') >= 0);
    assertFalse(withUnpairedSurrogate.indexOf('\u0000') >= 0);
    assertFalse(hasSurrogateCodeUnits(withNull));
    assertFalse(hasSurrogateCodeUnits(withUnpairedSurrogate));
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
