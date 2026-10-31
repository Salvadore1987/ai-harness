package dev.localllm.harness.infrastructure.in.cli;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import dev.localllm.harness.application.dto.result.SessionSummaryResult;
import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;

import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandDispatcherTest {

  private final CliTestContext context = new CliTestContext();
  private final SlashCommandDispatcher dispatcher = context.dispatcher();
  private final ReplSession session = context.session();

  @Test
  void dispatch_Exit_ReturnsExit() {
    // Act
    SlashOutcome outcome = dispatcher.dispatch("/exit", session);

    // Assert
    assertThat(outcome).isEqualTo(SlashOutcome.EXIT);
  }

  @Test
  void dispatch_Help_PrintsCommandListAndContinues() {
    // Act
    SlashOutcome outcome = dispatcher.dispatch("/help", session);

    // Assert
    assertThat(outcome).isEqualTo(SlashOutcome.CONTINUE);
    assertThat(context.terminal.plainText()).contains("/model", "/think", "Ctrl+C");
  }

  @Test
  void dispatch_SaveWithFile_WritesLatestAnswerAsStored() {
    // Arrange
    context.conversation.append(session.getSessionId(), new ChatMessage(Role.USER, "Вопрос"));
    context.conversation.append(session.getSessionId(),
        new ChatMessage(Role.ASSISTANT, "**Ответ** как есть\n"));

    // Act
    SlashOutcome outcome = dispatcher.dispatch("/save notes/answer.md", session);

    // Assert
    assertThat(outcome).isEqualTo(SlashOutcome.CONTINUE);
    assertThat(context.answerFiles.files())
        .containsEntry(CliTestContext.CWD.resolve("notes/answer.md"), "**Ответ** как есть\n");
    assertThat(context.terminal.plainText())
        .contains("Ответ сохранён в /work/project/notes/answer.md (19 симв.)");
  }

  @Test
  void dispatch_SaveWithoutAnswers_ReportsAndContinues() {
    // Act
    SlashOutcome outcome = dispatcher.dispatch("/save", session);

    // Assert
    assertThat(outcome).isEqualTo(SlashOutcome.CONTINUE);
    assertThat(context.answerFiles.files()).isEmpty();
    assertThat(context.terminal.plainText()).contains("✗ Сохранять нечего");
  }

  @Test
  void dispatch_ModelWithoutArgument_ListsModelsMarkingTheCurrentOne() {
    // Act
    dispatcher.dispatch("/model", session);

    // Assert
    assertThat(context.terminal.plainText())
        .contains("* qwen3.5:27b")
        .contains("  codellama:7b-instruct")
        .contains("thinking tools");
  }

  @Test
  void dispatch_List_PrintsHeaderAndInstalledModelsMarkingTheCurrentOne() {
    // Act
    SlashOutcome outcome = dispatcher.dispatch("/list", session);

    // Assert
    assertThat(outcome).isEqualTo(SlashOutcome.CONTINUE);
    assertThat(context.terminal.plainText())
        .contains("МОДЕЛЬ", "ВОЗМОЖНОСТИ")
        .contains("* qwen3.5:27b")
        .contains("  codellama:7b-instruct");
  }

  @Test
  void dispatch_ListWithPrefix_PrintsOnlyMatchingModels() {
    // Act
    dispatcher.dispatch("/list codellama", session);

    // Assert
    assertThat(context.terminal.plainText())
        .contains("codellama:7b-instruct")
        .doesNotContain("qwen3.5:27b");
  }

  @Test
  void dispatch_Ps_PrintsLoadedModelWithSizeProcessorContextAndUnloadTime() {
    // Act
    SlashOutcome outcome = dispatcher.dispatch("/ps", session);

    // Assert
    assertThat(outcome).isEqualTo(SlashOutcome.CONTINUE);
    assertThat(context.terminal.plainText())
        .contains("МОДЕЛЬ", "ПРОЦЕССОР", "ВЫГРУЗКА")
        .contains("qwen3.5:27b", "20 GB", "100% GPU", "32768", "через 2 мин");
  }

  @Test
  void dispatch_PsWhenOllamaDown_ReportsAndContinues() {
    // Arrange
    context.runningModels.failWith(
        new LlmUnavailableException("http://localhost:11434", new IllegalStateException()));

    // Act
    SlashOutcome outcome = dispatcher.dispatch("/ps", session);

    // Assert
    assertThat(outcome).isEqualTo(SlashOutcome.CONTINUE);
    assertThat(context.terminal.plainText()).contains("Ollama недоступна");
  }

  @Test
  void dispatch_ModelSwitchToModelWithoutThinking_TurnsThinkingOff() {
    // Act
    dispatcher.dispatch("/model codellama:7b-instruct", session);

    // Assert
    assertThat(session.getModel()).isEqualTo("codellama:7b-instruct");
    assertThat(session.isThinkingEnabled()).isFalse();
    assertThat(context.terminal.plainText()).contains("режим выключен");
  }

  @Test
  void dispatch_ModelUnknown_ReportsPullHintAndKeepsCurrentModel() {
    // Act
    SlashOutcome outcome = dispatcher.dispatch("/model llama9:1t", session);

    // Assert
    assertThat(outcome).isEqualTo(SlashOutcome.CONTINUE);
    assertThat(session.getModel()).isEqualTo("qwen3.5:27b");
    assertThat(context.terminal.plainText()).contains("✗ Модель llama9:1t не найдена",
        "ollama pull llama9:1t");
  }

  @Test
  void dispatch_ThinkOff_TurnsThinkingOff() {
    // Act
    dispatcher.dispatch("/think off", session);

    // Assert
    assertThat(session.isThinkingEnabled()).isFalse();
    assertThat(context.terminal.plainText()).contains("Режим размышлений: off");
  }

  @Test
  void dispatch_ThinkOnForModelWithoutThinking_ReportsErrorAndStaysOff() {
    // Arrange
    dispatcher.dispatch("/model codellama:7b-instruct", session);

    // Act
    dispatcher.dispatch("/think on", session);

    // Assert
    assertThat(session.isThinkingEnabled()).isFalse();
    assertThat(context.terminal.plainText()).contains("✗ Режим размышлений недоступен");
  }

  @Test
  void dispatch_ThinkWithInvalidArgument_PrintsUsage() {
    // Act
    dispatcher.dispatch("/think maybe", session);

    // Assert
    assertThat(context.terminal.plainText()).contains("Использование: /think on|off");
  }

  @Test
  void dispatch_Clear_StartsNewSession() {
    // Arrange
    SessionId before = session.getSessionId();

    // Act
    dispatcher.dispatch("/clear", session);

    // Assert
    assertThat(session.getSessionId()).isNotNull().isNotEqualTo(before);
    assertThat(context.terminal.plainText()).contains("/resume");
  }

  @Test
  void dispatch_Sessions_ListsStoredSessionsWithShortIds() {
    // Arrange
    SessionInfo stored = register("Explain the build");

    // Act
    dispatcher.dispatch("/sessions", session);

    // Assert
    assertThat(context.terminal.plainText())
        .contains(stored.sessionId().toString().substring(0, 13))
        .contains("Explain the build")
        .contains("/resume <id>");
  }

  @Test
  void dispatch_Purge_DeletesOtherSessionsAndKeepsCurrent() {
    // Arrange
    SessionInfo stored = register("Earlier work");
    SessionInfo current = register("Current work");
    session.resume(new SessionSummaryResult(current.sessionId(), current.title(),
        current.model(), current.createdAt(), current.messageCount()));

    // Act
    dispatcher.dispatch("/purge", session);

    // Assert
    assertThat(context.catalog.find(stored.sessionId())).isEmpty();
    assertThat(context.catalog.find(current.sessionId())).isPresent();
    assertThat(context.terminal.plainText()).contains("Удалено сессий: 1");
  }

  @Test
  void dispatch_PurgeWithoutStoredSessions_SaysNothingToDelete() {
    // Act
    dispatcher.dispatch("/purge", session);

    // Assert
    assertThat(context.terminal.plainText()).contains("Удалять нечего");
  }

  @Test
  void dispatch_ResumeWithoutArgument_SwitchesToLatestSession() {
    // Arrange
    SessionInfo stored = register("Earlier work");

    // Act
    dispatcher.dispatch("/resume", session);

    // Assert
    assertThat(session.getSessionId()).isEqualTo(stored.sessionId());
    assertThat(session.getTitle()).isEqualTo("Earlier work");
  }

  @Test
  void dispatch_ResumeUnknown_ReportsSessionNotFound() {
    // Act
    dispatcher.dispatch("/resume ffff", session);

    // Assert
    assertThat(context.terminal.plainText()).contains("✗ Сессия не найдена");
  }

  @Test
  void dispatch_Compact_SummarizesLongHistory() {
    // Arrange
    SessionId current = session.getSessionId();
    for (String text : new String[] {"q1", "a1", "q2", "a2", "q3", "a3"}) {
      context.conversation.append(current,
          new ChatMessage(text.startsWith("q") ? Role.USER : Role.ASSISTANT, text));
    }
    context.chatModel.respondWith(new ChatChunk.Content("summary"));
    context.budget.setContextLength(40);

    // Act
    dispatcher.dispatch("/compact", session);

    // Assert
    assertThat(context.conversation.history(current)).hasSize(3);
    assertThat(context.terminal.plainText()).contains("История сжата");
  }

  @Test
  void dispatch_ModeAgent_SwitchesMode() {
    // Act
    dispatcher.dispatch("/mode agent", session);

    // Assert
    assertThat(session.getMode()).isEqualTo(ReplMode.AGENT);
    assertThat(context.terminal.plainText()).contains("Режим: agent");
  }

  @Test
  void dispatch_ModeWithoutArgument_ShowsCurrentMode() {
    // Act
    dispatcher.dispatch("/mode", session);

    // Assert
    assertThat(context.terminal.plainText()).contains("Режим: chat");
  }

  @Test
  void dispatch_ModeWithUnknownValue_PrintsUsage() {
    // Act
    dispatcher.dispatch("/mode turbo", session);

    // Assert
    assertThat(session.getMode()).isEqualTo(ReplMode.CHAT);
    assertThat(context.terminal.plainText()).contains("Использование: /mode chat|agent");
  }

  @Test
  void dispatch_ModeAgentForModelWithoutTools_KeepsChat() {
    // Arrange
    dispatcher.dispatch("/model codellama:7b-instruct", session);

    // Act
    dispatcher.dispatch("/mode agent", session);

    // Assert
    assertThat(session.getMode()).isEqualTo(ReplMode.CHAT);
    assertThat(context.terminal.plainText()).contains("не поддерживает инструменты");
  }

  @Test
  void dispatch_ModelWithoutToolsInAgentMode_FallsBackToChat() {
    // Arrange
    dispatcher.dispatch("/mode agent", session);

    // Act
    dispatcher.dispatch("/model codellama:7b-instruct", session);

    // Assert
    assertThat(session.getMode()).isEqualTo(ReplMode.CHAT);
    assertThat(context.terminal.plainText()).contains("режим chat");
  }

  @Test
  void dispatch_Tools_ListsToolsWithSource() {
    // Act
    dispatcher.dispatch("/tools", session);

    // Assert
    assertThat(context.terminal.plainText())
        .contains("read_file                builtin  Fake read_file")
        .contains("glob                     builtin  Fake glob")
        .contains("mcp__fs__list_directory  fs       Fake mcp__fs__list_directory");
  }

  @Test
  void dispatch_ToolsWithUnknownPrefix_SaysNothingMatches() {
    // Act
    dispatcher.dispatch("/tools zzz", session);

    // Assert
    assertThat(context.terminal.plainText()).contains("Нет инструментов на zzz");
  }

  @Test
  void dispatch_Context_PrintsUsage() {
    // Act
    dispatcher.dispatch("/context", session);

    // Assert
    assertThat(context.terminal.plainText()).contains("Контекст: ~0 из 65536 токенов (0 %)");
  }

  @Test
  void dispatch_UnknownCommand_PrintsHint() {
    // Act
    dispatcher.dispatch("/frobnicate", session);

    // Assert
    assertThat(context.terminal.plainText()).contains("Неизвестная команда /frobnicate");
  }

  private SessionInfo register(String title) {
    SessionInfo info = new SessionInfo(SessionId.newId(), CliTestContext.CWD, "qwen3.5:27b",
        title, Instant.parse("2026-10-29T10:00:00Z"), 4);
    context.catalog.register(info);
    return info;
  }
}
