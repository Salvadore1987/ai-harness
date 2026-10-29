package dev.localllm.harness.infrastructure.in.cli.handlers;

import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Reports failures escaping the picocli command and maps them to exit code 1.
 */
@Component
public class PicocliExceptionHandler implements CommandLine.IExecutionExceptionHandler {

  /** Exit code of a failed run. */
  public static final int FAILURE_EXIT_CODE = 1;

  private final CliErrorReporter reporter;

  /**
   * Creates the handler.
   *
   * @param reporter prints the failure to the terminal
   */
  public PicocliExceptionHandler(CliErrorReporter reporter) {
    this.reporter = reporter;
  }

  @Override
  public int handleExecutionException(
      Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) {
    reporter.report(ex);
    return FAILURE_EXIT_CODE;
  }
}
