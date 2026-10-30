package dev.localllm.harness.infrastructure.out.console;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.dto.result.CompactionResult;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.port.out.TurnOutputPort;
import dev.localllm.harness.infrastructure.config.DisplayOptions;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties.ThinkingDisplay;

/**
 * Prints a streamed turn to the terminal.
 *
 * <p>Reasoning is shown dim with a {@code ✻} prefix ({@code stream} display) or as one summary
 * line ({@code summary} display). On a non-interactive terminal, for example {@code harness -p}
 * with redirected output, only the answer goes to stdout and the reasoning summary goes to stderr.
 * Tool calls are shown as {@code ⏺ name(arguments)} followed by the first lines of the result,
 * or the whole result with {@code --verbose}; on a non-interactive terminal they go to stderr.
 * The diff of {@code edit_file} is shown in full up to 40 lines, added lines green and removed
 * lines red.
 */
@Component
public class ConsoleTurnOutputAdapter implements TurnOutputPort {

  private static final double CHARS_PER_TOKEN = 3.5;
  private static final String THINKING_PREFIX = "✻ ";
  private static final AttributedStyle THINKING_STYLE = AttributedStyle.DEFAULT.italic();
  private static final AttributedStyle WARNING_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
  private static final AttributedStyle ERROR_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
  private static final AttributedStyle TOOL_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
  private static final AttributedStyle TOOL_OUTPUT_STYLE = AttributedStyle.DEFAULT.faint();
  private static final AttributedStyle ADDED_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
  private static final AttributedStyle REMOVED_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
  private static final AttributedStyle HUNK_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
  private static final String TOOL_PREFIX = "⏺ ";
  private static final String RESULT_PREFIX = "  ⎿ ";
  private static final String RESULT_INDENT = "    ";

  private final Terminal terminal;
  private final ThinkingDisplay display;
  private final PrintStream diagnostics;
  private final DisplayOptions displayOptions;
  private final ToolCallFormatter formatter = new ToolCallFormatter();

  private Phase phase = Phase.IDLE;
  private long thinkingStartedAt;
  private int thinkingChars;
  private boolean atLineStart = true;

  /**
   * Creates the adapter.
   *
   * @param terminal terminal to print to
   * @param properties harness settings with the thinking display mode
   * @param displayOptions command line display flags
   */
  @Autowired
  public ConsoleTurnOutputAdapter(
      Terminal terminal, HarnessProperties properties, DisplayOptions displayOptions) {
    this(terminal, properties.thinking().display(), System.err, displayOptions);
  }

  ConsoleTurnOutputAdapter(Terminal terminal, ThinkingDisplay display, PrintStream diagnostics,
      DisplayOptions displayOptions) {
    this.terminal = terminal;
    this.display = display;
    this.diagnostics = diagnostics;
    this.displayOptions = displayOptions;
  }

  @Override
  public synchronized void onThinking(String text) {
    if (phase == Phase.IDLE) {
      phase = Phase.THINKING;
      thinkingStartedAt = System.nanoTime();
      thinkingChars = 0;
      if (isStreamingThinking()) {
        print(styled(THINKING_PREFIX, THINKING_STYLE));
      }
    }
    thinkingChars += text.length();
    if (isStreamingThinking()) {
      print(styled(text, THINKING_STYLE));
    }
  }

  @Override
  public synchronized void onContent(String text) {
    if (phase == Phase.THINKING) {
      finishThinking();
    }
    phase = Phase.ANSWERING;
    print(text);
  }

  @Override
  public synchronized void onToolCall(ToolCall call) {
    if (phase == Phase.THINKING) {
      finishThinking();
    }
    String formatted = formatter.call(call);
    if (isInteractive()) {
      endLine();
      if (phase == Phase.ANSWERING) {
        println("");
      }
      println(styled(TOOL_PREFIX, TOOL_STYLE) + formatted);
      writer().flush();
    } else {
      diagnostics.println(TOOL_PREFIX + formatted);
      diagnostics.flush();
    }
    phase = Phase.IDLE;
  }

