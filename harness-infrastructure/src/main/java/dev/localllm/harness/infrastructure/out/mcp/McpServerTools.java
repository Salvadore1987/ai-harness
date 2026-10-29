package dev.localllm.harness.infrastructure.out.mcp;

import java.util.List;

import org.springframework.ai.tool.ToolCallback;

/**
 * Tools of a connected MCP server.
 *
 * @param server server name from the configuration
 * @param callbacks its tools, named {@code mcp__<server>__<tool>}
 */
public record McpServerTools(String server, List<ToolCallback> callbacks) {

  /**
   * Copies the callbacks.
   */
  public McpServerTools {
    callbacks = List.copyOf(callbacks);
  }
}
