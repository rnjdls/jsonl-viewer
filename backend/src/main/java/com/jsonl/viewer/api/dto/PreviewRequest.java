package com.jsonl.viewer.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PreviewRequest {
  private String fieldPath;
  private String valueContains;
  private String filtersOp;
  private List<FilterSpec> filters;
  private String sortDir;
  private String cursor;
  private Integer limit;

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
}
