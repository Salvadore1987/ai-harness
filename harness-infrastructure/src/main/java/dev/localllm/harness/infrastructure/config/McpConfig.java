package dev.localllm.harness.infrastructure.config;

import java.util.List;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.localllm.harness.infrastructure.out.mcp.McpServersFileReader;
import dev.localllm.harness.infrastructure.out.mcp.McpStartupReporter;

/**
 * MCP servers beyond those Spring AI configures from {@code spring.ai.mcp.client.*}.
 */
@Configuration(proxyBeanMethods = false)
public class McpConfig {

  /**
   * Stdio servers of {@code <harness.home>/mcp.json}; Spring AI creates a client for every
   * transport list in the context.
   *
   * @param properties harness settings with the home directory
   * @param reporter receives the problems of the file
   * @return transports of the servers in the file
   */
  @Bean
  public List<NamedClientMcpTransport> mcpJsonTransports(
      HarnessProperties properties, McpStartupReporter reporter) {
    return new McpServersFileReader(reporter).read(properties.mcpServersFile());
  }
}
