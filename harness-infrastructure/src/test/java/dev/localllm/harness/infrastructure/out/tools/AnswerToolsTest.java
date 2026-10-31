package dev.localllm.harness.infrastructure.out.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import dev.localllm.harness.application.fixture.InMemoryConversationPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.service.SaveAnswerService;
import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.out.file.AnswerFileAdapter;
import dev.localllm.harness.infrastructure.out.mcp.McpTestSupport;
import dev.localllm.harness.infrastructure.out.tools.mapper.AnswerToolMapper;
import dev.localllm.harness.infrastructure.out.tools.mapper.ToolSpecSpringAiMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code save_answer} called through the registry, as the agent loop calls it.
 */
class AnswerToolsTest {

  private static final String ANSWER = "## Итог\n\n| a | b |\n|---|---|\n| 1 | 2 |\n";

  @TempDir
  private Path cwd;

  private final InMemoryConversationPort conversation = new InMemoryConversationPort();
  private final SessionId sessionId = SessionId.newId();
  private SpringAiToolExecutorAdapter executor;

  @BeforeEach
  void setUp() {
    SaveAnswerService service = new SaveAnswerService(conversation, new AnswerFileAdapter(),
        Clock.systemUTC(), Mappers.getMapper(TurnApplicationMapper.class));
    AnswerTools tools = new AnswerTools(service, Mappers.getMapper(AnswerToolMapper.class));
    executor = new SpringAiToolExecutorAdapter(
        List.of(MethodToolCallbackProvider.builder().toolObjects(tools).build()),
        McpTestSupport.noServers(),
        Mappers.getMapper(ToolSpecSpringAiMapper.class),
        TestProperties.create(cwd.resolve("home"), false));
  }

  @Test
  void saveAnswer_DuringTurn_WritesPreviousAnswerVerbatim() throws IOException {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.USER, "Сравни a и b"));
    conversation.append(sessionId, new ChatMessage(Role.ASSISTANT, ANSWER));
    conversation.append(sessionId, new ChatMessage(Role.USER, "Сохрани данный текст"));

    // Act
    ToolResult result = call("{\"path\":\"out/answer.md\"}");

    // Assert
    assertThat(result.error()).isFalse();
    assertThat(result.output()).startsWith("Saved " + ANSWER.length() + " characters to ");
    assertThat(Files.readString(cwd.resolve("out/answer.md"))).isEqualTo(ANSWER);
  }

  @Test
  void saveAnswer_NoEarlierAnswer_ReturnsError() {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.USER, "Сохрани ответ"));

    // Act
    ToolResult result = call("{}");

    // Assert
    assertThat(result.error()).isTrue();
    assertThat(result.output()).contains("no answer to save");
  }

  private ToolResult call(String argumentsJson) {
    return executor.execute(new ToolCall("c1", "save_answer", argumentsJson), sessionId, cwd);
  }
}
