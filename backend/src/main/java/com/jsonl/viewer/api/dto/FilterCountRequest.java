package com.jsonl.viewer.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FilterCountRequest {
  private String fieldPath;
  private String valueContains;
  private String filtersOp;
  private List<FilterSpec> filters;

  public String getFieldPath() {
    return fieldPath;
  }

  public void setFieldPath(String fieldPath) {
    this.fieldPath = fieldPath;
  }

  public String getValueContains() {
    return valueContains;
  }

  public void setValueContains(String valueContains) {
    this.valueContains = valueContains;
  }

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
}
