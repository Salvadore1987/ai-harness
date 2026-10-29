package dev.localllm.harness.infrastructure.in.cli;

import org.junit.jupiter.api.Test;

import dev.localllm.harness.application.model.SessionId;

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
  }

  @Test
  void dispatch_UnknownCommand_PrintsHint() {
    // Act
    dispatcher.dispatch("/frobnicate", session);

    // Assert
    assertThat(context.terminal.plainText()).contains("Неизвестная команда /frobnicate");
  }
}
