package dev.localllm.harness.infrastructure.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.localllm.harness.infrastructure.out.tools.FileTools;

/**
 * Callbacks of the built-in {@code @Tool} methods.
 */
@Configuration(proxyBeanMethods = false)
public class ToolsConfig {

  /**
   * Built-in tools registered from their {@code @Tool} methods.
   *
   * @param fileTools read-only file tools
   * @return provider of the built-in tool callbacks
   */
  @Bean
  public ToolCallbackProvider builtinToolCallbacks(FileTools fileTools) {
    return MethodToolCallbackProvider.builder().toolObjects(fileTools).build();
  }
}
