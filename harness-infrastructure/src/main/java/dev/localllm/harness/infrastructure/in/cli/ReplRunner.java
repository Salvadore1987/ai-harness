package dev.localllm.harness.infrastructure.in.cli;

import java.nio.file.Path;
import java.util.Locale;

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.Nullable;
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
  private static final String AGENT_PROMPT = "agent› ";
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
    LineReader reader = createReader(session);
    printBanner(session);
    for (String input = readInput(reader, session); input != null;
        input = readInput(reader, session)) {
      if (handle(input, session) == SlashOutcome.EXIT) {
        return;
      }
    }
  }

  private @Nullable String readInput(LineReader reader, ReplSession session) {
    boolean exitArmed = false;
    while (true) {
      try {
        return normalize(
            reader.readLine(session.getMode() == ReplMode.AGENT ? AGENT_PROMPT : PROMPT));
      } catch (UserInterruptException e) {
        boolean emptyPrompt = e.getPartialLine().isEmpty();
        if (emptyPrompt && exitArmed) {
          return null;
        }
        exitArmed = emptyPrompt;
        if (exitArmed) {
          hint("Нажмите Ctrl+C ещё раз для выхода");
        }
      } catch (EndOfFileException e) {
        return null;
      }
    }
  }

  static String normalize(String line) {
    return line.replace("\\\n", "\n").strip();
  }

  private SlashOutcome handle(String input, ReplSession session) {
    if (input.startsWith("/")) {
      return dispatcher.dispatch(input, session);
    }
    if (!input.isEmpty()) {
      turnExecutor.execute(session, input);
    }
    return SlashOutcome.CONTINUE;
  }

  private LineReader createReader(ReplSession session) {
    DefaultParser parser = new DefaultParser();
    parser.setEofOnEscapedNewLine(true);
    parser.setEofOnUnclosedQuote(false);
    parser.setQuoteChars(new char[0]);
    LineReader reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .appName("harness")
        .parser(parser)
        .completer(completer(session))
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

  private static Completer completer(ReplSession session) {
    return new AggregateCompleter(
        new SlashCommandCompleter(), new FileMentionCompleter(session.getWorkingDirectory()));
  }

  private void printBanner(ReplSession session) {
    String title = session.getTitle();
    hint("harness · модель " + session.getModel()
        + " · режим " + session.getMode().name().toLowerCase(Locale.ROOT)
        + " · размышления " + (session.isThinkingEnabled() ? "on" : "off")
        + " · " + session.getWorkingDirectory()
        + " · /help — команды");
    if (title != null) {
      hint("продолжаю сессию «" + title + "»");
    }
  }

  private void hint(String text) {
    terminal.writer().println(new AttributedString(text, HINT_STYLE).toAnsi(terminal));
    terminal.writer().flush();
  }
}
