package dev.localllm.harness.infrastructure.out.mcp;

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.McpConnectionInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tool names {@code mcp__<server>__<tool>}.
 */
class McpToolNamingTest {

  private final McpToolNaming naming = new McpToolNaming();

  @Test
  void prefixedToolName_ServerTitle_PrefixesWithServerName() {
    // Arrange
    McpConnectionInfo connection = connection(
        McpSchema.Implementation.builder("spring-ai-mcp-client - github", "1.0.0")
            .title("github")
            .build());

    // Act
    String name = naming.prefixedToolName(connection, tool("create_issue"));

    // Assert
    assertThat(name).isEqualTo("mcp__github__create_issue");
  }

  @Test
  void prefixedToolName_CharactersNotAllowedInToolNames_AreDroppedOrReplaced() {
    // Arrange
    McpConnectionInfo connection = connection(
        McpSchema.Implementation.builder("client", "1.0.0").title("my-fs.local").build());

    // Act
    String name = naming.prefixedToolName(connection, tool("read-file v2"));

    // Assert
    assertThat(name).isEqualTo("mcp__my_fslocal__read_filev2");
  }

  @Test
  void serverName_NoTitle_FallsBackToClientName() {
    // Act
    String server = McpToolNaming.serverName(
        McpSchema.Implementation.builder("standalone", "1.0.0").build());

    // Assert
    assertThat(server).isEqualTo("standalone");
  }

  private static McpConnectionInfo connection(McpSchema.Implementation clientInfo) {
    return McpConnectionInfo.builder()
        .clientCapabilities(McpSchema.ClientCapabilities.builder().build())
        .clientInfo(clientInfo)
        .build();
  }

  private static McpSchema.Tool tool(String name) {
    return McpSchema.Tool.builder(name, Map.of("type", "object")).build();
  }
}
