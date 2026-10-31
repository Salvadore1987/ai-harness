package dev.localllm.harness.infrastructure.in.cli;

import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.infrastructure.config.HarnessProperties;

/**
 * Subcommand {@code harness list [PREFIX]}: prints the installed models and exits.
 */
@Component
@Command(
    name = "list",
    mixinStandardHelpOptions = true,
    description = "List the installed models; * marks the default model.")
public class ListModelsCommand implements Callable<Integer> {

  @Parameters(arity = "0..1", paramLabel = "PREFIX",
      description = "Only models whose name starts with the prefix.")
  private String namePrefix = "";

  private final ModelListPrinter printer;
  private final HarnessProperties properties;

  /**
   * Creates the subcommand.
   *
   * @param printer prints the model table
   * @param properties harness settings with the default model
   */
  public ListModelsCommand(ModelListPrinter printer, HarnessProperties properties) {
    this.printer = printer;
    this.properties = properties;
  }

  @Override
  public Integer call() {
    printer.printInstalled(LlmProvider.OLLAMA, namePrefix, properties.model());
    return CommandLine.ExitCode.OK;
  }
}
