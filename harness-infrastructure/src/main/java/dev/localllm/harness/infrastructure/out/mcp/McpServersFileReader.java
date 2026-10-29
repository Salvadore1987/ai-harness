package dev.localllm.harness.infrastructure.out.mcp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import dev.localllm.harness.infrastructure.out.mcp.dto.McpServersFile;

/**
 * Reads the stdio servers of {@code mcp.json} (Claude Desktop format) into MCP transports.
 *
 * <p>Unlike {@code spring.ai.mcp.client.stdio.servers-configuration}, a missing file means no
 * servers, and a broken file or entry is reported as a warning instead of failing the startup.
 * Fields this harness does not know are ignored. A command that is not found is reported here:
 * the MCP SDK loses the failure to start the process and waits for the initialization timeout.
 */
public class McpServersFileReader {

  private static final JsonMapper JSON = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .build();

  private final McpStartupReporter reporter;

  /**
   * Creates the reader.
   *
   * @param reporter receives the problems of the file
   */
  public McpServersFileReader(McpStartupReporter reporter) {
    this.reporter = reporter;
  }

  /**
   * Reads the servers of a file in the file order.
   *
   * @param file path of {@code mcp.json}
   * @return a transport per valid server entry, empty if the file is missing or unreadable
   */
  public List<NamedClientMcpTransport> read(Path file) {
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    McpServersFile servers;
    try {
      servers = JSON.readValue(file.toFile(), McpServersFile.class);
    } catch (JacksonException e) {
      reporter.problem(file + " не прочитан: " + e.getOriginalMessage());
      return List.of();
    }
    Map<String, McpServersFile.Server> entries = servers.mcpServers();
    if (entries == null) {
      reporter.problem(file + ": нет объекта mcpServers");
      return List.of();
    }
    List<NamedClientMcpTransport> transports = new ArrayList<>();
    JacksonMcpJsonMapper mcpJson = new JacksonMcpJsonMapper(JsonMapper.shared());
    entries.forEach((name, server) -> {
      String command = server.command();
      if (command == null || command.isBlank()) {
        reporter.problem("MCP-сервер «" + name + "» в " + file
            + " пропущен: нет command (в mcp.json поддерживаются только stdio-серверы)");
        return;
      }
      Map<String, String> env = server.env() == null ? Map.of() : server.env();
      if (!isExecutable(command, env.getOrDefault("PATH", System.getenv("PATH")))) {
        reporter.problem("MCP-сервер «" + name + "» недоступен: команда " + command
            + " не найдена");
        return;
      }
      ServerParameters parameters = ServerParameters.builder(command)
          .args(server.args() == null ? List.of() : server.args())
          .env(env)
          .build();
      transports.add(new NamedClientMcpTransport(
          name, new StdioClientTransport(parameters, mcpJson)));
    });
    return transports;
  }

  /**
   * Whether a command can be started: an executable path, or a name found on {@code PATH}.
   *
   * @param command command of the server
   * @param path search path, {@code null} if unset
   * @return whether the process can be started
   */
  static boolean isExecutable(String command, @Nullable String path) {
    if (command.contains(File.separator)) {
      return Files.isExecutable(Path.of(command));
    }
    if (path == null) {
      return false;
    }
    for (String directory : path.split(File.pathSeparator, -1)) {
      if (!directory.isEmpty() && Files.isExecutable(Path.of(directory, command))) {
        return true;
      }
    }
    return false;
  }
}
