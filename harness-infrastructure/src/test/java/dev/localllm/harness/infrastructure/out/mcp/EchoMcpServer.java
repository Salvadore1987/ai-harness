package dev.localllm.harness.infrastructure.out.mcp;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stdio MCP server started as a separate process by the tests.
 *
 * <p>Tools: {@code echo} returns its {@code text} as two text items, {@code fail} always
 * returns an error result. The process runs until the client closes it.
 */
public final class EchoMcpServer {

  private EchoMcpServer() {
  }

  /**
   * Serves the tools over stdin and stdout.
   *
   * @param args ignored
   * @throws InterruptedException never, the process is destroyed by the client
   */
  public static void main(String[] args) throws InterruptedException {
    JacksonMcpJsonMapper json = new JacksonMcpJsonMapper(JsonMapper.shared());
    McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(json))
        .serverInfo("echo", "1.0.0")
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        .toolCall(McpSchema.Tool.builder("echo", Map.of("type", "object",
                    "properties", Map.of("text", Map.of("type", "string")),
                    "required", List.of("text")))
                .description("Echoes the text.")
                .build(),
            (exchange, request) -> McpSchema.CallToolResult.builder()
                .addTextContent("echo: " + request.arguments().get("text"))
                .addTextContent("done")
                .build())
        .toolCall(McpSchema.Tool.builder("fail", Map.of("type", "object"))
                .description("Always fails.")
                .build(),
            (exchange, request) -> McpSchema.CallToolResult.builder()
                .addTextContent("broken on purpose")
                .isError(true)
                .build())
        .build();
    try {
      Thread.currentThread().join();
    } finally {
      server.close();
    }
  }
}
