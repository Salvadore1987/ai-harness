package dev.localllm.harness.infrastructure.in.cli.handlers;

/**
 * Turns one kind of failure into a message for the terminal.
 */
public interface CliExceptionHandler {

  /**
   * Tells whether this handler is responsible for the failure.
   *
   * @param error the failure
   * @return {@code true} if {@link #describe(Throwable)} applies
   */
  boolean supports(Throwable error);

  /**
   * Describes the failure for the user, including what to do about it.
   *
   * @param error the failure
   * @return one-line message without styling
   */
  String describe(Throwable error);
}
