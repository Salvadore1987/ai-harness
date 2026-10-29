package dev.localllm.harness.infrastructure.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.localllm.harness.infrastructure.out.tools.FileTools;
import dev.localllm.harness.infrastructure.out.tools.HttpTools;
import dev.localllm.harness.infrastructure.out.tools.ShellTools;

/**
 * Callbacks of the built-in {@code @Tool} methods.
 */
@Configuration(proxyBeanMethods = false)
public class ToolsConfig {

  /**
   * Built-in tools registered from their {@code @Tool} methods.
   *
   * @param fileTools file tools
   * @param shellTools {@code run_command}
   * @param httpTools {@code http_fetch}
   * @return provider of the built-in tool callbacks
   */
  @Bean
  public ToolCallbackProvider builtinToolCallbacks(
      FileTools fileTools, ShellTools shellTools, HttpTools httpTools) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(fileTools, shellTools, httpTools)
        .build();
  }
}
