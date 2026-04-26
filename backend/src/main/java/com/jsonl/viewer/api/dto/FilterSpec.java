package com.jsonl.viewer.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public class FilterSpec {
  private String type;
  private String query;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  @JsonAnySetter
  public void rejectUnknown(String property, Object value) {
    throw new IllegalArgumentException("Unsupported filter member: " + property);
  }
}
