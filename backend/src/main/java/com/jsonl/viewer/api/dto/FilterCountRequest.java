package com.jsonl.viewer.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.List;

public class FilterCountRequest {
  private String filtersOp;
  private List<FilterSpec> filters;

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

  @JsonAnySetter
  public void rejectUnknown(String property, Object value) {
    throw new IllegalArgumentException("Unsupported filters/count property: " + property);
  }
}
