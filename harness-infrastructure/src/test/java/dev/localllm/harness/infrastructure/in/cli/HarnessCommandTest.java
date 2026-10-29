package dev.localllm.harness.infrastructure.in.cli;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.application.model.TurnStatus;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessCommandTest {

  private final CliTestContext context = new CliTestContext();

  @Test
  void execute_PrintModeWithInstalledModel_RunsOneTurnAndExitsWithZero() {
    // Arrange
    context.chatModel.respondWith(new ChatChunk.Content("hi"));

    // Act
    int exitCode = new CommandLine(context.command()).execute("-p", "say hi");

    // Assert
    assertThat(exitCode).isZero();
    ChatRequest request = context.chatModel.requests().getFirst();
    assertThat(request.model()).isEqualTo("qwen3.5:27b");
    assertThat(request.thinkingEnabled()).isTrue();
    assertThat(context.output.content()).containsExactly("hi");
  }

  @Test
  void execute_PrintModeWithNoThink_SendsThinkingDisabled() {
    // Arrange
    context.chatModel.respondWith(new ChatChunk.Content("hi"));

    // Act
    new CommandLine(context.command()).execute("--no-think", "-p", "say hi");

    // Assert
    assertThat(context.chatModel.requests().getFirst().thinkingEnabled()).isFalse();
  }

  @Test
  void execute_PrintModeWithModelWithoutThinking_TurnsThinkingOff() {
    // Arrange
    context.chatModel.respondWith(new ChatChunk.Content("hi"));

    // Act
    new CommandLine(context.command())
        .execute("--model", "codellama:7b-instruct", "-p", "say hi");

    // Assert
    ChatRequest request = context.chatModel.requests().getFirst();
    assertThat(request.model()).isEqualTo("codellama:7b-instruct");
    assertThat(request.thinkingEnabled()).isFalse();
  }

  @Test
  void execute_PrintModeWithMissingModel_ReportsAndExitsWithOne() {
    // Act
    int exitCode = new CommandLine(context.command()).execute("--model", "missing", "-p", "hi");

    // Assert
    assertThat(exitCode).isEqualTo(1);
    assertThat(context.chatModel.requests()).isEmpty();
    assertThat(context.terminal.plainText()).contains("✗ Модель missing не найдена");
  }

  @Test
  void execute_PrintModeWhenOllamaDown_ReportsAndExitsWithOne() {
    // Arrange
    context.chatModel.failWith(new LlmUnavailableException("http://localhost:11434",
        new IllegalStateException("refused")));

    // Act
    int exitCode = new CommandLine(context.command()).execute("-p", "hi");

    // Assert
    assertThat(exitCode).isEqualTo(1);
    assertThat(context.terminal.plainText())
        .contains("✗ Ollama недоступна на http://localhost:11434. Запустите: ollama serve");
  }

  @Test
  void execute_PrintModeWhenCancelled_ExitsWithOne() {
    // Arrange
    context.chatModel.respondWith(new ChatChunk.Content("par")).endWith(TurnStatus.CANCELLED);

    // Act
    int exitCode = new CommandLine(context.command()).execute("-p", "hi");

    // Assert
    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void execute_PrintModeWithResume_ContinuesLatestSessionAndItsModel() {
    // Arrange
    SessionId stored = SessionId.newId();
    context.catalog.register(new SessionInfo(stored, CliTestContext.CWD,
        "codellama:7b-instruct", "earlier", Instant.parse("2026-10-29T10:00:00Z"), 2));
    context.conversation.append(stored, new ChatMessage(Role.USER, "earlier question"));
    context.conversation.append(stored, new ChatMessage(Role.ASSISTANT, "earlier answer"));
    context.chatModel.respondWith(new ChatChunk.Content("continued"));

    // Act
    int exitCode = new CommandLine(context.command())
        .execute("--cwd", CliTestContext.CWD.toString(), "--resume", "-p", "and now?");

    // Assert
    assertThat(exitCode).isZero();
    ChatRequest request = context.chatModel.requests().getFirst();
    assertThat(request.model()).isEqualTo("codellama:7b-instruct");
    assertThat(request.messages()).extracting(ChatMessage::content)
        .containsExactly("earlier question", "earlier answer", "and now?");
  }

  @Test
  void execute_ResumeWithoutStoredSessions_ReportsAndExitsWithOne() {
    // Act
    int exitCode = new CommandLine(context.command())
        .execute("--cwd", CliTestContext.CWD.toString(), "--resume", "-p", "hi");

    // Assert
    assertThat(exitCode).isEqualTo(1);
    assertThat(context.terminal.plainText()).contains("✗ Сессия не найдена");
  }

  @Test
  void normalize_WithEscapedNewlines_JoinsLinesAndTrims() {
    // Act
    String input = ReplRunner.normalize("  first\\\nsecond  ");

    // Assert
    assertThat(input).isEqualTo("first\nsecond");
  }
}
