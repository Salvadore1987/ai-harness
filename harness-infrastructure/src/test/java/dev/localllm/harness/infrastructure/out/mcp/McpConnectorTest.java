package dev.localllm.harness.infrastructure.out.mcp;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import io.modelcontextprotocol.client.transport.ServerParameters;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;
import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.TestTerminals;
import dev.localllm.harness.infrastructure.out.tools.FileTools;
import dev.localllm.harness.infrastructure.out.tools.SpringAiToolExecutorAdapter;
import dev.localllm.harness.infrastructure.out.tools.mapper.ToolSpecSpringAiMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real stdio MCP server next to one that cannot start, registered with the built-in tools.
 */
class McpConnectorTest {

  private static final TestTerminals.Captured TERMINAL = TestTerminals.dumb();

  private static McpConnector connector;
  private static SpringAiToolExecutorAdapter executor;

  @BeforeAll
  static void connect() {
    connector = McpTestSupport.connector(List.of(
            McpTestSupport.stdioClient("echo", McpTestSupport.echoServer()),
            McpTestSupport.stdioClient("broken",
                ServerParameters.builder("/nonexistent/mcp-server").build(),
                Duration.ofSeconds(1))),
        new McpStartupReporter(TERMINAL.terminal()));
    connector.start();
    executor = new SpringAiToolExecutorAdapter(
        List.of(MethodToolCallbackProvider.builder().toolObjects(new FileTools()).build()),
        connector, Mappers.getMapper(ToolSpecSpringAiMapper.class),
        TestProperties.create(Path.of("build/harness-home"), false));
  }

  @AfterAll
  static void close() {
    connector.destroy();
  }

  @Test
  void available_EchoAndBrokenServers_AddsPrefixedEchoToolsAfterBuiltins() {
    // Act
    List<ToolSpec> tools = executor.available();

    // Assert
    assertThat(tools).extracting(ToolSpec::name)
        .contains("read_file")
        .endsWith("mcp__echo__echo", "mcp__echo__fail")
        .noneMatch(name -> name.contains("broken"));
    assertThat(tools).filteredOn(tool -> tool.source() == ToolSource.MCP)
        .allSatisfy(tool -> assertThat(tool.server()).isEqualTo("echo"))
        .first()
        .satisfies(tool -> {
          assertThat(tool.description()).isEqualTo("Echoes the text.");
          assertThat(tool.inputSchema()).contains("\"text\"");
        });
    assertThat(tools).filteredOn(tool -> tool.source() == ToolSource.BUILTIN)
        .allSatisfy(tool -> assertThat(tool.server()).isNull());
  }

  @Test
  void servers_BrokenServer_IsReportedOnce() {
    // Act
    executor.available();
    connector.servers();

    // Assert
    assertThat(TERMINAL.plainText())
        .containsOnlyOnce("⚠ MCP-сервер «broken» недоступен: нет ответа на инициализацию")
        .doesNotContain("«echo»");
  }

  @Test
  void execute_EchoTool_ReturnsTextItemsAsPlainText() {
    // Act
    ToolResult result = executor.execute(
        new ToolCall("call-1", "mcp__echo__echo", "{\"text\":\"hi\"}"), SessionId.newId(),
        Path.of("."));

    // Assert
    assertThat(result.error()).isFalse();
    assertThat(result.output()).isEqualTo("echo: hi\n\ndone");
  }

  @Test
  void execute_ToolReturningError_IsAnErrorResult() {
    // Act
    ToolResult result = executor.execute(
        new ToolCall("call-2", "mcp__echo__fail", "{}"), SessionId.newId(), Path.of("."));

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).isEqualTo("Error: broken on purpose");
  }
}
