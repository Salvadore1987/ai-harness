package dev.localllm.harness.infrastructure.in.cli.handlers;

import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.exception.HarnessException;

/**
 * Prints a failure to the terminal using the first handler that supports it, and logs it.
 */
@Component
public class CliErrorReporter {

  private static final Logger log = LoggerFactory.getLogger(CliErrorReporter.class);
  private static final AttributedStyle ERROR_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);

  private final List<CliExceptionHandler> handlers;
  private final Terminal terminal;

  /**
   * Creates the reporter.
   *
   * @param handlers handlers ordered by {@link org.springframework.core.annotation.Order}
   * @param terminal terminal to print to
   */
  public CliErrorReporter(List<CliExceptionHandler> handlers, Terminal terminal) {
    this.handlers = List.copyOf(handlers);
    this.terminal = terminal;
  }

  /**
   * Reports a failure; expected harness errors are logged without a stack trace.
   *
   * @param error the failure
   */
  public void report(Throwable error) {
    String message = handlers.stream()
        .filter(handler -> handler.supports(error))
        .findFirst()
        .map(handler -> handler.describe(error))
        .orElseGet(() -> String.valueOf(error.getMessage()));
    if (error instanceof HarnessException harness) {
      log.info("Harness error {}: {}", harness.getCode(), harness.getMessage());
    } else {
      log.error("Unexpected error", error);
    }
    terminal.writer().println(new AttributedString("✗ " + message, ERROR_STYLE).toAnsi(terminal));
    terminal.writer().flush();
  }
}
