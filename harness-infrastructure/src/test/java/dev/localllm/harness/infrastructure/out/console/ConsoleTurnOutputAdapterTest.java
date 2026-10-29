package dev.localllm.harness.infrastructure.out.console;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.localllm.harness.application.dto.result.CompactionResult;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.model.TurnStatus;
import dev.localllm.harness.infrastructure.TestTerminals;
import dev.localllm.harness.infrastructure.config.HarnessProperties.ThinkingDisplay;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleTurnOutputAdapterTest {

  private final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();

  @Test
  void onThinking_WithStreamDisplay_PrintsDimPrefixedReasoningBeforeAnswer() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onThinking("Let me ");
    adapter.onThinking("think");
    adapter.onContent("42");
    adapter.onComplete(result(TurnStatus.COMPLETED));

    // Assert
    assertThat(terminal.plainText()).isEqualTo("✻ Let me think\n\n42\n");
    assertThat(terminal.rawText()).contains("\u001B[");
  }

  @Test
  void onThinking_WithSummaryDisplay_PrintsOneSummaryLine() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.SUMMARY);

    // Act
    adapter.onThinking("a".repeat(35));
    adapter.onContent("answer");
    adapter.onComplete(result(TurnStatus.COMPLETED));

    // Assert
    assertThat(terminal.plainText())
        .matches("(?s)✻ размышлял \\d+\\.\\d с \\(~10 токенов\\)\nanswer\n")
        .doesNotContain("aaaa");
  }

  @Test
  void onThinking_OnNonInteractiveTerminal_KeepsStdoutForTheAnswerOnly() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.dumb();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onThinking("reasoning");
    adapter.onContent("Hi");
    adapter.onComplete(result(TurnStatus.COMPLETED));

    // Assert
    assertThat(terminal.plainText()).isEqualTo("Hi\n");
    assertThat(diagnostics.toString(StandardCharsets.UTF_8)).startsWith("✻ размышлял");
  }

  @Test
  void onComplete_WhenCancelled_PrintsStoppedNotice() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onContent("part");
    adapter.onComplete(result(TurnStatus.CANCELLED));

    // Assert
    assertThat(terminal.plainText()).isEqualTo("part\n⏹ генерация остановлена\n");
  }

  @Test
  void onComplete_WhenInterruptedDuringThinking_ClosesReasoningAndWarns() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onThinking("hmm");
    adapter.onComplete(result(TurnStatus.INTERRUPTED));

    // Assert
    assertThat(terminal.plainText())
        .isEqualTo("✻ hmm\n\n⚠ ответ прерван: соединение с Ollama оборвалось\n");
  }

  @Test
  void onCompaction_WithSummary_PrintsTokenReduction() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onCompaction(new CompactionResult(true, true, 52000, 8000));

    // Assert
    assertThat(terminal.plainText()).isEqualTo("↻ история сжата: ~52000 → ~8000 токенов\n");
  }

  @Test
  void onCompaction_WhenNothingToCompact_Warns() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onCompaction(new CompactionResult(false, false, 60000, 60000));

    // Assert
    assertThat(terminal.plainText()).startsWith("⚠ контекст почти заполнен");
  }

  private ConsoleTurnOutputAdapter adapter(
      TestTerminals.Captured terminal, ThinkingDisplay display) {
    return new ConsoleTurnOutputAdapter(terminal.terminal(), display,
        new PrintStream(diagnostics, true, StandardCharsets.UTF_8));
  }

  private static TurnResult result(TurnStatus status) {
    return new TurnResult("", status, 1, 1, Duration.ofSeconds(1));
  }
}
