package dev.localllm.harness.infrastructure.in.cli;

import java.util.List;
import java.util.Locale;

import org.jline.terminal.Terminal;
import org.springframework.stereotype.Component;

import dev.localllm.harness.infrastructure.in.cli.handlers.CliErrorReporter;

/**
 * Routes REPL slash commands to their handlers; failures are reported and the REPL continues.
 */
@Component
public class SlashCommandDispatcher {

  /** Names of the supported commands, used for completion. */
  public static final List<String> COMMANDS = List.of("/help", "/exit", "/clear", "/sessions",
      "/resume", "/compact", "/context", "/model", "/think", "/mode", "/tools");

  private static final String HELP = """
      /help                 список команд
      /exit                 выход (также Ctrl+D или дважды Ctrl+C)
      /clear                новая сессия; текущая сохраняется
      /sessions             сохранённые сессии этой директории
      /resume [id]          продолжить сессию; без id — последнюю
      /compact              сжать историю: старая часть заменяется сводкой
      /context              сколько контекста занято
      /model [имя]          сменить модель; без имени — список установленных
      /think [on|off]       режим размышлений; без аргумента — текущее состояние
      /mode [chat|agent]    режим: chat — ответ без инструментов, agent — модель читает файлы
      /tools [префикс]      инструменты агента
      @путь                 приложить файл из рабочей директории (Tab — автодополнение)
      Многострочный ввод: \\ в конце строки или Alt+Enter. Ctrl+C прерывает генерацию.""";

  private final SessionSlashCommands sessionCommands;
  private final ModelSlashCommands modelCommands;
  private final AgentSlashCommands agentCommands;
  private final CliErrorReporter errorReporter;
  private final Terminal terminal;

  /**
   * Creates the dispatcher.
   *
   * @param sessionCommands session-related commands
   * @param modelCommands model-related commands
   * @param agentCommands agent-related commands
   * @param errorReporter prints failures
   * @param terminal terminal to print to
   */
  public SlashCommandDispatcher(
      SessionSlashCommands sessionCommands,
      ModelSlashCommands modelCommands,
      AgentSlashCommands agentCommands,
      CliErrorReporter errorReporter,
      Terminal terminal) {
    this.sessionCommands = sessionCommands;
    this.modelCommands = modelCommands;
    this.agentCommands = agentCommands;
    this.errorReporter = errorReporter;
    this.terminal = terminal;
  }

  /**
   * Executes a slash command.
   *
   * @param line input line starting with {@code /}
   * @param session CLI state, updated by the command
   * @return whether the REPL continues
   */
  public SlashOutcome dispatch(String line, ReplSession session) {
    String[] parts = line.trim().split("\\s+", 2);
    String name = parts[0].toLowerCase(Locale.ROOT);
    String argument = parts.length > 1 ? parts[1].trim() : "";
    if ("/exit".equals(name) || "/quit".equals(name)) {
      return SlashOutcome.EXIT;
    }
    try {
      switch (name) {
        case "/help" -> println(HELP);
        case "/clear" -> sessionCommands.clear(session);
        case "/sessions" -> sessionCommands.sessions(session);
        case "/resume" -> sessionCommands.resume(session, argument);
        case "/compact" -> sessionCommands.compact(session);
        case "/context" -> sessionCommands.context(session);
        case "/model" -> modelCommands.model(session, argument);
        case "/think" -> modelCommands.think(session, argument);
        case "/mode" -> agentCommands.mode(session, argument);
        case "/tools" -> agentCommands.tools(argument);
        default -> println("Неизвестная команда " + parts[0] + ". Список команд: /help");
      }
    } catch (RuntimeException e) {
      errorReporter.report(e);
    }
    return SlashOutcome.CONTINUE;
  }

  private void println(String text) {
    terminal.writer().println(text);
    terminal.writer().flush();
  }
}
