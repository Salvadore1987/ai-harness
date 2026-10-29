package dev.localllm.harness.infrastructure.out.mcp;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Collects problems of the MCP startup and prints them as warnings.
 *
 * <p>Servers connect on background threads while the REPL owns the terminal, so problems are
 * queued and printed by {@link #flush()} on the thread that waits for the MCP tools. Everything
 * is also written to the log.
 */
@Component
public class McpStartupReporter {

  private static final Logger log = LoggerFactory.getLogger(McpStartupReporter.class);
  private static final AttributedStyle WARNING_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);

  private final Terminal terminal;
  private final Queue<String> warnings = new ConcurrentLinkedQueue<>();

  /**
   * Creates the reporter.
   *
   * @param terminal terminal receiving the warnings
   */
  public McpStartupReporter(Terminal terminal) {
    this.terminal = terminal;
  }

  /**
   * Records a server that connected.
   *
   * @param server server name
   * @param tools number of its tools
   */
  public void connected(String server, int tools) {
    log.info("MCP server {} connected with {} tools", server, tools);
  }

  /**
   * Records a server that could not be connected; its tools stay unavailable.
   *
   * @param server server name
   * @param error the failure
   */
  public void failed(String server, Throwable error) {
    log.warn("MCP server {} is unavailable", server, error);
    String reason = hasCause(error, TimeoutException.class)
        ? "нет ответа на инициализацию" : describe(error);
    warnings.add("MCP-сервер «" + server + "» недоступен: " + reason);
  }

  /**
   * Records a configuration problem that does not stop the other servers.
   *
   * @param message what is wrong, in the user's language
   */
  public void problem(String message) {
    log.warn("MCP configuration problem: {}", message);
    warnings.add(message);
  }

  /**
   * Prints the warnings recorded since the last call.
   */
  public void flush() {
    String warning = warnings.poll();
    while (warning != null) {
      terminal.writer().println(new AttributedString("⚠ " + warning, WARNING_STYLE)
          .toAnsi(terminal));
      warning = warnings.poll();
    }
    terminal.writer().flush();
  }

  private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (type.isInstance(cause)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Message of the innermost cause, which names the actual problem (a missing executable, a
   * refused connection) rather than the wrappers around it.
   *
   * @param error the failure
   * @return short description
   */
  static String describe(Throwable error) {
    Throwable cause = error;
    Throwable next = cause.getCause();
    while (next != null) {
      cause = next;
      next = cause.getCause();
    }
    String message = cause.getMessage();
    return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
  }
}
