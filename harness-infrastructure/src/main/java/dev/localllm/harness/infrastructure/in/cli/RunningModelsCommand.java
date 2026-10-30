package dev.localllm.harness.infrastructure.in.cli;

import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Subcommand {@code harness ps [PREFIX]}: prints the models loaded by Ollama and exits.
 */
@Component
@Command(
    name = "ps",
    mixinStandardHelpOptions = true,
    description = "List the models loaded into memory by Ollama.")
public class RunningModelsCommand implements Callable<Integer> {

  @Parameters(arity = "0..1", paramLabel = "PREFIX",
      description = "Only models whose name starts with the prefix.")
  private String namePrefix = "";

  private final ModelListPrinter printer;

  /**
   * Creates the subcommand.
   *
   * @param printer prints the model table
   */
  public RunningModelsCommand(ModelListPrinter printer) {
    this.printer = printer;
  }

  @Override
  public Integer call() {
    printer.printRunning(namePrefix);
    return CommandLine.ExitCode.OK;
  }
}
