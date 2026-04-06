package com.jsonl.generator;

import com.jsonl.generator.config.GeneratorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GeneratorProperties.class)
public class MockJsonlGeneratorApplication {

  public static void main(String[] args) {
    SpringApplication.run(MockJsonlGeneratorApplication.class, args);
  }
}
