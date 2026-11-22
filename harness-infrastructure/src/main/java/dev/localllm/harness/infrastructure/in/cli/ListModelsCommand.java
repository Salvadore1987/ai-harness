package dev.localllm.harness.infrastructure.in.cli;

import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;
import picocli.CommandLine;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import dev.localllm.harness.application.dto.result.ProviderResult;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.port.in.SwitchProviderUseCase;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * Subcommand {@code harness list [--provider P] [PREFIX]}: prints the models of a provider and
 * exits.
 */
@Component
@Command(
    name = "list",
    mixinStandardHelpOptions = true,
    description = "List the models of a provider; * marks its default model.")
public class ListModelsCommand implements Callable<Integer> {

  @Parameters(arity = "0..1", paramLabel = "PREFIX",
      description = "Only models whose name starts with the prefix.")
  private String namePrefix = "";

  @Option(names = "--provider", paramLabel = "PROVIDER", converter = LlmProviderConverter.class,
      description = "ollama or anthropic, defaults to harness.provider.")
  private @Nullable LlmProvider provider;

  private final ModelListPrinter printer;
  private final SwitchProviderUseCase switchProviderUseCase;
  private final ReplCliMapper mapper;
  private final HarnessProperties properties;

  /**
   * Creates the subcommand.
   *
   * @param printer prints the model table
   * @param switchProviderUseCase validates the provider and gives its default model
   * @param mapper builds the provider command
   * @param properties harness settings with the start provider
   */
  public ListModelsCommand(
      ModelListPrinter printer,
      SwitchProviderUseCase switchProviderUseCase,
      ReplCliMapper mapper,
      HarnessProperties properties) {
    this.printer = printer;
    this.switchProviderUseCase = switchProviderUseCase;
    this.mapper = mapper;
    this.properties = properties;
  }

  @Override
  public Integer call() {
    ProviderResult selected = switchProviderUseCase.execute(mapper.toSwitchProviderCommand(
        provider == null ? properties.provider() : provider));
    printer.printInstalled(selected.provider(), namePrefix, selected.defaultModel());
    return CommandLine.ExitCode.OK;
  }
}
