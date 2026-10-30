package dev.localllm.harness.infrastructure.in.cli;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

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
      "/resume", "/compact", "/context", "/save", "/model", "/list", "/ps", "/think", "/mode",
      "/tools");

  private static final String HELP = """
      /help                 список команд
      /exit                 выход (также Ctrl+D или дважды Ctrl+C)
      /clear                новая сессия; текущая сохраняется
      /sessions             сохранённые сессии этой директории
      /resume [id]          продолжить сессию; без id — последнюю
      /compact              сжать историю: старая часть заменяется сводкой
      /context              сколько контекста занято
      /save [файл]          сохранить последний ответ в файл дословно, без перегенерации
      /model [имя]          сменить модель; без имени — список установленных
      /list [префикс]       установленные модели
      /ps [префикс]         модели, загруженные в память Ollama
      /think [on|off]       режим размышлений; без аргумента — текущее состояние
      /mode [chat|agent]    режим: chat — ответ без инструментов, agent — модель читает файлы
      /tools [префикс]      инструменты агента
      @путь                 приложить файл из рабочей директории (Tab — автодополнение)
      Многострочный ввод: \\ в конце строки или Alt+Enter. Ctrl+C прерывает генерацию.""";

  private final Map<String, BiConsumer<ReplSession, String>> handlers;
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
    this.handlers = Map.ofEntries(
        Map.entry("/help", (session, argument) -> println(HELP)),
        Map.entry("/clear", (session, argument) -> sessionCommands.clear(session)),
        Map.entry("/sessions", (session, argument) -> sessionCommands.sessions(session)),
        Map.entry("/resume", sessionCommands::resume),
        Map.entry("/compact", (session, argument) -> sessionCommands.compact(session)),
        Map.entry("/context", (session, argument) -> sessionCommands.context(session)),
        Map.entry("/save", sessionCommands::save),
        Map.entry("/model", modelCommands::model),
        Map.entry("/list", modelCommands::list),
        Map.entry("/ps", (session, argument) -> modelCommands.ps(argument)),
        Map.entry("/think", modelCommands::think),
        Map.entry("/mode", agentCommands::mode),
        Map.entry("/tools", (session, argument) -> agentCommands.tools(argument)));
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
    BiConsumer<ReplSession, String> handler = handlers.get(name);
    if (handler == null) {
      println("Неизвестная команда " + parts[0] + ". Список команд: /help");
      return SlashOutcome.CONTINUE;
    }
    try {
      handler.accept(session, argument);
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
