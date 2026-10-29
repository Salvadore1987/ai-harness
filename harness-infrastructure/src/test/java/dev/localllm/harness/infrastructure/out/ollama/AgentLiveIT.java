package dev.localllm.harness.infrastructure.out.ollama;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import dev.localllm.harness.application.dto.command.RunAgentTurnCommand;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.fixture.FakeSessionCatalogPort;
import dev.localllm.harness.application.fixture.FixedContextBudgetPort;
import dev.localllm.harness.application.fixture.InMemoryConversationPort;
import dev.localllm.harness.application.fixture.RecordingTurnOutputPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.AgentPolicy;
import dev.localllm.harness.application.model.ContextPolicy;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.TurnStatus;
import dev.localllm.harness.application.service.AgentLoopService;
import dev.localllm.harness.application.service.ContextMeter;
import dev.localllm.harness.application.service.ConversationCompactor;
import dev.localllm.harness.application.service.SessionRegistrar;
import dev.localllm.harness.application.service.TurnCancellation;
import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.TestTerminals;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties.ToolCallModeType;
import dev.localllm.harness.infrastructure.out.mcp.McpConnector;
import dev.localllm.harness.infrastructure.out.mcp.McpStartupReporter;
import dev.localllm.harness.infrastructure.out.mcp.McpTestSupport;
import dev.localllm.harness.infrastructure.out.tools.FileTools;
import dev.localllm.harness.infrastructure.out.tools.HttpTools;
import dev.localllm.harness.infrastructure.out.tools.ShellTools;
import dev.localllm.harness.infrastructure.out.tools.SpringAiToolExecutorAdapter;
import dev.localllm.harness.infrastructure.out.tools.mapper.ToolSpecSpringAiMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent loop on the local {@code qwen3.5:27b} with the real file tools.
 *
 * <p>Started with {@code ./gradlew test -PincludeTags=live}; prints the turn duration of each
 * scenario: native tool calls with and without thinking, the text-json protocol, an edit and a
 * tool of a stdio MCP server.
 */
@Tag("live")
class AgentLiveIT {

  private static final String BASE_URL = "http://localhost:11434";
  private static final String QUESTION =
      "Прочитай файл Calc.java и скажи, что на самом деле делает метод add.";

