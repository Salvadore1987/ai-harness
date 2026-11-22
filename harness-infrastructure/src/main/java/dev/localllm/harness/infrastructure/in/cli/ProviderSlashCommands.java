package dev.localllm.harness.infrastructure.in.cli;

import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jline.terminal.Terminal;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.dto.result.ProviderResult;
import dev.localllm.harness.application.dto.result.ProviderSummaryResult;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.port.in.ListProvidersQuery;
import dev.localllm.harness.application.port.in.StartSessionUseCase;
import dev.localllm.harness.application.port.in.SwitchProviderUseCase;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * Slash command {@code /provider}: lists the LLM providers or switches to one.
 */
@Component
public class ProviderSlashCommands {

  private static final String ROW = "%s%-10s %-11s %s%n";

  private final ListProvidersQuery listProvidersQuery;
  private final SwitchProviderUseCase switchProviderUseCase;
  private final StartSessionUseCase startSessionUseCase;
  private final ModelSlashCommands modelCommands;
  private final ReplCliMapper mapper;
  private final Terminal terminal;

  /**
   * Creates the command.
   *
   * @param listProvidersQuery lists the providers for {@code /provider}
   * @param switchProviderUseCase validates the provider for {@code /provider <name>}
   * @param startSessionUseCase starts the session of the new provider
   * @param modelCommands validates the default model of the new provider
   * @param mapper builds commands from the CLI state
   * @param terminal terminal to print to
   */
  public ProviderSlashCommands(
      ListProvidersQuery listProvidersQuery,
      SwitchProviderUseCase switchProviderUseCase,
      StartSessionUseCase startSessionUseCase,
      ModelSlashCommands modelCommands,
      ReplCliMapper mapper,
      Terminal terminal) {
    this.listProvidersQuery = listProvidersQuery;
    this.switchProviderUseCase = switchProviderUseCase;
    this.startSessionUseCase = startSessionUseCase;
    this.modelCommands = modelCommands;
    this.mapper = mapper;
    this.terminal = terminal;
  }

  /**
   * {@code /provider [name]}: lists the providers or switches to one in a new session with its
   * default model.
   *
   * @param session CLI state
   * @param argument provider name, empty to list
   */
  public void provider(ReplSession session, String argument) {
    if (argument.isEmpty()) {
      list(session);
      return;
    }
    Optional<LlmProvider> requested = LlmProviderConverter.find(argument);
    if (requested.isEmpty()) {
      println("Неизвестный провайдер " + argument + ". Доступны: "
          + LlmProviderConverter.names(", "));
      return;
    }
    if (requested.get() == session.getProvider()) {
      println("Провайдер " + LlmProviderConverter.displayName(requested.get()) + " уже активен");
      return;
    }
    ProviderResult selected =
        switchProviderUseCase.execute(mapper.toSwitchProviderCommand(requested.get()));
    session.applyProvider(selected);
    session.startNew(
        startSessionUseCase.execute(mapper.toStartSessionCommand(session)).sessionId());
    println("Провайдер: " + LlmProviderConverter.displayName(selected.provider())
        + ". Новая сессия; предыдущую можно продолжить через /resume");
    modelCommands.applySessionModel(session);
  }

  private void list(ReplSession session) {
    List<ProviderSummaryResult> providers =
        listProvidersQuery.execute(mapper.toListProvidersCriteria(session));
    PrintWriter writer = terminal.writer();
    writer.printf(Locale.ROOT, ROW, "  ", "ПРОВАЙДЕР", "СТАТУС", "МОДЕЛЬ ПО УМОЛЧАНИЮ");
    for (ProviderSummaryResult provider : providers) {
      writer.printf(Locale.ROOT, ROW, provider.active() ? "* " : "  ",
          LlmProviderConverter.displayName(provider.provider()),
          provider.available() ? "доступен" : "недоступен", provider.defaultModel());
    }
    writer.println("Сменить: /provider <имя> (начинает новую сессию)");
    writer.flush();
  }

  private void println(String text) {
    terminal.writer().println(text);
    terminal.writer().flush();
  }
}
