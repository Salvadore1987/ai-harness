package dev.localllm.harness.infrastructure.out.ollama;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
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
import dev.localllm.harness.infrastructure.out.tools.FileTools;
import dev.localllm.harness.infrastructure.out.tools.SpringAiToolExecutorAdapter;
import dev.localllm.harness.infrastructure.out.tools.mapper.ToolSpecSpringAiMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent loop on the local {@code qwen3.5:27b} with the real file tools.
 *
 * <p>Started with {@code ./gradlew test -PincludeTags=live}; prints the turn duration with and
 * without thinking.
 */
@Tag("live")
class AgentLiveIT {

  private static final String BASE_URL = "http://localhost:11434";

  @TempDir
  private Path cwd;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void execute_AskingAboutAFile_ReadsItWithTheTool(boolean thinking) throws IOException {
    // Arrange
    Files.writeString(cwd.resolve("Calc.java"), """
        public class Calc {
          public int add(int a, int b) {
            return a * b + 1;
          }
        }
        """);
    InMemoryConversationPort conversation = new InMemoryConversationPort();
    RecordingTurnOutputPort output = new RecordingTurnOutputPort();
    OllamaChatAdapter chatAdapter = OllamaTestSupport.chatAdapter(BASE_URL);
    ContextMeter meter = new ContextMeter(new FixedContextBudgetPort(TestProperties.NUM_CTX),
        new ContextPolicy(0.8, 0.25, 3.5));
    AgentLoopService service = new AgentLoopService(conversation,
        new SessionRegistrar(new FakeSessionCatalogPort(), Clock.systemUTC()),
        chatAdapter,
        new SpringAiToolExecutorAdapter(
            List.of(MethodToolCallbackProvider.builder().toolObjects(new FileTools()).build()),
            Mappers.getMapper(ToolSpecSpringAiMapper.class),
            TestProperties.create(cwd.resolve("home"), BASE_URL, false)),
        output, new ConversationCompactor(conversation, chatAdapter, meter),
        new TurnCancellation(), new AgentPolicy(10, 2),
        Mappers.getMapper(TurnApplicationMapper.class));

    // Act
    TurnResult result = service.execute(new RunAgentTurnCommand(SessionId.newId(), cwd,
        OllamaTestSupport.MODEL,
        "Прочитай файл Calc.java и скажи, что на самом деле делает метод add.", thinking));

    // Assert
    System.out.printf(Locale.ROOT, "agent turn, thinking=%s: %.1f s, %d completion tokens,"
            + " tools %s%n", thinking, result.elapsed().toMillis() / 1000.0,
        result.completionTokens(), output.toolCalls().stream().map(ToolCall::name).toList());
    assertThat(result.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(output.toolCalls()).extracting(ToolCall::name).contains("read_file");
    assertThat(output.toolResults()).anyMatch(toolResult -> toolResult.output().contains("a * b"));
    assertThat(result.content()).isNotBlank();
  }
}
