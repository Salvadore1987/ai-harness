package dev.localllm.harness.infrastructure.out.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;

import dev.localllm.harness.infrastructure.TestTerminals;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code mcp.json} in the Claude Desktop format.
 */
class McpServersFileReaderTest {

  @TempDir
  private Path home;

  private final TestTerminals.Captured terminal = TestTerminals.dumb();
  private final McpStartupReporter reporter = new McpStartupReporter(terminal.terminal());
  private final McpServersFileReader reader = new McpServersFileReader(reporter);

  @Test
  void read_MissingFile_ReturnsNoServersSilently() {
    // Act
    List<NamedClientMcpTransport> transports = reader.read(home.resolve("mcp.json"));

    // Assert
    assertThat(transports).isEmpty();
    reporter.flush();
    assertThat(terminal.plainText()).isEmpty();
  }

  @Test
  void read_MixedEntries_KeepsStartableStdioServersInFileOrderAndWarnsAboutRest()
      throws IOException {
    // Arrange
    Path file = write("""
        {
          "mcpServers": {
            "filesystem": {
              "command": "sh",
              "args": ["-c", "exec mcp-server-filesystem /tmp"],
              "env": {"DEBUG": "1"},
              "disabled": false
            },
            "remote": {"type": "http", "url": "https://example.com/mcp"},
            "missing": {"command": "no-such-mcp-server-command"},
            "git": {"command": "/bin/sh", "args": ["-c", "exec mcp-server-git"]}
          },
          "globalShortcut": "Ctrl+Space"
        }
        """);

    // Act
    List<NamedClientMcpTransport> transports = reader.read(file);

    // Assert
    assertThat(transports).extracting(NamedClientMcpTransport::name)
        .containsExactly("filesystem", "git");
    reporter.flush();
    assertThat(terminal.plainText())
        .contains("⚠ MCP-сервер «remote» в " + file + " пропущен: нет command")
        .contains("⚠ MCP-сервер «missing» недоступен: команда no-such-mcp-server-command "
            + "не найдена");
  }

  @Test
  void read_BrokenJson_WarnsAndReturnsNoServers() throws IOException {
    // Arrange
    Path file = write("{\"mcpServers\": {\"fs\": ");

    // Act
    List<NamedClientMcpTransport> transports = reader.read(file);

    // Assert
    assertThat(transports).isEmpty();
    reporter.flush();
    assertThat(terminal.plainText()).contains("⚠ " + file + " не прочитан:");
  }

  @Test
  void read_NoServersObject_Warns() throws IOException {
    // Arrange
    Path file = write("{\"servers\": {}}");

    // Act
    List<NamedClientMcpTransport> transports = reader.read(file);

    // Assert
    assertThat(transports).isEmpty();
    reporter.flush();
    assertThat(terminal.plainText()).contains("нет объекта mcpServers");
  }

  @Test
  void isExecutable_CommandsAndPaths_ChecksExecutableFiles() {
    // Act & Assert
    assertThat(McpServersFileReader.isExecutable("sh", "/nonexistent:/bin")).isTrue();
    assertThat(McpServersFileReader.isExecutable("sh", "/nonexistent")).isFalse();
    assertThat(McpServersFileReader.isExecutable("sh", null)).isFalse();
    assertThat(McpServersFileReader.isExecutable("/bin/sh", null)).isTrue();
    assertThat(McpServersFileReader.isExecutable("/etc/hosts", null)).isFalse();
  }

  private Path write(String json) throws IOException {
    return Files.writeString(home.resolve("mcp.json"), json, StandardCharsets.UTF_8);
  }
}
