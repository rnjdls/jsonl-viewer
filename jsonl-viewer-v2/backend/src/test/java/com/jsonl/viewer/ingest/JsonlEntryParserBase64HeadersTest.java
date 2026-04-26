package com.jsonl.viewer.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonlEntryParserBase64HeadersTest {
  private JsonlEntryParser parser;

  @BeforeEach
  void setUp() {
    parser = new JsonlEntryParser(new ObjectMapper());
  }

  @Test
  void decodesRootHeadersString() {
    JsonlEntryParseResult parsed = parser.parse("{\"headers\":\"SGVsbG8gV29ybGQh\"}");

    assertNull(parsed.parseError());
    assertNotNull(parsed.parsed());
    assertEquals("Hello World!", parsed.parsed().get("headers").asText());
  }

  @Test
  void decodesDecodableValuesInRootHeadersObject() {
    JsonlEntryParseResult parsed = parser.parse("""
        {
          "headers": {
            "authorization": "QmVhcmVyIHRva2Vu",
            "contentType": "YXBwbGljYXRpb24vanNvbg==",
            "status": 200,
            "binary": "AAECAwQ="
          }
        }
        """);

    assertNull(parsed.parseError());
    assertEquals("Bearer token", parsed.parsed().get("headers").get("authorization").asText());
    assertEquals("application/json", parsed.parsed().get("headers").get("contentType").asText());
    assertEquals(200, parsed.parsed().get("headers").get("status").asInt());
    assertEquals("AAECAwQ=", parsed.parsed().get("headers").get("binary").asText());
  }

  @Test
  void leavesInvalidBase64ValuesUnchanged() {
    JsonlEntryParseResult parsed = parser.parse("{\"headers\":\"not_base64!!\"}");

    assertNull(parsed.parseError());
    assertEquals("not_base64!!", parsed.parsed().get("headers").asText());
  }

  @Test
  void decodesOnlyRootHeadersField() {
    JsonlEntryParseResult parsed = parser.parse("""
        {
          "headers": "SGk=",
          "body": {
            "headers": "SGVsbG8="
          }
        }
        """);

    assertNull(parsed.parseError());
    assertEquals("Hi", parsed.parsed().get("headers").asText());
    assertEquals("SGVsbG8=", parsed.parsed().get("body").get("headers").asText());
  }
}
