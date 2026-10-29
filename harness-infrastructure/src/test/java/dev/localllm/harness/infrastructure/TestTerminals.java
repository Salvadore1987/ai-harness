package dev.localllm.harness.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.terminal.impl.ExternalTerminal;
import org.jline.utils.AttributedString;

/**
 * In-memory terminals capturing everything written to them.
 */
public final class TestTerminals {

  private TestTerminals() {
  }

  /**
   * A terminal with a captured output buffer.
   *
   * @param terminal the terminal
   * @param output bytes written to it
   */
  public record Captured(Terminal terminal, ByteArrayOutputStream output) {

    /**
     * Output written so far, without ANSI escape sequences.
     *
     * @return plain text output
     */
    public String plainText() {
      terminal.writer().flush();
      return AttributedString.fromAnsi(output.toString(StandardCharsets.UTF_8)).toString()
          .replace("\r\n", "\n");
    }

    /**
     * Output written so far, including ANSI escape sequences.
     *
     * @return raw output
     */
    public String rawText() {
      terminal.writer().flush();
      return output.toString(StandardCharsets.UTF_8);
    }
  }

  /**
   * Creates an interactive xterm-like terminal.
   *
   * @return captured terminal
   */
  public static Captured interactive() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      return new Captured(new ExternalTerminal("test", "xterm-256color",
          new ByteArrayInputStream(new byte[0]), output, StandardCharsets.UTF_8), output);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Creates a non-interactive terminal, as used when stdout is redirected.
   *
   * @return captured terminal
   */
  public static Captured dumb() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      return new Captured(new DumbTerminal("test", Terminal.TYPE_DUMB,
          new ByteArrayInputStream(new byte[0]), output, StandardCharsets.UTF_8), output);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
