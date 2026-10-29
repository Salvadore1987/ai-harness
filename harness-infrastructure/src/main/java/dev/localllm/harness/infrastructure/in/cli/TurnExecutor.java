package dev.localllm.harness.infrastructure.in.cli;

import java.util.Locale;
import java.util.Optional;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.port.in.CancelTurnUseCase;
import dev.localllm.harness.application.port.in.SendMessageUseCase;
import dev.localllm.harness.infrastructure.in.cli.handlers.CliErrorReporter;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * Runs one chat turn from the CLI: Ctrl+C cancels the generation, failures are reported.
 */
@Component
public class TurnExecutor {

  private static final AttributedStyle STATS_STYLE = AttributedStyle.DEFAULT.faint();
  private static final AttributedStyle WARNING_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);

  private final SendMessageUseCase sendMessageUseCase;
  private final CancelTurnUseCase cancelTurnUseCase;
  private final ReplCliMapper mapper;
  private final FileMentionExpander fileMentionExpander;
  private final CliErrorReporter errorReporter;
  private final Terminal terminal;

  /**
   * Creates the executor.
   *
   * @param sendMessageUseCase runs the turn
   * @param cancelTurnUseCase cancels the running turn on Ctrl+C
   * @param mapper builds commands from the CLI state
   * @param fileMentionExpander attaches files mentioned as {@code @path}
   * @param errorReporter prints failures
   * @param terminal terminal receiving the interrupt signal
   */
  public TurnExecutor(
      SendMessageUseCase sendMessageUseCase,
      CancelTurnUseCase cancelTurnUseCase,
      ReplCliMapper mapper,
      FileMentionExpander fileMentionExpander,
      CliErrorReporter errorReporter,
      Terminal terminal) {
    this.sendMessageUseCase = sendMessageUseCase;
    this.cancelTurnUseCase = cancelTurnUseCase;
    this.mapper = mapper;
    this.fileMentionExpander = fileMentionExpander;
    this.errorReporter = errorReporter;
    this.terminal = terminal;
  }

  /**
   * Attaches mentioned files, sends the message and waits for the answer.
   *
   * @param session CLI state
   * @param text user message
   * @return the finished turn, or empty if it failed (the failure is already reported)
   */
  public Optional<TurnResult> execute(ReplSession session, String text) {
    Terminal.SignalHandler previous = terminal.handle(Terminal.Signal.INT,
        signal -> cancelTurnUseCase.execute(mapper.toCancelTurnCommand(session)));
    try {
      FileMentionExpander.Expansion expansion =
          fileMentionExpander.expand(text, session.getWorkingDirectory());
      expansion.problems().forEach(problem -> printStyled("⚠ " + problem, WARNING_STYLE));
      TurnResult result = sendMessageUseCase.execute(
          mapper.toSendMessageCommand(session, expansion.text()));
      if (session.isVerbose()) {
        printStats(result);
      }
      return Optional.of(result);
    } catch (RuntimeException e) {
      errorReporter.report(e);
      return Optional.empty();
    } finally {
      terminal.handle(Terminal.Signal.INT, previous);
    }
  }

  private void printStats(TurnResult result) {
    double seconds = result.elapsed().toMillis() / 1000.0;
    String stats;
    if (result.completionTokens() == 0) {
      stats = String.format(Locale.ROOT, "· %.1f с · сервер не сообщил число токенов", seconds);
    } else {
      stats = String.format(Locale.ROOT,
          "· промпт %d ток. · ответ %d ток. · %.1f ток/с · %.1f с",
          result.promptTokens(), result.completionTokens(),
          result.completionTokens() / Math.max(seconds, 0.001), seconds);
    }
    printStyled(stats, STATS_STYLE);
  }

  private void printStyled(String text, AttributedStyle style) {
    terminal.writer().println(new AttributedString(text, style).toAnsi(terminal));
    terminal.writer().flush();
  }
}
