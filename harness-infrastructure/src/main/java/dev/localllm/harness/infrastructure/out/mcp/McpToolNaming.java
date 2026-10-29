package dev.localllm.harness.infrastructure.out.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.stereotype.Component;

/**
 * Names MCP tools {@code mcp__<server>__<tool>}, so that they never clash with the built-in
 * tools or with each other and show where they come from.
 *
 * <p>The server is the connection name from the configuration, which the client carries as its
 * title. Characters other than letters, digits and {@code _} are dropped, {@code -} becomes
 * {@code _}, as tool names in requests to the model allow.
 */
@Component
public class McpToolNaming implements McpToolNamePrefixGenerator {

  private static final String PREFIX = "mcp__";
  private static final String SEPARATOR = "__";

  @Override
  public String prefixedToolName(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
    return toolName(serverName(connectionInfo.clientInfo()), tool.name());
  }

  /**
   * Name of a tool offered to the model.
   *
   * @param server server name
   * @param tool tool name on the server
   * @return prefixed tool name
   */
  public static String toolName(String server, String tool) {
    return PREFIX + McpToolUtils.format(server) + SEPARATOR + McpToolUtils.format(tool);
  }

  /**
   * Server name of a client: its title, set to the connection name by the configuration.
   *
   * @param clientInfo client implementation info
   * @return server name
   */
  public static String serverName(McpSchema.Implementation clientInfo) {
    String title = clientInfo.title();
    return title == null || title.isBlank() ? clientInfo.name() : title;
  }
}
