package dev.localllm.harness.infrastructure.out.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import io.modelcontextprotocol.client.McpSyncClient;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Connects the configured MCP servers in the background and hands out their tools.
 *
 * <p>The connection starts as soon as the application has started, so the REPL opens without
 * waiting for the servers. Each server connects on its own virtual thread: a server that cannot
 * start or answer within {@code spring.ai.mcp.client.request-timeout} is reported and left out,
 * the others work. {@link #servers()} waits for all of them once, when the tools are first
 * needed.
 *
 * <p>The Spring AI clients are created uninitialized ({@code initialized: false}) and without
 * the shared tool callback provider, which would fail as a whole on one broken server.
 */
@Component
@Lazy(false)
public class McpConnector implements DisposableBean {

  private static final Executor CONNECT_THREADS =
      task -> Thread.ofVirtual().name("mcp-connect").start(task);

  private final ObjectProvider<List<McpSyncClient>> clientsProvider;
  private final McpToolNaming naming;
  private final McpStartupReporter reporter;
  private final List<McpSyncClient> clients = new CopyOnWriteArrayList<>();
  private @Nullable CompletableFuture<List<McpServerTools>> connection;

  /**
   * Creates the connector.
   *
   * @param clientsProvider MCP clients of all configured servers, created on first access
   * @param naming names the tools {@code mcp__<server>__<tool>}
   * @param reporter reports servers that cannot be connected
   */
  public McpConnector(
      ObjectProvider<List<McpSyncClient>> clientsProvider,
      McpToolNaming naming,
      McpStartupReporter reporter) {
    this.clientsProvider = clientsProvider;
    this.naming = naming;
    this.reporter = reporter;
  }

  /**
   * Starts connecting once the application has started.
   */
  @EventListener(ApplicationStartedEvent.class)
  public void onApplicationStarted() {
    start();
  }

  /**
   * Starts connecting the servers unless already started.
   */
  public synchronized void start() {
    if (connection == null) {
      connection = CompletableFuture.supplyAsync(this::connectAll, CONNECT_THREADS);
    }
  }

  /**
   * Waits for the servers and prints the problems of the startup.
   *
   * @return connected servers in the configuration order
   */
  public List<McpServerTools> servers() {
    List<McpServerTools> servers = connection().join();
    reporter.flush();
    return servers;
  }

  private synchronized CompletableFuture<List<McpServerTools>> connection() {
    start();
    return Objects.requireNonNull(connection);
  }

  @Override
  public void destroy() {
    clients.forEach(McpConnector::close);
  }

  private List<McpServerTools> connectAll() {
    try {
      clientsProvider.stream().flatMap(List::stream).forEach(clients::add);
    } catch (RuntimeException e) {
      reporter.problem("MCP-клиенты не созданы: " + McpStartupReporter.describe(e));
      return List.of();
    }
    List<CompletableFuture<@Nullable McpServerTools>> connecting = new ArrayList<>();
    for (McpSyncClient client : clients) {
      connecting.add(CompletableFuture.supplyAsync(() -> connect(client), CONNECT_THREADS));
    }
    return connecting.stream()
        .map(CompletableFuture::join)
        .filter(Objects::nonNull)
        .toList();
  }

  private @Nullable McpServerTools connect(McpSyncClient client) {
    String server = McpToolNaming.serverName(client.getClientInfo());
    try {
      client.initialize();
      ToolCallback[] callbacks = SyncMcpToolCallbackProvider.builder()
          .addMcpClient(client)
          .toolNamePrefixGenerator(naming)
          .toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.noOp())
          .build()
          .getToolCallbacks();
      List<ToolCallback> tools = Arrays.stream(callbacks)
          .<ToolCallback>map(McpTextToolCallback::new)
          .toList();
      reporter.connected(server, tools.size());
      return new McpServerTools(server, tools);
    } catch (RuntimeException e) {
      reporter.failed(server, e);
      clients.remove(client);
      close(client);
      return null;
    }
  }

  private static void close(McpSyncClient client) {
    try {
      client.closeGracefully();
    } catch (RuntimeException e) {
      client.close();
    }
  }
}
