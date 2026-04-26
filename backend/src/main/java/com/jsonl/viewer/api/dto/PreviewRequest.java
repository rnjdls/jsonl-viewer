package com.jsonl.viewer.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.List;

public class PreviewRequest {
  private String filtersOp;
  private List<FilterSpec> filters;
  private String sortDir;
  private String cursor;
  private Integer limit;

  public String getFiltersOp() {
    return filtersOp;
  }

  public void setFiltersOp(String filtersOp) {
    this.filtersOp = filtersOp;
  }

  public List<FilterSpec> getFilters() {
    return filters;
  }

  public void setFilters(List<FilterSpec> filters) {
    this.filters = filters;
  }

  public String getSortDir() {
    return sortDir;
  }

  public void setSortDir(String sortDir) {
    this.sortDir = sortDir;
  }

  public String getCursor() {
    return cursor;
  }

  public void setCursor(String cursor) {
    this.cursor = cursor;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  @JsonAnySetter
  public void rejectUnknown(String property, Object value) {
    throw new IllegalArgumentException("Unsupported filters/preview property: " + property);
  }
}
