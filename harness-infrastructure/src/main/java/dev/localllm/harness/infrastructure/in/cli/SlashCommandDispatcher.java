package dev.localllm.harness.infrastructure.in.cli;

import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

import org.jline.terminal.Terminal;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.dto.result.ThinkingToggleResult;
import dev.localllm.harness.application.port.in.ListModelsQuery;
import dev.localllm.harness.application.port.in.StartSessionUseCase;
import dev.localllm.harness.application.port.in.SwitchModelUseCase;
import dev.localllm.harness.application.port.in.ToggleThinkingUseCase;
import dev.localllm.harness.infrastructure.in.cli.handlers.CliErrorReporter;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * Executes the REPL slash commands {@code /help /exit /clear /model /think}.
 */
@Component
public class SlashCommandDispatcher {

  /** Names of the supported commands, used for completion. */
  public static final List<String> COMMANDS =
      List.of("/help", "/exit", "/clear", "/model", "/think");

  private static final String HELP = """
      /help                 список команд
      /exit                 выход (также Ctrl+D или дважды Ctrl+C)
      /clear                новая сессия, история очищается
      /model [имя]          сменить модель; без имени — список установленных
      /think [on|off]       режим размышлений; без аргумента — текущее состояние
      Многострочный ввод: \\ в конце строки или Alt+Enter. Ctrl+C прерывает генерацию.""";

  private final StartSessionUseCase startSessionUseCase;
  private final SwitchModelUseCase switchModelUseCase;
  private final ToggleThinkingUseCase toggleThinkingUseCase;
  private final ListModelsQuery listModelsQuery;
  private final ReplCliMapper mapper;
  private final CliErrorReporter errorReporter;
  private final Terminal terminal;

  /**
   * Creates the dispatcher.
   *
   * @param startSessionUseCase starts a new session for {@code /clear}
   * @param switchModelUseCase validates the model for {@code /model}
   * @param toggleThinkingUseCase validates the thinking state for {@code /think}
   * @param listModelsQuery lists models for {@code /model} without arguments
   * @param mapper builds commands from the CLI state
   * @param errorReporter prints failures
   * @param terminal terminal to print to
   */
  public SlashCommandDispatcher(
      StartSessionUseCase startSessionUseCase,
      SwitchModelUseCase switchModelUseCase,
      ToggleThinkingUseCase toggleThinkingUseCase,
      ListModelsQuery listModelsQuery,
      ReplCliMapper mapper,
      CliErrorReporter errorReporter,
      Terminal terminal) {
    this.startSessionUseCase = startSessionUseCase;
    this.switchModelUseCase = switchModelUseCase;
    this.toggleThinkingUseCase = toggleThinkingUseCase;
    this.listModelsQuery = listModelsQuery;
    this.mapper = mapper;
    this.errorReporter = errorReporter;
    this.terminal = terminal;
  }

  /**
   * Executes a slash command; failures are reported and the REPL continues.
   *
   * @param line input line starting with {@code /}
   * @param session CLI state, updated by the command
   * @return whether the REPL continues
   */
  public SlashOutcome dispatch(String line, ReplSession session) {
    String[] parts = line.trim().split("\\s+", 2);
    String argument = parts.length > 1 ? parts[1].trim() : "";
    try {
      return switch (parts[0].toLowerCase(Locale.ROOT)) {
        case "/help" -> print(HELP);
        case "/exit", "/quit" -> SlashOutcome.EXIT;
        case "/clear" -> clear(session);
        case "/model" -> argument.isEmpty() ? listModels(session) : switchModel(session, argument);
        case "/think" -> think(session, argument);
        default -> print("Неизвестная команда " + parts[0] + ". Список команд: /help");
      };
    } catch (RuntimeException e) {
      errorReporter.report(e);
      return SlashOutcome.CONTINUE;
    }
  }

  private SlashOutcome clear(ReplSession session) {
    session.setSessionId(
        startSessionUseCase.execute(mapper.toStartSessionCommand(session)).sessionId());
    return print("Новая сессия " + session.getSessionId());
  }

  private SlashOutcome listModels(ReplSession session) {
    List<ModelSummaryResult> models = listModelsQuery.execute(mapper.toListModelsCriteria(""));
    if (models.isEmpty()) {
      return print("Нет установленных моделей. Выполните: ollama pull " + session.getModel());
    }
    PrintWriter writer = terminal.writer();
    for (ModelSummaryResult model : models) {
      String marker = model.name().equals(session.getModel()) ? "* " : "  ";
      writer.printf(Locale.ROOT, "%s%-32s %6s %-8s%s%s%n", marker, model.name(),
          model.parameterSize(), model.quantization(),
          model.thinkingSupported() ? " thinking" : "",
          model.toolsSupported() ? " tools" : "");
    }
    writer.flush();
    return SlashOutcome.CONTINUE;
  }

  private SlashOutcome switchModel(ReplSession session, String model) {
    ModelSummaryResult selected = switchModelUseCase.execute(mapper.toSwitchModelCommand(model));
    boolean thinkingDisabled = session.applyModel(selected);
    String notice = thinkingDisabled
        ? " (модель не поддерживает размышления — режим выключен)"
        : "";
    return print("Модель: " + selected.name() + notice);
  }

  private SlashOutcome think(ReplSession session, String argument) {
    if (argument.isEmpty()) {
      return print("Режим размышлений: " + (session.isThinkingEnabled() ? "on" : "off"));
    }
    String state = argument.toLowerCase(Locale.ROOT);
    if (!"on".equals(state) && !"off".equals(state)) {
      return print("Использование: /think on|off");
    }
    boolean enabled = "on".equals(state);
    ThinkingToggleResult result =
        toggleThinkingUseCase.execute(mapper.toToggleThinkingCommand(session, enabled));
    session.setThinkingEnabled(result.enabled());
    return print("Режим размышлений: " + (result.enabled() ? "on" : "off"));
  }

  private SlashOutcome print(String text) {
    terminal.writer().println(text);
    terminal.writer().flush();
    return SlashOutcome.CONTINUE;
  }
}
