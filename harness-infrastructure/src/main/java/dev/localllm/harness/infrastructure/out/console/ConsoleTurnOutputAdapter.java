package dev.localllm.harness.infrastructure.out.console;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Locale;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.port.out.TurnOutputPort;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties.ThinkingDisplay;

/**
 * Prints a streamed turn to the terminal.
 *
 * <p>Reasoning is shown dim with a {@code ✻} prefix ({@code stream} display) or as one summary
 * line ({@code summary} display). On a non-interactive terminal, for example {@code harness -p}
 * with redirected output, only the answer goes to stdout and the reasoning summary goes to stderr.
 */
@Component
public class ConsoleTurnOutputAdapter implements TurnOutputPort {

  private static final double CHARS_PER_TOKEN = 3.5;
  private static final String THINKING_PREFIX = "✻ ";
  private static final AttributedStyle THINKING_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT).faint();
  private static final AttributedStyle WARNING_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
  private static final AttributedStyle ERROR_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);

  private final Terminal terminal;
  private final ThinkingDisplay display;
  private final PrintStream diagnostics;

  private Phase phase = Phase.IDLE;
  private long thinkingStartedAt;
  private int thinkingChars;
  private boolean atLineStart = true;

  /**
   * Creates the adapter.
   *
   * @param terminal terminal to print to
   * @param properties harness settings with the thinking display mode
   */
  @Autowired
  public ConsoleTurnOutputAdapter(Terminal terminal, HarnessProperties properties) {
    this(terminal, properties.thinking().display(), System.err);
  }

  ConsoleTurnOutputAdapter(Terminal terminal, ThinkingDisplay display, PrintStream diagnostics) {
    this.terminal = terminal;
    this.display = display;
    this.diagnostics = diagnostics;
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