  @Override
  public synchronized void onToolResult(ToolResult result) {
    boolean verbose = displayOptions.isVerbose();
    List<String> lines = new ArrayList<>(formatter.outputLines(result, verbose));
    if (verbose) {
      lines.add(String.format(Locale.ROOT, "%.1f с%s", result.duration().toMillis() / 1000.0,
          result.truncated() ? " · вывод обрезан" : ""));
    }
    if (isInteractive()) {
      printStyledResult(result, lines);
    } else {
      for (int i = 0; i < lines.size(); i++) {
        diagnostics.println(resultPrefix(i) + lines.get(i));
      }
      diagnostics.flush();
    }
  }

  private void printStyledResult(ToolResult result, List<String> lines) {
    AttributedStyle style = result.error() ? ERROR_STYLE : TOOL_OUTPUT_STYLE;
    boolean diff = ToolCallFormatter.isDiff(result);
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      println(resultPrefix(i) + styled(line, diff && i > 0 ? diffStyle(line) : style));
    }
    println("");
    writer().flush();
  }

  private static String resultPrefix(int index) {
    return index == 0 ? RESULT_PREFIX : RESULT_INDENT;
  }

  @Override
  public synchronized void onComplete(TurnResult result) {
    if (phase == Phase.THINKING) {
      finishThinking();
    }
    endLine();
    switch (result.status()) {
      case CANCELLED -> println(styled("⏹ генерация остановлена", WARNING_STYLE));
      case INTERRUPTED -> println(styled("⚠ ответ прерван: соединение с Ollama оборвалось",
          ERROR_STYLE));
      case COMPLETED -> {
        // the answer itself is the whole output
      }
    }
    phase = Phase.IDLE;
    writer().flush();
  }

  @Override
  public synchronized void onCompaction(CompactionResult result) {
    String message;
    if (!result.compacted()) {
      message = "⚠ контекст почти заполнен, но сжимать нечего: последние сообщения слишком длинные";
    } else if (result.summarized()) {
      message = String.format(Locale.ROOT, "↻ история сжата: ~%d → ~%d токенов",
          result.tokensBefore(), result.tokensAfter());
    } else {
      message = String.format(Locale.ROOT,
          "↻ старые сообщения отброшены без сводки: ~%d → ~%d токенов",
          result.tokensBefore(), result.tokensAfter());
    }
    endLine();
    println(styled(message, result.compacted() ? THINKING_STYLE : WARNING_STYLE));
    writer().flush();
  }

  private static AttributedStyle diffStyle(String line) {
    if (line.startsWith("+")) {
      return ADDED_STYLE;
    }
    if (line.startsWith("-")) {
      return REMOVED_STYLE;
    }
    return line.startsWith("@@") ? HUNK_STYLE : TOOL_OUTPUT_STYLE;
  }

  private void finishThinking() {
    if (isStreamingThinking()) {
      endLine();
      println("");
      return;
    }
    String summary = thinkingSummary();
    if (isInteractive()) {
      println(styled(summary, THINKING_STYLE));
    } else {
      diagnostics.println(summary);
      diagnostics.flush();
    }
  }

  private String thinkingSummary() {
    Duration elapsed = Duration.ofNanos(System.nanoTime() - thinkingStartedAt);
    long tokens = Math.round(thinkingChars / CHARS_PER_TOKEN);
    return String.format(Locale.ROOT, "%sразмышлял %.1f с (~%d токенов)",
        THINKING_PREFIX, elapsed.toMillis() / 1000.0, tokens);
  }

  private boolean isStreamingThinking() {
    return display == ThinkingDisplay.STREAM && isInteractive();
  }

  private boolean isInteractive() {
    String type = terminal.getType();
    return !Terminal.TYPE_DUMB.equals(type) && !Terminal.TYPE_DUMB_COLOR.equals(type);
  }

  private String styled(String text, AttributedStyle style) {
    return new AttributedString(text, style).toAnsi(terminal);
  }

  private void print(String text) {
    if (text.isEmpty()) {
      return;
    }
    writer().print(text);
    writer().flush();
    atLineStart = text.endsWith("\n");
  }

  private void println(String text) {
    writer().println(text);
    atLineStart = true;
  }

  private void endLine() {
    if (!atLineStart) {
      println("");
    }
  }

  private PrintWriter writer() {
    return terminal.writer();
  }

  private enum Phase {
    IDLE,
    THINKING,
    ANSWERING
  }
}
