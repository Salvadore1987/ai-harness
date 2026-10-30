package dev.localllm.harness.infrastructure.in.cli;

import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.jline.terminal.Terminal;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.dto.result.CompactionResult;
import dev.localllm.harness.application.dto.result.ContextUsageResult;
import dev.localllm.harness.application.dto.result.SavedAnswerResult;
import dev.localllm.harness.application.dto.result.SessionSummaryResult;
import dev.localllm.harness.application.port.in.CompactSessionUseCase;
import dev.localllm.harness.application.port.in.GetContextUsageQuery;
import dev.localllm.harness.application.port.in.ListSessionsQuery;
import dev.localllm.harness.application.port.in.ResumeSessionUseCase;
import dev.localllm.harness.application.port.in.SaveAnswerUseCase;
import dev.localllm.harness.application.port.in.StartSessionUseCase;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * Slash commands about the session: {@code /clear /sessions /resume /compact /context /save}.
 */
@Component
public class SessionSlashCommands {

  /** Length of the identifier prefix shown by {@code /sessions}; unique per millisecond. */
  static final int SHORT_ID_LENGTH = 13;

  private static final DateTimeFormatter CREATED_AT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

  private final StartSessionUseCase startSessionUseCase;
  private final ResumeSessionUseCase resumeSessionUseCase;
  private final ListSessionsQuery listSessionsQuery;
  private final CompactSessionUseCase compactSessionUseCase;
  private final GetContextUsageQuery getContextUsageQuery;
  private final SaveAnswerUseCase saveAnswerUseCase;
  private final ReplCliMapper mapper;
  private final Terminal terminal;

  /**
   * Creates the commands.
   *
   * @param startSessionUseCase starts a new session for {@code /clear}
   * @param resumeSessionUseCase finds the session for {@code /resume}
   * @param listSessionsQuery lists sessions for {@code /sessions}
   * @param compactSessionUseCase compacts the history for {@code /compact}
   * @param getContextUsageQuery measures the context for {@code /context}
   * @param saveAnswerUseCase saves the latest answer for {@code /save}
   * @param mapper builds commands from the CLI state
   * @param terminal terminal to print to
   */
  public SessionSlashCommands(
      StartSessionUseCase startSessionUseCase,
      ResumeSessionUseCase resumeSessionUseCase,
      ListSessionsQuery listSessionsQuery,
      CompactSessionUseCase compactSessionUseCase,
      GetContextUsageQuery getContextUsageQuery,
      SaveAnswerUseCase saveAnswerUseCase,
      ReplCliMapper mapper,
      Terminal terminal) {
    this.startSessionUseCase = startSessionUseCase;
    this.resumeSessionUseCase = resumeSessionUseCase;
    this.listSessionsQuery = listSessionsQuery;
    this.compactSessionUseCase = compactSessionUseCase;
    this.getContextUsageQuery = getContextUsageQuery;
    this.saveAnswerUseCase = saveAnswerUseCase;
    this.mapper = mapper;
    this.terminal = terminal;
  }

  /**
   * {@code /clear}: starts a new session; the current one stays stored.
   *
   * @param session CLI state
   */
  public void clear(ReplSession session) {
    session.startNew(
        startSessionUseCase.execute(mapper.toStartSessionCommand(session)).sessionId());
    println("Новая сессия. Предыдущую можно продолжить через /resume");
  }

  /**
   * {@code /sessions}: lists the stored sessions of the working directory.
   *
   * @param session CLI state
   */
  public void sessions(ReplSession session) {
    List<SessionSummaryResult> sessions =
        listSessionsQuery.execute(mapper.toListSessionsCriteria(session));
    if (sessions.isEmpty()) {
      println("В этой директории нет сохранённых сессий");
      return;
    }
    PrintWriter writer = terminal.writer();
    for (SessionSummaryResult stored : sessions) {
      String id = stored.sessionId().toString().substring(0, SHORT_ID_LENGTH);
      String marker = stored.sessionId().equals(session.getSessionId()) ? "* " : "  ";
      writer.printf(Locale.ROOT, "%s%s  %s  %3d сообщ.  %s%n", marker, id,
          CREATED_AT.format(stored.createdAt()), stored.messageCount(), stored.title());
    }
    writer.println("Продолжить: /resume <id>");
    writer.flush();
  }

  /**
   * {@code /resume [id]}: switches to a stored session, the latest one without an argument.
   *
   * @param session CLI state
   * @param reference identifier or prefix, empty for the latest session
   */
  public void resume(ReplSession session, String reference) {
    SessionSummaryResult resumed =
        resumeSessionUseCase.execute(mapper.toResumeSessionCommand(session, reference));
    session.resume(resumed);
    println("Сессия «" + resumed.title() + "» (" + resumed.messageCount() + " сообщ.)");
  }

  /**
   * {@code /compact}: compacts the history of the current session.
   *
   * @param session CLI state
   */
  public void compact(ReplSession session) {
    CompactionResult result =
        compactSessionUseCase.execute(mapper.toCompactSessionCommand(session));
    if (!result.compacted()) {
      println("Сжимать нечего: вся история помещается в хвост, который остаётся дословно");
      return;
    }
    println(String.format(Locale.ROOT, "История сжата%s: ~%d → ~%d токенов",
        result.summarized() ? "" : " без сводки (модель не ответила)",
        result.tokensBefore(), result.tokensAfter()));
  }

  /**
   * {@code /context}: shows how full the context window is.
   *
   * @param session CLI state
   */
  public void context(ReplSession session) {
    ContextUsageResult usage =
        getContextUsageQuery.execute(mapper.toContextUsageCriteria(session));
    println(String.format(Locale.ROOT, "Контекст: ~%d из %d токенов (%d %%)",
        usage.usedTokens(), usage.budgetTokens(), usage.percent()));
  }

  /**
   * {@code /save [file]}: writes the latest answer to a file as it is stored, without asking the
   * model again.
   *
   * @param session CLI state
   * @param path target file, empty for {@code answer-<date>-<time>.md}
   */
  public void save(ReplSession session, String path) {
    SavedAnswerResult result =
        saveAnswerUseCase.execute(mapper.toSaveAnswerCommand(session, path));
    println(String.format(Locale.ROOT, "Ответ сохранён в %s (%d симв.)",
        result.file(), result.characters()));
  }

  private void println(String text) {
    terminal.writer().println(text);
    terminal.writer().flush();
  }
}
