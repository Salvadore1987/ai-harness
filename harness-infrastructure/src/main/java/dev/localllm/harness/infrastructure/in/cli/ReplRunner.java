package dev.localllm.harness.infrastructure.in.cli;

import java.nio.file.Path;

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.stereotype.Component;

import dev.localllm.harness.infrastructure.config.HarnessProperties;

/**
 * Interactive read-eval-print loop on JLine.
 *
 * <p>Ctrl+C on an empty prompt arms the exit, a second consecutive Ctrl+C leaves; Ctrl+D leaves
 * immediately. A trailing backslash or Alt+Enter continues the input on the next line.
 */
@Component
public class ReplRunner {

  private static final String PROMPT = "› ";
  private static final String INSERT_NEWLINE_WIDGET = "harness-insert-newline";
  private static final AttributedStyle HINT_STYLE = AttributedStyle.DEFAULT.faint();

  private final Terminal terminal;
  private final TurnExecutor turnExecutor;
  private final SlashCommandDispatcher dispatcher;
  private final Path historyFile;

  /**
   * Creates the REPL.
   *
   * @param terminal terminal to read from and print to
   * @param turnExecutor runs chat turns
   * @param dispatcher executes slash commands
   * @param properties harness settings with the home directory for the input history
   */
  public ReplRunner(
      Terminal terminal,
      TurnExecutor turnExecutor,
      SlashCommandDispatcher dispatcher,
      HarnessProperties properties) {
    this.terminal = terminal;
    this.turnExecutor = turnExecutor;
    this.dispatcher = dispatcher;
    this.historyFile = properties.home().resolve("history");
  }

  /**
   * Reads and executes input until the user leaves.
   *
   * @param session CLI state
   */
  public void run(ReplSession session) {
    LineReader reader = createReader();
    printBanner(session);
    boolean exitArmed = false;
    while (true) {
      String line;
      try {
        line = reader.readLine(PROMPT);
      } catch (UserInterruptException e) {
        if (!e.getPartialLine().isEmpty()) {
          exitArmed = false;
          continue;
        }
        if (exitArmed) {
          return;
        }
        exitArmed = true;
        hint("Нажмите Ctrl+C ещё раз для выхода");
        continue;
      } catch (EndOfFileException e) {
        return;
      }
      exitArmed = false;
      String input = normalize(line);
      if (input.isEmpty()) {
        continue;
      }
      if (input.startsWith("/")) {
        if (dispatcher.dispatch(input, session) == SlashOutcome.EXIT) {
          return;
        }
        continue;
      }
      turnExecutor.execute(session, input);
    }
  }

  static String normalize(String line) {
    return line.replace("\\\n", "\n").strip();
  }

  private LineReader createReader() {
    DefaultParser parser = new DefaultParser();
    parser.setEofOnEscapedNewLine(true);
    parser.setEofOnUnclosedQuote(false);
    parser.setQuoteChars(new char[0]);
    LineReader reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .appName("harness")
        .parser(parser)
        .completer(new SlashCommandCompleter())
        .variable(LineReader.HISTORY_FILE, historyFile)
        .variable(LineReader.SECONDARY_PROMPT_PATTERN, "  ")
        .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
        .build();
    reader.getWidgets().put(INSERT_NEWLINE_WIDGET, () -> {
      reader.getBuffer().write('\n');
      return true;
    });
    KeyMap<Binding> mainKeyMap = reader.getKeyMaps().get(LineReader.MAIN);
    if (mainKeyMap != null) {
      mainKeyMap.bind(new Reference(INSERT_NEWLINE_WIDGET), KeyMap.alt('\r'));
    }
    return reader;
  }

  private void printBanner(ReplSession session) {
    hint("harness · модель " + session.getModel()
        + " · размышления " + (session.isThinkingEnabled() ? "on" : "off")
        + " · " + session.getWorkingDirectory()
        + " · /help — команды");
  }

  private void hint(String text) {
    terminal.writer().println(new AttributedString(text, HINT_STYLE).toAnsi(terminal));
    terminal.writer().flush();
  }
}
