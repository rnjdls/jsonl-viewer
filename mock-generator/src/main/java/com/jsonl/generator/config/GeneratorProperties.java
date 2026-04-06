package com.jsonl.generator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "generator")
public class GeneratorProperties {

  private String samplePath = "/data/sample.jsonl";
  private String outputPath = "/data/generated.jsonl";
  private long intervalMs = 2000;
  private int batchMin = 250;
  private int batchMax = 350;
  private boolean truncateOnStart = true;

  public String getSamplePath() {
    return samplePath;
  }

  public void setSamplePath(String samplePath) {
    this.samplePath = samplePath;
  }

  public String getOutputPath() {
    return outputPath;
  }

  public void setOutputPath(String outputPath) {
    this.outputPath = outputPath;
  }

  public long getIntervalMs() {
    return intervalMs;
  }

  public void setIntervalMs(long intervalMs) {
    this.intervalMs = intervalMs;
  }

  public int getBatchMin() {
    return batchMin;
  }

  public void setBatchMin(int batchMin) {
    this.batchMin = batchMin;
  }

  public int getBatchMax() {
    return batchMax;
  }

  public void setBatchMax(int batchMax) {
    this.batchMax = batchMax;
  }

  public boolean isTruncateOnStart() {
    return truncateOnStart;
  }

  public void setTruncateOnStart(boolean truncateOnStart) {
    this.truncateOnStart = truncateOnStart;
  }
}
