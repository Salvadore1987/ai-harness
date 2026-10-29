package dev.localllm.harness.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Beans of the session storage.
 */
@Configuration(proxyBeanMethods = false)
public class MemoryConfig {

  /**
   * JSON mapper for the JSONL session format, kept separate from any HTTP mapper so that the file
   * format does not change with web settings.
   *
   * @return the mapper
   */
  @Bean
  public JsonMapper jsonlMapper() {
    return JsonMapper.builder().build();
  }

  /**
   * System clock used for timestamps.
   *
   * @return UTC system clock
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
