package dev.localllm.harness.infrastructure.in.cli;

import java.util.Locale;

import org.jline.terminal.Terminal;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.dto.result.ThinkingToggleResult;
import dev.localllm.harness.application.port.in.SwitchModelUseCase;
import dev.localllm.harness.application.port.in.ToggleThinkingUseCase;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * Slash commands about the model: {@code /model}, {@code /list}, {@code /ps} and {@code /think}.
 */
@Component
public class ModelSlashCommands {

  private final SwitchModelUseCase switchModelUseCase;
  private final ToggleThinkingUseCase toggleThinkingUseCase;
  private final ModelListPrinter modelListPrinter;
  private final ReplCliMapper mapper;
  private final Terminal terminal;

  /**
   * Creates the commands.
   *
   * @param switchModelUseCase validates the model for {@code /model}
   * @param toggleThinkingUseCase validates the thinking state for {@code /think}
   * @param modelListPrinter prints the installed and the loaded models
   * @param mapper builds commands from the CLI state
   * @param terminal terminal to print to
   */
  public ModelSlashCommands(
      SwitchModelUseCase switchModelUseCase,
      ToggleThinkingUseCase toggleThinkingUseCase,
      ModelListPrinter modelListPrinter,
      ReplCliMapper mapper,
      Terminal terminal) {
    this.switchModelUseCase = switchModelUseCase;
    this.toggleThinkingUseCase = toggleThinkingUseCase;
    this.modelListPrinter = modelListPrinter;
    this.mapper = mapper;
    this.terminal = terminal;
  }

  /**
   * {@code /model [name]}: lists the installed models or switches to one.
   *
   * @param session CLI state
   * @param argument model tag, empty to list
   */
  public void model(ReplSession session, String argument) {
    if (argument.isEmpty()) {
      modelListPrinter.printInstalled("", session.getModel());
    } else {
      switchModel(session, argument);
    }
  }

  /**
   * {@code /list [prefix]}: lists the installed models, marking the current one.
   *
   * @param session CLI state with the current model
   * @param argument name prefix, empty for all models
   */
  public void list(ReplSession session, String argument) {
    modelListPrinter.printInstalled(argument, session.getModel());
  }

  /**
   * {@code /ps [prefix]}: lists the models loaded into memory by Ollama.
   *
   * @param argument name prefix, empty for all loaded models
   */
  public void ps(String argument) {
    modelListPrinter.printRunning(argument);
  }

  /**
   * {@code /think [on|off]}: shows or changes the thinking mode.
   *
   * @param session CLI state
   * @param argument {@code on}, {@code off} or empty to show the state
   */
  public void think(ReplSession session, String argument) {
    if (argument.isEmpty()) {
      println("Режим размышлений: " + (session.isThinkingEnabled() ? "on" : "off"));
      return;
    }
    String state = argument.toLowerCase(Locale.ROOT);
    if (!"on".equals(state) && !"off".equals(state)) {
      println("Использование: /think on|off");
      return;
    }
    ThinkingToggleResult result = toggleThinkingUseCase.execute(
        mapper.toToggleThinkingCommand(session, "on".equals(state)));
    session.setThinkingEnabled(result.enabled());
    println("Режим размышлений: " + (result.enabled() ? "on" : "off"));
  }

  private void switchModel(ReplSession session, String model) {
    ModelSummaryResult selected = switchModelUseCase.execute(mapper.toSwitchModelCommand(model));
    boolean thinkingDisabled = session.applyModel(selected);
    StringBuilder notice = new StringBuilder();
    if (thinkingDisabled) {
      notice.append(" (модель не поддерживает размышления — режим выключен)");
    }
    if (session.getMode() == ReplMode.AGENT && !session.isToolsSupported()) {
      session.setMode(ReplMode.CHAT);
      notice.append(" (модель не поддерживает инструменты — режим chat)");
    }
    println("Модель: " + selected.name() + notice);
  }

  private void println(String text) {
    terminal.writer().println(text);
    terminal.writer().flush();
  }
}
