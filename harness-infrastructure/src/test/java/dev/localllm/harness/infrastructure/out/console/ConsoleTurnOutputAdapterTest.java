package dev.localllm.harness.infrastructure.out.console;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.localllm.harness.application.dto.result.CompactionResult;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.TurnStatus;
import dev.localllm.harness.infrastructure.TestTerminals;
import dev.localllm.harness.infrastructure.config.DisplayOptions;
import dev.localllm.harness.infrastructure.config.HarnessProperties.ThinkingDisplay;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleTurnOutputAdapterTest {

  private final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
  private final DisplayOptions displayOptions = new DisplayOptions();

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

  @Test
  void onToolCall_AfterAnswerText_PrintsCallWithArgumentValuesAndResultPreview() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onContent("Reading");
    adapter.onToolCall(new ToolCall("c1", "read_file",
        "{\"path\":\"src/Calc.java\",\"offset\":10,\"limit\":null}"));
    adapter.onToolResult(new ToolResult("c1", "read_file", "l1\nl2\nl3\nl4\nl5",
        false, false, Duration.ofMillis(12)));

    // Assert
    assertThat(terminal.plainText()).isEqualTo("""
        Reading

        ⏺ read_file(src/Calc.java, 10)
          ⎿ l1
            l2
            l3
            … ещё 2 строк (--verbose — полностью)

        """);
  }

  @Test
  void onToolResult_WhenVerbose_PrintsWholeOutputAndDuration() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);
    displayOptions.setVerbose(true);

    // Act
    adapter.onToolCall(new ToolCall("c1", "glob", "{\"pattern\":\"**/*.java\"}"));
    adapter.onToolResult(new ToolResult("c1", "glob", "a\nb\nc\nd\ne",
        false, true, Duration.ofMillis(1500)));

    // Assert
    assertThat(terminal.plainText())
        .contains("⏺ glob(**/*.java)\n  ⎿ a\n    b\n    c\n    d\n    e\n")
        .contains("1.5 с · вывод обрезан");
  }

  @Test
  void onToolCall_DuringThinking_EndsThinkingFirst() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onThinking("need the file");
    adapter.onToolCall(new ToolCall("c1", "list_dir", "{}"));
    adapter.onToolResult(new ToolResult("c1", "list_dir", "", true, false, Duration.ZERO));
    adapter.onThinking("next");

    // Assert
    assertThat(terminal.plainText()).isEqualTo(
        "✻ need the file\n\n⏺ list_dir()\n  ⎿ (пустой вывод)\n\n✻ next");
  }

  @Test
  void onToolCall_OnNonInteractiveTerminal_PrintsCallsToDiagnostics() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.dumb();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onToolCall(new ToolCall("c1", "glob", "{\"pattern\":\"*.java\"}"));
    adapter.onToolResult(new ToolResult("c1", "glob", "A.java", false, false, Duration.ZERO));
    adapter.onContent("answer");
    adapter.onComplete(result(TurnStatus.COMPLETED));

    // Assert
    assertThat(terminal.plainText()).isEqualTo("answer\n");
    assertThat(diagnostics.toString(StandardCharsets.UTF_8))
        .isEqualTo("⏺ glob(*.java)\n  ⎿ A.java\n");
  }

  @Test
  void onToolResult_WithNumberedLines_KeepsIndentAndReplacesTabs() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onToolResult(new ToolResult("c1", "read_file", "     1\tclass A {}\n",
        false, false, Duration.ZERO));

    // Assert
    assertThat(terminal.plainText()).isEqualTo("  ⎿      1  class A {}\n\n");
  }

  @Test
  void onToolCall_WithInvalidJsonArguments_ShowsThemAsIs() {
    // Arrange
    TestTerminals.Captured terminal = TestTerminals.interactive();
    ConsoleTurnOutputAdapter adapter = adapter(terminal, ThinkingDisplay.STREAM);

    // Act
    adapter.onToolCall(new ToolCall("c1", "grep", "not json"));

    // Assert
    assertThat(terminal.plainText()).isEqualTo("⏺ grep(not json)\n");
  }

  private ConsoleTurnOutputAdapter adapter(
      TestTerminals.Captured terminal, ThinkingDisplay display) {
    return new ConsoleTurnOutputAdapter(terminal.terminal(), display,
        new PrintStream(diagnostics, true, StandardCharsets.UTF_8), displayOptions);
  }

  private static TurnResult result(TurnStatus status) {
    return new TurnResult("", status, 1, 1, Duration.ofSeconds(1));
  }
}