  @TempDir
  private Path cwd;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void execute_AskingAboutAFile_ReadsItWithTheTool(boolean thinking) throws IOException {
    // Arrange
    writeCalc();
    RecordingTurnOutputPort output = new RecordingTurnOutputPort();
    AgentLoopService service = service(OllamaTestSupport.chatAdapter(BASE_URL), output);

    // Act
    TurnResult result = service.execute(command(QUESTION, thinking));

    // Assert
    report("native, thinking=" + thinking, result, output);
    assertThat(result.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(output.toolCalls()).extracting(ToolCall::name).contains("read_file");
    assertThat(output.toolResults()).anyMatch(toolResult -> toolResult.output().contains("a * b"));
    assertThat(result.content()).isNotBlank();
  }

  @Test
  void execute_InTextJsonMode_ReadsFileWithAToolBlock() throws IOException {
    // Arrange
    writeCalc();
    RecordingTurnOutputPort output = new RecordingTurnOutputPort();
    AgentLoopService service = service(OllamaTestSupport.chatAdapter(BASE_URL,
        Map.of("qwen3.5", ToolCallModeType.TEXT_JSON)), output);

    // Act
    TurnResult result = service.execute(command(QUESTION, false));

    // Assert
    report("text-json", result, output);
    assertThat(result.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(output.toolCalls()).extracting(ToolCall::name).contains("read_file");
    assertThat(output.toolResults()).anyMatch(toolResult -> toolResult.output().contains("a * b"));
    assertThat(result.content()).isNotBlank().doesNotContain("```tool");
  }

  @Test
  void execute_AskingToFixAFile_EditsIt() throws IOException {
    // Arrange
    writeCalc();
    RecordingTurnOutputPort output = new RecordingTurnOutputPort();
    AgentLoopService service = service(OllamaTestSupport.chatAdapter(BASE_URL), output);

    // Act
    TurnResult result = service.execute(command(
        "Исправь метод add в Calc.java, чтобы он возвращал сумму a и b.", false));

    // Assert
    report("edit", result, output);
    assertThat(result.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(output.toolCalls()).extracting(ToolCall::name)
        .containsAnyOf("edit_file", "write_file");
    assertThat(Files.readString(cwd.resolve("Calc.java")))
        .containsPattern("return a \\+ b;|return b \\+ a;").doesNotContain("a * b");
  }

  @Test
  void execute_AskingForAnMcpTool_CallsItOnTheStdioServer() {
    // Arrange
    RecordingTurnOutputPort output = new RecordingTurnOutputPort();
    McpConnector connector = McpTestSupport.connector(
        List.of(McpTestSupport.stdioClient("echo", McpTestSupport.echoServer())),
        new McpStartupReporter(TestTerminals.dumb().terminal()));
    AgentLoopService service = service(OllamaTestSupport.chatAdapter(BASE_URL), output, connector);

    // Act
    TurnResult result;
    try {
      result = service.execute(command(
          "Вызови инструмент echo MCP-сервера с текстом «привет» и перескажи ответ.", false));
    } finally {
      connector.destroy();
    }

    // Assert
    report("mcp", result, output);
    assertThat(result.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(output.toolCalls()).extracting(ToolCall::name).contains("mcp__echo__echo");
    assertThat(output.toolResults())
        .anyMatch(toolResult -> toolResult.output().equals("echo: привет\n\ndone"));
  }

  private AgentLoopService service(OllamaChatAdapter chatAdapter,
      RecordingTurnOutputPort output) {
    return service(chatAdapter, output, McpTestSupport.noServers());
  }

  private AgentLoopService service(OllamaChatAdapter chatAdapter,
      RecordingTurnOutputPort output, McpConnector mcpConnector) {
    InMemoryConversationPort conversation = new InMemoryConversationPort();
    HarnessProperties properties = TestProperties.create(cwd.resolve("home"), BASE_URL, false);
    ContextMeter meter = new ContextMeter(new FixedContextBudgetPort(TestProperties.NUM_CTX),
        new ContextPolicy(0.8, 0.25, 3.5));
    return new AgentLoopService(conversation,
        new SessionRegistrar(new FakeSessionCatalogPort(), Clock.systemUTC()),
        chatAdapter,
        new SpringAiToolExecutorAdapter(
            List.of(MethodToolCallbackProvider.builder().toolObjects(new FileTools(),
                new ShellTools(properties), new HttpTools(properties)).build()),
            mcpConnector, Mappers.getMapper(ToolSpecSpringAiMapper.class), properties),
        output, new ConversationCompactor(conversation, chatAdapter, meter),
        new TurnCancellation(), new AgentPolicy(10, 2),
        Mappers.getMapper(TurnApplicationMapper.class));
  }

  private RunAgentTurnCommand command(String text, boolean thinking) {
    return new RunAgentTurnCommand(SessionId.newId(), cwd, OllamaTestSupport.MODEL, text,
        thinking);
  }

  private void writeCalc() throws IOException {
    Files.writeString(cwd.resolve("Calc.java"), """
        public class Calc {
          public int add(int a, int b) {
            return a * b + 1;
          }
        }
        """);
  }

  private static void report(String scenario, TurnResult result,
      RecordingTurnOutputPort output) {
    System.out.printf(Locale.ROOT, "agent turn, %s: %.1f s, %d completion tokens, tools %s%n",
        scenario, result.elapsed().toMillis() / 1000.0, result.completionTokens(),
        output.toolCalls().stream().map(ToolCall::name).toList());
  }
}
