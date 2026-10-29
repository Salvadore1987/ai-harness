package dev.localllm.harness.infrastructure.out.mcp;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import dev.localllm.harness.infrastructure.TestTerminals;

/**
 * MCP clients and connectors for tests.
 */
public final class McpTestSupport {

  private McpTestSupport() {
  }

  /**
   * Stdio parameters that start {@link EchoMcpServer} in a new JVM with the test classpath.
   *
   * @return server parameters
   */
  public static ServerParameters echoServer() {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    return ServerParameters.builder(java)
        .args("-cp", System.getProperty("java.class.path"), EchoMcpServer.class.getName())
        .build();
  }

  /**
   * A client named like the ones Spring AI creates: the server name is the title.
   *
   * @param server server name
   * @param parameters how to start the server
   * @return uninitialized client
   */
  public static McpSyncClient stdioClient(String server, ServerParameters parameters) {
    return client(server, new StdioClientTransport(parameters, json()));
  }

  /**
   * A stdio client that gives up on the initialization quickly.
   *
   * @param server server name
   * @param parameters how to start the server
   * @param initializationTimeout how long to wait for the server to initialize
   * @return uninitialized client
   */
  public static McpSyncClient stdioClient(
      String server, ServerParameters parameters, Duration initializationTimeout) {
    return builder(server, new StdioClientTransport(parameters, json()))
        .initializationTimeout(initializationTimeout)
        .build();
  }

  /**
   * A client over any transport, named like the ones Spring AI creates.
   *
   * @param server server name
   * @param transport client transport
   * @return uninitialized client
   */
  public static McpSyncClient client(String server, McpClientTransport transport) {
    return builder(server, transport).build();
  }

  /**
   * A connector over fixed clients.
   *
   * @param clients clients of the configured servers
   * @param reporter receives the startup problems
   * @return connector, not started
   */
  public static McpConnector connector(List<McpSyncClient> clients, McpStartupReporter reporter) {
    return new McpConnector(provider(clients), new McpToolNaming(), reporter);
  }

  /**
   * A connector without servers.
   *
   * @return connector
   */
  public static McpConnector noServers() {
    return connector(List.of(), new McpStartupReporter(TestTerminals.dumb().terminal()));
  }

  /**
   * JSON mapper of the MCP transports.
   *
   * @return mapper
   */
  public static JacksonMcpJsonMapper json() {
    return new JacksonMcpJsonMapper(JsonMapper.shared());
  }

  private static McpClient.SyncSpec builder(String server, McpClientTransport transport) {
    return McpClient.sync(transport)
        .clientInfo(McpSchema.Implementation.builder("harness - " + server, "1.0.0")
            .title(server)
            .build())
        .requestTimeout(Duration.ofSeconds(20));
  }

  private static ObjectProvider<List<McpSyncClient>> provider(List<McpSyncClient> clients) {
    return new ObjectProvider<>() {
      @Override
      public List<McpSyncClient> getObject() {
        return clients;
      }

      @Override
      public Stream<List<McpSyncClient>> stream() {
        return Stream.of(clients);
      }
    };
  }
}
