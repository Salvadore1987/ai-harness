package dev.localllm.harness.infrastructure.out.mcp.dto;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Contents of {@code mcp.json} in the Claude Desktop format.
 *
 * @param mcpServers server entries by name
 */
public record McpServersFile(@Nullable Map<String, Server> mcpServers) {

  /**
   * One server entry; only stdio servers with a command are supported.
   *
   * @param command executable that starts the server
   * @param args command arguments
   * @param env environment variables added to the inherited {@code PATH}, {@code HOME} and the
   *     like
   */
  public record Server(
      @Nullable String command,
      @Nullable List<String> args,
      @Nullable Map<String, String> env) {
  }
}
