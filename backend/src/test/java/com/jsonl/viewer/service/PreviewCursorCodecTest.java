package com.jsonl.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PreviewCursorCodecTest {
  private final PreviewCursorCodec codec = new PreviewCursorCodec(new ObjectMapper());

  @Test
  void roundTripIdCursor() {
    PreviewCursor source = new PreviewCursor("id", "asc", 101L, null, null);

    String encoded = codec.encode(source);
    PreviewCursor decoded = codec.decode(encoded, "id", "asc");

    assertEquals(source, decoded);
  }

  @Test
  void roundTripLineNoCursor() {
    PreviewCursor source = new PreviewCursor("lineNo", "desc", 88L, 144L, null);

    String encoded = codec.encode(source);
    PreviewCursor decoded = codec.decode(encoded, "lineNo", "desc");

    assertEquals(source, decoded);
  }

  @Test
  void roundTripTimestampCursorWithValue() {
    Instant ts = Instant.parse("2026-04-06T13:23:58.807619673Z");
    PreviewCursor source = new PreviewCursor("timestamp", "asc", 77L, null, ts);

    String encoded = codec.encode(source);
    PreviewCursor decoded = codec.decode(encoded, "timestamp", "asc");

    assertEquals(source, decoded);
  }

  @Test
  void roundTripTimestampCursorWithNullTs() {
    PreviewCursor source = new PreviewCursor("timestamp", "desc", 42L, null, null);

    String encoded = codec.encode(source);
    PreviewCursor decoded = codec.decode(encoded, "timestamp", "desc");

    assertEquals(source, decoded);
  }

  @Test
  void decodeRejectsSortMismatch() {
    String encoded = codec.encode(new PreviewCursor("id", "asc", 9L, null, null));

    assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded, "timestamp", "asc"));
  }
}
