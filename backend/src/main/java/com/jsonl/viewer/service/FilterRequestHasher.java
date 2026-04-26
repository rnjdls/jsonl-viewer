package com.jsonl.viewer.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FilterRequestHasher {
  public String hash(String filtersOp, List<FilterCriteria> filters) {
    StringBuilder payload = new StringBuilder();
    payload.append(filtersOp == null ? "and" : filtersOp);

    if (filters != null) {
      for (FilterCriteria filter : filters) {
        payload.append('\n')
            .append(nullToEmpty(filter.type())).append('|')
            .append(nullToEmpty(filter.query()));
      }
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(payload.toString().getBytes(StandardCharsets.UTF_8));
      return toHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private String toHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16));
      hex.append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
  }
}
