package com.jsonl.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsonl.viewer.repo.JsonlEntryRepositoryCustom.PreviewCursor;
import org.junit.jupiter.api.Test;

class PreviewCursorCodecTest {
  private final PreviewCursorCodec codec = new PreviewCursorCodec(new ObjectMapper());

  @Test
  void roundTripCursor() {
    PreviewCursor source = new PreviewCursor("asc", 144L, 88L);

    String encoded = codec.encode(source);
    PreviewCursor decoded = codec.decode(encoded, "asc");

    assertEquals(source, decoded);
  }

  @Test
  void decodeRejectsSortMismatch() {
    String encoded = codec.encode(new PreviewCursor("asc", 9L, 99L));

    assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded, "desc"));
  }
}
