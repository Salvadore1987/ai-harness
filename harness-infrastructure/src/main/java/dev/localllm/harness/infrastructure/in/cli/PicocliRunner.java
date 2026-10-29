package dev.localllm.harness.infrastructure.in.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import dev.localllm.harness.infrastructure.in.cli.handlers.PicocliExceptionHandler;

/**
 * Runs {@link HarnessCommand} once the Spring context is ready and exposes its exit code.
 *
 * <p>Disabled with {@code harness.cli.enabled=false} so that tests can start the context without
 * entering the interactive loop.
 */
@Component
@ConditionalOnProperty(name = "harness.cli.enabled", havingValue = "true", matchIfMissing = true)
public class PicocliRunner implements CommandLineRunner, ExitCodeGenerator {

  private final HarnessCommand command;
  private final PicocliExceptionHandler exceptionHandler;
  private int exitCode;

  /**
   * Creates the runner.
   *
   * @param command entry command bean
   * @param exceptionHandler reports failures escaping the command
   */
  public PicocliRunner(HarnessCommand command, PicocliExceptionHandler exceptionHandler) {
    this.command = command;
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public void run(String... args) {
    exitCode = new CommandLine(command)
        .setExecutionExceptionHandler(exceptionHandler)
        .execute(args);
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
